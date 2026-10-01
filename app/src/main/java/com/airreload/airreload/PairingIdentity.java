package com.airreload.airreload;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** A durable, session-specific retry identity; the QR token still authorizes pairing. */
final class PairingIdentity {
  interface Store {
    String read();
    boolean write(String secret);
  }

  private PairingIdentity() {}

  static String requestId(Context context, PairingUrl pairing) throws IOException {
    SharedPreferences prefs = context.getSharedPreferences("airreload_pairing", Context.MODE_PRIVATE);
    return requestId(new Store() {
      @Override public String read() { return prefs.getString("secret", null); }
      @Override public boolean write(String secret) {
        // Persist before the POST: Android may kill the app while the PC builds.
        return prefs.edit().putString("secret", secret)
            .putString("session_url", pairing.uri().toString()).commit();
      }
    }, pairing);
  }

  static PairingUrl savedSession(Context context) {
    SharedPreferences prefs = context.getSharedPreferences("airreload_pairing", Context.MODE_PRIVATE);
    String saved = prefs.getString("session_url", null);
    if (saved == null) return null;
    try {
      return PairingUrl.parse(saved);
    } catch (IllegalArgumentException invalid) {
      prefs.edit().remove("session_url").apply();
      return null;
    }
  }

  static synchronized void forgetSession(Context context, PairingUrl pairing) {
    SharedPreferences prefs = context.getSharedPreferences("airreload_pairing", Context.MODE_PRIVATE);
    // A cancelled worker must not discard a newer session.
    if (pairing.uri().toString().equals(prefs.getString("session_url", null))) {
      prefs.edit().remove("session_url").apply();
    }
  }

  static synchronized String requestId(Store store, PairingUrl pairing) throws IOException {
    if (Thread.currentThread().isInterrupted()) {
      throw new java.io.InterruptedIOException("Pairing cancelled.");
    }
    String secret = store.read();
    if (secret == null) {
      secret = UUID.randomUUID().toString();
    }
    // Retry persistence even when an earlier failed commit updated the in-memory preferences.
    if (!store.write(secret)) {
      throw new IOException("Could not save pairing details. Free some phone storage and try again.");
    }
    // Do not send a reusable phone identifier. The same
    // installation and session produce the same ID after cancellation or restart.
    int port = pairing.uri().getPort();
    String scheme = pairing.uri().getScheme().toLowerCase(Locale.ROOT);
    if (port == -1) port = "https".equals(scheme) ? 443 : 80;
    String session = scheme + "\n" + pairing.uri().getHost().toLowerCase(Locale.ROOT)
        + "\n" + port + "\n" + pairing.uri().getRawPath() + "\n" + pairing.token();
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] digest = mac.doFinal(session.getBytes(StandardCharsets.UTF_8));
      StringBuilder id = new StringBuilder(64);
      for (byte value : digest) {
        id.append(Character.forDigit((value & 0xff) >>> 4, 16));
        id.append(Character.forDigit(value & 0xf, 16));
      }
      return id.toString();
    } catch (GeneralSecurityException unavailable) {
      throw new IOException("Could not prepare pairing details.", unavailable);
    }
  }
}
