package com.airreload.airreload;

import static org.junit.Assert.*;

import java.io.IOException;
import org.junit.Test;

public class PairingIdentityTest {
  static final String URL = "http://192.0.2.1:8080/pair/session?airreload_pairing=1&token="
      + "abcdefghijklmnopqrstuvwxyzABCDEFG0123456789%3D";

  @Test public void reusesSavedIdentityAfterRescanAndStoreRecreation() throws Exception {
    MemoryStore store = new MemoryStore();
    PairingUrl pairing = PairingUrl.parse(URL);
    String first = PairingIdentity.requestId(store, pairing);
    assertTrue(first.matches("[a-f0-9]{64}"));
    assertNotNull(store.secret);
    assertEquals(first, PairingIdentity.requestId(store, pairing));
    MemoryStore reopened = new MemoryStore();
    reopened.secret = store.secret;
    assertEquals(first, PairingIdentity.requestId(reopened, pairing));
  }

  @Test public void differentPhonesAndSessionsCannotReplayTheSameClaim() throws Exception {
    MemoryStore phone = new MemoryStore();
    String first = PairingIdentity.requestId(phone, PairingUrl.parse(URL));
    assertNotEquals(first, PairingIdentity.requestId(new MemoryStore(), PairingUrl.parse(URL)));
    assertNotEquals(first, PairingIdentity.requestId(phone,
        PairingUrl.parse(URL.replace("/session?", "/another?"))));
    assertNotEquals(first, PairingIdentity.requestId(phone,
        PairingUrl.parse(URL.replace(":8080", ":8081"))));
    assertNotEquals(first, PairingIdentity.requestId(phone,
        PairingUrl.parse(URL.replace("token=a", "token=z"))));
  }

  @Test public void equivalentQueryEncodingAndOrderKeepTheSameIdentity() throws Exception {
    MemoryStore phone = new MemoryStore();
    String first = PairingIdentity.requestId(phone, PairingUrl.parse(URL));
    String reordered = URL.replace("airreload_pairing=1&", "").replace("%3D", "=")
        + "&airreload_pairing=1";
    assertEquals(first, PairingIdentity.requestId(phone, PairingUrl.parse(reordered)));
  }

  @Test public void refusesToClaimBeforeIdentityIsDurablySaved() throws Exception {
    MemoryStore store = new MemoryStore();
    store.writable = false;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        PairingIdentity.requestId(store, PairingUrl.parse(URL));
        fail("Must not pair using an identity that cannot survive a restart");
      } catch (IOException expected) {
        assertTrue(expected.getMessage().contains("storage"));
      }
    }
    store.writable = true;
    assertNotNull(PairingIdentity.requestId(store, PairingUrl.parse(URL)));
  }

  static final class MemoryStore implements PairingIdentity.Store {
    String secret;
    boolean writable = true;
    @Override public String read() { return secret; }
    @Override public boolean write(String value) {
      secret = value; // SharedPreferences updates memory even if its disk write fails.
      return writable;
    }
  }
}
