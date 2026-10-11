package com.airreload.airreload;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;

final class Telemetry {
  private static Analytics instance;

  private Telemetry() {}

  private static synchronized Analytics client(Context context) {
    if (instance != null) return instance;
    Context app = context.getApplicationContext();
    SharedPreferences prefs = app.getSharedPreferences("airreload_analytics", Context.MODE_PRIVATE);
    String version;
    try { version = app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName; }
    catch (Exception ignored) { version = "unknown"; }
    ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(32), runnable -> {
          Thread thread = new Thread(runnable, "airreload-analytics");
          thread.setDaemon(true);
          return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    executor.allowCoreThreadTimeOut(true);
    instance = new Analytics(new Analytics.Preferences() {
      @Override public boolean enabled() { return prefs.getBoolean("enabled", true); }
      @Override public String id() { return prefs.getString("installation_id", null); }
      @Override public void save(boolean enabled, String id) {
        SharedPreferences.Editor edit = prefs.edit().putBoolean("enabled", enabled);
        if (id == null) edit.remove("installation_id");
        else edit.putString("installation_id", id);
        edit.apply();
      }
    }, Telemetry::send, executor, version, Build.VERSION.SDK_INT);
    return instance;
  }

  static boolean enabled(Context context) {
    try { return client(context).enabled(); }
    catch (RuntimeException ignored) { return false; }
  }

  static boolean setEnabled(Context context, boolean enabled) {
    try { return client(context).setEnabled(enabled); }
    catch (RuntimeException ignored) { return false; }
  }

  static void capture(Context context, Analytics.Event event) {
    try { client(context).capture(event); }
    catch (RuntimeException ignored) { /* Best effort only. */ }
  }

  private static void send(String payload) throws Exception {
    HttpsURLConnection connection = (HttpsURLConnection)
        new URL("https://us.i.posthog.com/i/v0/e/").openConnection();
    try {
      connection.setConnectTimeout(1000);
      connection.setReadTimeout(1000);
      connection.setInstanceFollowRedirects(false);
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setDoOutput(true);
      byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
      connection.setFixedLengthStreamingMode(bytes.length);
      try (java.io.OutputStream output = connection.getOutputStream()) { output.write(bytes); }
      connection.getResponseCode();
    } finally { connection.disconnect(); }
  }
}
