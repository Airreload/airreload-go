package com.airreload.airreload;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;

public final class AnalyticsTest {
  static final class Store implements Analytics.Preferences {
    boolean enabled = true;
    String id;
    public boolean enabled() { return enabled; }
    public String id() { return id; }
    public void save(boolean value, String identity) { enabled = value; id = identity; }
  }
  static final class Fixture {
    final Store store = new Store();
    final List<Runnable> queue = new ArrayList<>();
    final List<String> payloads = new ArrayList<>();
    final Analytics analytics = new Analytics(store, payloads::add, queue::add, "1.1.0-test", 35);
    void flush() { List<Runnable> tasks = new ArrayList<>(queue); queue.clear(); tasks.forEach(Runnable::run); }
  }

  @Test public void defaultEnabledCreatesStableRandomIdentity() throws Exception {
    Fixture f = new Fixture();
    assertTrue(f.analytics.enabled());
    assertNull(f.store.id);
    f.analytics.capture(Analytics.Event.APP_OPENED);
    f.analytics.capture(Analytics.Event.PAIRING_STARTED);
    f.flush();
    assertEquals(2, f.payloads.size());
    assertEquals(new JSONObject(f.payloads.get(0)).getJSONObject("properties").getString("distinct_id"),
        new JSONObject(f.payloads.get(1)).getJSONObject("properties").getString("distinct_id"));
    assertEquals(36, f.store.id.length());
  }

  @Test public void disablingDropsQueuedEventsAndResetsIdentity() {
    Fixture f = new Fixture();
    f.analytics.capture(Analytics.Event.APP_OPENED);
    String old = f.store.id;
    assertTrue(f.analytics.setEnabled(false));
    assertNull(f.store.id);
    f.analytics.capture(Analytics.Event.PAIRING_STARTED);
    f.flush();
    assertTrue(f.payloads.isEmpty());
    f.analytics.setEnabled(true);
    f.analytics.capture(Analytics.Event.APP_OPENED);
    f.flush();
    assertNotEquals(old, f.store.id);
    assertEquals(1, f.payloads.size());
  }

  @Test public void reenableCannotSendOldQueuedEvents() {
    Fixture f = new Fixture();
    f.analytics.capture(Analytics.Event.APP_OPENED);
    f.analytics.setEnabled(false);
    f.analytics.setEnabled(true);
    f.flush();
    assertTrue(f.payloads.isEmpty());
  }

  @Test public void storedOptOutSurvivesNewClient() {
    Fixture f = new Fixture();
    f.store.enabled = false;
    f.analytics.capture(Analytics.Event.APP_OPENED);
    f.flush();
    assertTrue(f.queue.isEmpty());
    assertTrue(f.payloads.isEmpty());
    assertNull(f.store.id);
  }

  @Test public void payloadContainsOnlyAllowedMetadata() throws Exception {
    Fixture f = new Fixture();
    f.analytics.capture(Analytics.Event.DOWNLOAD_FAILED);
    f.flush();
    JSONObject event = new JSONObject(f.payloads.get(0));
    assertEquals("go_download_failed", event.getString("event"));
    assertEquals(4, event.length());
    JSONObject props = event.getJSONObject("properties");
    assertEquals(7, props.length());
    assertEquals("android", props.getString("surface"));
    assertEquals("1.1.0-test", props.getString("app_version"));
    assertEquals(35, props.getInt("android_api"));
    assertFalse(props.getBoolean("$process_person_profile"));
    assertTrue(props.getBoolean("$geoip_disable"));
    assertTrue(props.isNull("$ip"));
  }

  @Test public void networkAndQueueFailuresNeverEscape() {
    Store store = new Store();
    Analytics network = new Analytics(store, body -> { throw new Exception("offline"); },
        Runnable::run, "test", 35);
    network.capture(Analytics.Event.APP_OPENED);
    Analytics queue = new Analytics(store, body -> fail("must not send"),
        task -> { throw new java.util.concurrent.RejectedExecutionException(); }, "test", 35);
    queue.capture(Analytics.Event.APP_OPENED);
  }

  @Test public void corruptPreferencesFailClosed() {
    Analytics analytics = new Analytics(new Analytics.Preferences() {
      public boolean enabled() { throw new ClassCastException(); }
      public String id() { return null; }
      public void save(boolean enabled, String id) { throw new IllegalStateException(); }
    }, body -> fail("must not send"), Runnable::run, "test", 35);
    assertFalse(analytics.enabled());
    analytics.capture(Analytics.Event.APP_OPENED);
    assertFalse(analytics.setEnabled(false));
  }

  @Test public void progressAndRepeatedSuccessDoNotDuplicateEvents() {
    assertNull(State.transitionEvent("downloading", "downloading"));
    assertNull(State.transitionEvent("success", "success"));
    assertEquals(Analytics.Event.INSTALL_SUCCEEDED, State.transitionEvent("installing", "success"));
    assertEquals(Analytics.Event.DOWNLOAD_FAILED, State.transitionEvent("downloading", "error"));
    assertEquals(Analytics.Event.INSTALL_FAILED, State.transitionEvent("awaiting_install", "error"));
    assertEquals(Analytics.Event.INSTALL_CANCELLED, State.transitionEvent("installing", "idle"));
    assertNull(State.transitionEvent("pairing", "error"));
  }
}
