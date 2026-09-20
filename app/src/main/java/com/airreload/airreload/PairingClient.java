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
import org.json.JSONArray;
import org.json.JSONObject;

/** Reports Android's public ABI list, then polls the exact pairing endpoint for one APK URL. */
public final class PairingClient {
  private static final int MAX_RESPONSE_CHARS = 16 * 1024;
  private static final long POLL_MILLIS = 1_000L;
  private static final long MAX_WAIT_MILLIS = 10 * 60 * 1_000L;

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
                JSONObject state = request(pairing, "POST", report.toString());
                long deadline = System.currentTimeMillis() + MAX_WAIT_MILLIS;
                while (true) {
                  if (Thread.currentThread().isInterrupted()) return;
                  String phase = state.optString("state");
                  String message = state.optString("message", "Waiting for Airreload on your computer.");
                  if ("ready".equals(phase)) {
                    String url = state.optString("downloadUrl");
                    if (url.isEmpty()) throw new IOException("Airreload sent an incomplete download instruction.");
                    ApkUrl.parse(url);
                    callback.ready(url);
                    return;
                  }
                  if ("error".equals(phase)) {
                    callback.failed(message);
                    return;
                  }
                  if (!"building".equals(phase)) {
                    throw new IOException("The computer sent an unsupported pairing response. Restart Airreload and scan the new QR code.");
                  }
                  callback.building(message);
                  if (System.currentTimeMillis() >= deadline) {
                    callback.failed("Airreload did not finish building within 10 minutes. Scan a new pairing code to try again.");
                    return;
                  }
                  Thread.sleep(POLL_MILLIS);
                  state = request(pairing, "GET", null);
                }
              } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
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

  private static JSONObject request(PairingUrl pairing, String method, String body) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) pairing.uri().toURL().openConnection();
    try {
      connection.setConnectTimeout(20_000);
      connection.setReadTimeout(30_000);
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
        throw new IOException("This pairing code is no longer available. Restart Airreload on your computer and scan the new QR code.");
      }
      String response = read(status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream());
      JSONObject json = new JSONObject(response);
      if (status < 200 || status >= 300) {
        throw new IOException(json.optString("message", "The pairing server returned HTTP " + status + "."));
      }
      return json;
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
          throw new IOException("The pairing server sent an oversized response.");
        }
      }
    }
    return result.toString();
  }
}
