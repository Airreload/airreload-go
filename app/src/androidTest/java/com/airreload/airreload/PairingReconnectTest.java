package com.airreload.airreload;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Exercises real Android preferences and HTTP across separate pairing workers. */
@RunWith(AndroidJUnit4.class)
public class PairingReconnectTest {
  private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
  private final List<Thread> workers = new ArrayList<>();
  private Fixture server;

  @Before public void setUp() throws Exception {
    context.getSharedPreferences("airreload_pairing", Context.MODE_PRIVATE).edit().clear().commit();
    server = new Fixture();
  }

  @After public void tearDown() throws Exception {
    for (Thread worker : workers) worker.interrupt();
    server.close();
    for (Thread worker : workers) worker.join(12_000);
    context.getSharedPreferences("airreload_pairing", Context.MODE_PRIVATE).edit().clear().commit();
  }

  @Test public void reconnectDownloadsExistingBuildWithoutAnotherBuild() throws Exception {
    Events first = new Events();
    Thread worker = PairingClient.pair(context, server.url(), first);
    workers.add(worker);
    assertTrue(first.building.await(10, TimeUnit.SECONDS));
    worker.interrupt();
    worker.join(12_000);
    assertFalse(worker.isAlive());

    // The phone has left. The computer finishes before a new worker reconnects.
    server.ready = true;
    PairingUrl remembered = PairingIdentity.savedSession(context);
    assertNotNull(remembered);
    Events resumed = new Events();
    workers.add(PairingClient.pair(context, remembered, resumed));
    assertTrue(resumed.finished.await(10, TimeUnit.SECONDS));
    assertNull(resumed.error);
    assertEquals(server.downloadUrl(), resumed.download);
    assertEquals(1, server.builds.get());
    assertEquals(2, server.ids.size());
    assertEquals(server.ids.get(0), server.ids.get(1));
  }

  @Test public void reconnectDuringBuildKeepsPollingUntilReady() throws Exception {
    Events first = new Events();
    Thread worker = PairingClient.pair(context, server.url(), first);
    workers.add(worker);
    assertTrue(first.building.await(10, TimeUnit.SECONDS));
    worker.interrupt();
    worker.join(12_000);
    Events resumed = new Events();
    workers.add(PairingClient.pair(context, PairingIdentity.savedSession(context), resumed));
    assertTrue(resumed.building.await(10, TimeUnit.SECONDS));
    assertNull(resumed.download);
    server.ready = true;
    assertTrue(resumed.finished.await(10, TimeUnit.SECONDS));
    assertNull(resumed.error);
    assertEquals(server.downloadUrl(), resumed.download);
    assertEquals(1, server.builds.get());
  }

  @Test public void expiredSessionIsRemovedButTemporaryOfflineSessionIsRetained() throws Exception {
    PairingUrl pairing = server.url();
    PairingIdentity.requestId(context, pairing);
    server.status = 404;
    Events ended = new Events();
    workers.add(PairingClient.pair(context, pairing, ended));
    assertTrue(ended.finished.await(10, TimeUnit.SECONDS));
    assertTrue(ended.error.contains("session has ended"));
    assertNull(PairingIdentity.savedSession(context));

    server.close();
    Events offline = new Events();
    workers.add(PairingClient.pair(context, pairing, offline));
    assertTrue(offline.building.await(10, TimeUnit.SECONDS));
    assertEquals(pairing.uri(), PairingIdentity.savedSession(context).uri());
  }

  @Test public void staleWorkerCannotForgetNewSession() throws Exception {
    PairingUrl old = server.url();
    PairingUrl newer = PairingUrl.parse(old.uri().toString().replace("/session?", "/new?"));
    PairingIdentity.requestId(context, old);
    PairingIdentity.requestId(context, newer);
    PairingIdentity.forgetSession(context, old);
    assertEquals(newer.uri(), PairingIdentity.savedSession(context).uri());
  }

  private static final class Events implements PairingClient.Callback {
    final CountDownLatch building = new CountDownLatch(1);
    final CountDownLatch finished = new CountDownLatch(1);
    volatile String error;
    volatile String download;
    @Override public void building(String message) { building.countDown(); }
    @Override public void ready(String url) { download = url; finished.countDown(); }
    @Override public void failed(String message) { error = message; finished.countDown(); }
  }

  private static final class Fixture implements AutoCloseable {
    final ServerSocket socket = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
    final AtomicInteger builds = new AtomicInteger();
    final List<String> ids = Collections.synchronizedList(new ArrayList<>());
    final Thread worker;
    volatile boolean ready;
    volatile int status = 200;
    String accepted;

    Fixture() throws Exception {
      worker = new Thread(() -> {
        while (!socket.isClosed()) {
          try (Socket request = socket.accept()) {
            request.setSoTimeout(5000);
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8));
            String method = reader.readLine().split(" ")[0];
            int length = 0;
            String header;
            while (!(header = reader.readLine()).isEmpty()) {
              if (header.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) {
                length = Integer.parseInt(header.substring(header.indexOf(':') + 1).trim());
              }
            }
            int code = status;
            if ("POST".equals(method)) {
              char[] body = new char[length];
              int offset = 0;
              while (offset < length) {
                int read = reader.read(body, offset, length - offset);
                if (read < 0) throw new java.io.EOFException();
                offset += read;
              }
              String id = new JSONObject(new String(body)).getString("requestId");
              ids.add(id);
              if (accepted == null) { accepted = id; builds.incrementAndGet(); }
              if (!accepted.equals(id)) code = 409;
            }
            JSONObject response = new JSONObject().put("state", ready ? "ready" : "building")
                .put("message", "Building").put("downloadUrl", downloadUrl());
            byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);
            request.getOutputStream().write(("HTTP/1.1 " + code + " Response\r\n"
                + "Content-Type: application/json\r\nContent-Length: " + bytes.length
                + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            request.getOutputStream().write(bytes);
          } catch (Exception failure) {
            if (!socket.isClosed()) throw new RuntimeException(failure);
          }
        }
      });
      worker.start();
    }

    PairingUrl url() {
      return PairingUrl.parse("http://127.0.0.1:" + socket.getLocalPort()
          + "/pair/session?airreload_pairing=1&token=abcdefghijklmnopqrstuvwxyzABCDEFG0123456789%3D");
    }

    String downloadUrl() { return "http://127.0.0.1:" + socket.getLocalPort() + "/app.apk"; }
    @Override public void close() throws Exception { socket.close(); worker.join(6000); }
  }
}
