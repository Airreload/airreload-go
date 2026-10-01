package com.airreload.airreload;

import static org.junit.Assert.*;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

public class PairingClientTest {
  private static final String REPORT = "{\"abis\":[\"arm64-v8a\"],\"requestId\":\"phone-request-12345\"}";
  private final Events events = new Events();
  private long now;

  @After public void clearInterrupt() {
    Thread.interrupted();
  }

  @Test public void keepsWaitingForAHealthyBuildBeyondAnHour() {
    int[] calls = {0};
    run((method, body) -> {
      calls[0]++;
      if (calls[0] == 1) {
        assertEquals("POST", method);
        assertEquals(REPORT, body);
      } else {
        assertEquals("GET", method);
        assertNull(body);
      }
      return state(calls[0] <= 3700 ? "building" : "ready");
    });
    assertEquals(3_700_000L, now);
    assertEquals("http://192.0.2.1/app.apk", events.ready);
    assertNull(events.error);
  }

  @Test public void retriesTheSameReportWhenThePostResponseIsLost() {
    int[] calls = {0};
    run((method, body) -> {
      assertEquals("POST", method);
      assertEquals(REPORT, body);
      if (calls[0]++ == 0) throw new SocketTimeoutException("response lost");
      return state("ready");
    });
    assertEquals(2, calls[0]);
    assertNotNull(events.ready);
    assertTrue(events.messages.get(0).contains("Retrying"));
    assertNull(events.error);
  }

  @Test public void resumesPollingAfterATemporaryWifiFailure() {
    int[] calls = {0};
    run((method, body) -> {
      int call = calls[0]++;
      if (call == 0) return state("building");
      assertEquals("GET", method);
      assertNull(body);
      if (call <= 3) throw new ConnectException("offline");
      return state("ready");
    });
    assertEquals(5, calls[0]);
    assertNotNull(events.ready);
    assertNull(events.error);
  }

  @Test public void rescanningAfterALongBuildAndAppRestartResumesTheClaim() throws Exception {
    PairingIdentityTest.MemoryStore store = new PairingIdentityTest.MemoryStore();
    PairingUrl pairing = PairingUrl.parse(PairingIdentityTest.URL);
    String firstId = PairingIdentity.requestId(store, pairing);
    String firstReport = new JSONObject(REPORT).put("requestId", firstId).toString();
    String[] acceptedId = {null};
    boolean[] built = {false};
    PairingClient.Transport server = (method, body) -> {
      if ("POST".equals(method)) {
        String id = new JSONObject(body).getString("requestId");
        if (acceptedId[0] == null) acceptedId[0] = id;
        if (!acceptedId[0].equals(id)) {
          throw new PairingClient.PairingException("This pairing code has already been used.");
        }
      }
      return state(built[0] ? "ready" : "building");
    };
    PairingClient.runSession(firstReport, events, server, () -> now, millis -> {
      now += millis;
      if (now > 700_000) throw new InterruptedException();
    });
    assertNull(events.error);
    assertNull(events.ready);
    Thread.interrupted();

    PairingIdentityTest.MemoryStore reopened = new PairingIdentityTest.MemoryStore();
    reopened.secret = store.secret;
    String resumedReport = new JSONObject(REPORT)
        .put("requestId", PairingIdentity.requestId(reopened, pairing)).toString();
    built[0] = true;
    PairingClient.runSession(resumedReport, events, server, () -> now, millis -> now += millis);
    assertEquals("http://192.0.2.1/app.apk", events.ready);
    assertNull(events.error);

    Events otherPhone = new Events();
    String otherReport = new JSONObject(REPORT).put("requestId",
        PairingIdentity.requestId(new PairingIdentityTest.MemoryStore(), pairing)).toString();
    PairingClient.runSession(otherReport, otherPhone, server, () -> now, millis -> now += millis);
    assertNull(otherPhone.ready);
    assertEquals("This pairing code has already been used.", otherPhone.error);
  }

  @Test public void explainsRecoveryAfterAContinuousMinuteOffline() {
    run((method, body) -> { throw new ConnectException("raw socket error"); });
    assertEquals(60_000L, now);
    assertNull(events.ready);
    assertTrue(events.error.contains("Keep the computer awake"));
    assertTrue(events.error.contains("new QR code"));
    assertFalse(events.error.contains("raw socket error"));
  }

  @Test public void successfulPollResetsTheOfflineBudget() {
    int[] calls = {0};
    run((method, body) -> {
      int call = calls[0]++;
      if (call == 0 || call == 51) return state("building");
      if (call == 102) return state("ready");
      throw new ConnectException("offline");
    });
    assertTrue(now > 60_000L);
    assertNotNull(events.ready);
    assertNull(events.error);
  }

  @Test public void stopsImmediatelyForExpiredOrClaimedCodes() {
    int[] calls = {0};
    run((method, body) -> {
      calls[0]++;
      throw new PairingClient.PairingException("This pairing code is no longer available.");
    });
    assertEquals(1, calls[0]);
    assertEquals(0L, now);
    assertEquals("This pairing code is no longer available.", events.error);
  }

  @Test public void reportsBuildFailureWithoutRetrying() {
    run((method, body) -> state("error").put("message", "Debug APK build failed"));
    assertEquals("Debug APK build failed", events.error);
    assertEquals(0L, now);
  }

  @Test public void cancellationDuringRetryHasNoErrorOrDownloadCallback() {
    PairingClient.runSession(REPORT, events,
        (method, body) -> { throw new ConnectException(); }, () -> now,
        millis -> { throw new InterruptedException(); });
    assertTrue(Thread.currentThread().isInterrupted());
    assertNull(events.error);
    assertNull(events.ready);
  }

  @Test public void cancellationDuringRequestSuppressesAReadyResponse() {
    run((method, body) -> {
      Thread.currentThread().interrupt();
      return state("ready");
    });
    assertNull(events.ready);
    assertNull(events.error);
  }

  private void run(PairingClient.Transport transport) {
    PairingClient.runSession(REPORT, events, transport, () -> now, millis -> now += millis);
  }

  private static JSONObject state(String phase) throws Exception {
    return new JSONObject().put("state", phase).put("message", "Building")
        .put("downloadUrl", "http://192.0.2.1/app.apk");
  }

  private static final class Events implements PairingClient.Callback {
    final List<String> messages = new ArrayList<>();
    String ready;
    String error;

    @Override public void building(String message) { messages.add(message); }
    @Override public void ready(String url) { ready = url; }
    @Override public void failed(String message) { error = message; }
  }
}
