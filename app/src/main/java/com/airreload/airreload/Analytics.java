package com.airreload.airreload;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.json.JSONObject;

/** Small, explicit event client. No arbitrary event names or properties are accepted. */
final class Analytics {
  static final String PROJECT_TOKEN = "phc_zSnDnDZpCrxj27TRpXksGrp6RPhyZyYMmXyYx8q9XxFP";
  enum Event {
    APP_OPENED("go_app_opened"), PAIRING_STARTED("go_pairing_started"),
    PAIRING_COMPLETED("go_pairing_completed"), PAIRING_FAILED("go_pairing_failed"),
    PAIRING_CANCELLED("go_pairing_cancelled"), DOWNLOAD_STARTED("go_download_started"),
    DOWNLOAD_COMPLETED("go_download_completed"), DOWNLOAD_FAILED("go_download_failed"),
    DOWNLOAD_CANCELLED("go_download_cancelled"), INSTALL_SUCCEEDED("go_install_succeeded"),
    INSTALL_FAILED("go_install_failed"), INSTALL_CANCELLED("go_install_cancelled");
    final String value;
    Event(String value) { this.value = value; }
  }

  interface Preferences {
    boolean enabled();
    String id();
    void save(boolean enabled, String id);
  }
  interface Transport { void send(String payload) throws Exception; }

  private final Preferences preferences;
  private final Transport transport;
  private final Executor executor;
  private final String version;
  private final int androidApi;
  private boolean optedOut;
  private long generation;
  private int sent;

  Analytics(Preferences preferences, Transport transport, Executor executor,
      String version, int androidApi) {
    this.preferences = preferences;
    this.transport = transport;
    this.executor = executor;
    this.version = version;
    this.androidApi = androidApi;
  }

  synchronized boolean enabled() {
    try { return !optedOut && preferences.enabled(); }
    catch (RuntimeException ignored) { return false; }
  }

  synchronized boolean setEnabled(boolean enabled) {
    generation++;
    if (!enabled) optedOut = true;
    try {
      String previousId = preferences.enabled() ? preferences.id() : null;
      preferences.save(enabled, enabled ? previousId : null);
      optedOut = !enabled;
      return true;
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  synchronized void capture(Event event) {
    try {
      if (!enabled() || sent >= 200) return;
      String identity = preferences.id();
      if (identity == null || !identity.matches("[a-f0-9-]{36}")) {
        identity = UUID.randomUUID().toString();
        preferences.save(true, identity);
      }
      final long capturedGeneration = generation;
      final String capturedId = identity;
      final long capturedAt = System.nanoTime();
      JSONObject properties = new JSONObject()
          .put("distinct_id", identity).put("surface", "android")
          .put("app_version", version).put("android_api", androidApi)
          .put("$process_person_profile", false).put("$geoip_disable", true)
          .put("$ip", JSONObject.NULL);
      final String payload = new JSONObject().put("token", PROJECT_TOKEN)
          .put("event", event.value).put("timestamp", Instant.now().toString())
          .put("properties", properties).toString();
      executor.execute(() -> {
        try {
          synchronized (Analytics.this) {
            if (!enabled() || capturedGeneration != generation ||
                !capturedId.equals(preferences.id()) ||
                System.nanoTime() - capturedAt > 15_000_000_000L) return;
          }
          transport.send(payload);
        } catch (Exception ignored) {
          // Analytics failures do not become app errors or generate retries.
        }
      });
      sent++;
    } catch (Exception ignored) {
      // Storage and queue failures must not interrupt pairing/installing.
    }
  }
}
