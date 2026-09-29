package com.airreload.airreload;

import android.os.Build;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Reports Android's public ABI list, then polls the exact pairing endpoint for one APK URL. */
public final class PairingClient {
  private static final int MAX_RESPONSE_CHARS = 16 * 1024;
  private static final long POLL_MILLIS = 1_000L;
  private static final long MAX_UNREACHABLE_MILLIS = 60_000L;

  interface Transport {
    JSONObject request(String method, String body) throws Exception;
  }

  interface Clock {
    long millis();
  }

  interface Sleeper {
    void sleep(long millis) throws InterruptedException;
  }

  static final class PairingException extends IOException {
    PairingException(String message) {
      super(message);
    }
  }

  public interface Callback {
    void building(String message);

    void ready(String downloadUrl);

    void failed(String message);
  }

  private PairingClient() {}

  public static Thread pair(PairingUrl pairing, Callback callback) {
    Thread worker = new Thread(
            () -> {
              try {
                JSONObject report = new JSONObject();
                JSONArray abis = new JSONArray();
                for (String abi : new LinkedHashSet<String>(java.util.Arrays.asList(Build.SUPPORTED_ABIS))) {
                  if (abi != null && !abi.isEmpty()) abis.put(abi);
                }
                report.put("abis", abis);
                // Keep this ID for all attempts, including when the PC accepted
                // the POST but its response was lost on Wi-Fi.
                report.put("requestId", UUID.randomUUID().toString());
                runSession(report.toString(), callback,
                    (method, body) -> request(pairing, method, body),
                    () -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()), Thread::sleep);
              } catch (Exception exception) {
                if (Thread.currentThread().isInterrupted()) return;
                callback.failed(
                    exception.getMessage() == null
                        ? "Pairing stopped. Scan a new code and try again."
                        : exception.getMessage());
              }
            },
            "airreload-pairing");
    worker.start();
    return worker;
  }

  static void runSession(String report, Callback callback, Transport transport,
      Clock clock, Sleeper sleeper) {
    boolean paired = false;
    Long unreachableSince = null;
    try {
      while (!Thread.currentThread().isInterrupted()) {
        long attemptStarted = clock.millis();
        JSONObject state;
        try {
          state = transport.request(paired ? "GET" : "POST", paired ? null : report);
          unreachableSince = null;
        } catch (PairingException terminal) {
          throw terminal;
        } catch (IOException unavailable) {
          if (Thread.currentThread().isInterrupted()) return;
          if (unreachableSince == null) unreachableSince = attemptStarted;
          if (clock.millis() - unreachableSince >= MAX_UNREACHABLE_MILLIS) {
            callback.failed("Cannot reach Airreload on your computer. Keep the computer awake "
                + "and both devices on the same Wi-Fi. If the session stopped, run Airreload "
                + "again and scan the new QR code.");
            return;
          }
          callback.building("Connection interrupted. Retrying… Keep Airreload running "
              + "and your computer awake.");
          sleeper.sleep(POLL_MILLIS);
          continue;
        }
        if (Thread.currentThread().isInterrupted()) return;
        String phase = state.optString("state");
        String message = state.optString("message", "Waiting for Airreload on your computer.");
        if ("ready".equals(phase)) {
          String url = state.optString("downloadUrl");
          if (url.isEmpty()) throw new PairingException("Airreload sent an incomplete download instruction.");
          ApkUrl.parse(url);
          callback.ready(url);
          return;
        }
        if ("error".equals(phase)) {
          callback.failed(message);
          return;
        }
        if (!"building".equals(phase)) {
          throw new PairingException("The computer sent an unsupported pairing response. "
              + "Restart Airreload and scan the new QR code.");
        }
        paired = true;
        callback.building(message);
        // A reachable build can take longer than ten minutes on a first run.
        // Only a sustained loss of contact ends the wait; Cancel still works.
        sleeper.sleep(POLL_MILLIS);
      }
    } catch (InterruptedException cancelled) {
      Thread.currentThread().interrupt();
    } catch (Exception failure) {
      if (Thread.currentThread().isInterrupted()) return;
      callback.failed(failure.getMessage() == null
          ? "Pairing stopped. Restart Airreload and scan the new QR code."
          : failure.getMessage());
    }
  }

  private static JSONObject request(PairingUrl pairing, String method, String body) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) pairing.uri().toURL().openConnection();
    try {
      connection.setConnectTimeout(5_000);
      connection.setReadTimeout(10_000);
      connection.setInstanceFollowRedirects(false);
      connection.setRequestMethod(method);
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("Cache-Control", "no-store");
      if (body != null) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(bytes.length);
        connection.setRequestProperty("Content-Type", "application/json");
        try (OutputStream output = connection.getOutputStream()) {
          output.write(bytes);
        }
      }
      int status = connection.getResponseCode();
      if (status == 404 || status == 410) {
        throw new PairingException("This pairing code is no longer available. Restart Airreload on your computer and scan the new QR code.");
      }
      String response = read(status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream());
      if (status < 200 || status >= 300) {
        String message = "The pairing server returned HTTP " + status + ". Restart Airreload and scan the new QR code.";
        try {
          message = new JSONObject(response).optString("message", message);
        } catch (org.json.JSONException ignored) {
          // A proxy or a different service can return a non-JSON error page.
        }
        throw new PairingException(message);
      }
      return new JSONObject(response);
    } finally {
      connection.disconnect();
    }
  }

  private static String read(InputStream input) throws IOException {
    if (input == null) throw new IOException("The pairing server returned no response.");
    StringBuilder result = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      char[] buffer = new char[1024];
      int count;
      while ((count = reader.read(buffer)) != -1) {
        result.append(buffer, 0, count);
        if (result.length() > MAX_RESPONSE_CHARS) {
          throw new PairingException("The pairing server sent an oversized response.");
        }
      }
    }
    return result.toString();
  }
}
