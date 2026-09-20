package com.airreload.airreload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class ApkUrlTest {
  @Test
  public void acceptsSignedEndpointWithoutApkSuffix() {
    assertEquals(
        "https://apps.example.org/download?id=12&sig=a%2Fb",
        ApkUrl.parse(" https://apps.example.org/download?id=12&sig=a%2Fb ").toString());
  }

  @Test
  public void acceptsHttpAndNonDefaultPort() {
    assertEquals(
        "http://192.168.1.20:8080/app.apk",
        ApkUrl.parse("http://192.168.1.20:8080/app.apk").toString());
  }

  @Test
  public void rejectsMalformedOrUnsafeLinks() {
    String[] invalid = {
      null,
      "",
      "hello",
      "file:///sdcard/app.apk",
      "intent://app",
      "https:///app.apk",
      "https://user:password@example.org/app.apk",
      "https://example.org/app.apk#fragment",
      "https://example.org:0/app.apk",
      "https://example.org:99999/app.apk",
      "https://example.org/app name.apk"
    };
    for (String value : invalid) {
      try {
        ApkUrl.parse(value);
        fail("Accepted unsafe value: " + value);
      } catch (IllegalArgumentException expected) {
        // Expected.
      }
    }
  }

  @Test
  public void parsesOnlyStrictAirreloadPairingLinks() {
    PairingUrl pairing =
        PairingUrl.parse(
            "http://192.168.1.20:8080/pair/endpoint?airreload_pairing=1&token="
                + "abcdefghijklmnopqrstuvwxyzABCDEFG0123456789");
    assertEquals("http://192.168.1.20:8080", pairing.source());
    assertEquals(43, pairing.token().length());
  }

  @Test
  public void acceptsCliBase64TokenWithEncodedPaddingWithoutChangingIt() {
    // Same 32-byte base64url encoding as Dart's randomToken(). The CLI URI
    // escapes the trailing '=' to '%3D' when putting it in the QR query.
    byte[] bytes = new byte[32];
    for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (i * 13);
    String token = java.util.Base64.getUrlEncoder().encodeToString(bytes);
    assertEquals(44, token.length());
    String url = "http://192.168.1.20:8080/pair/session?airreload_pairing=1&token="
        + token.replace("=", "%3D");
    PairingUrl pairing = PairingUrl.parse(url);
    assertEquals(token, pairing.token());
    assertEquals(url, pairing.uri().toString());
    assertEquals(token, PairingUrl.parse(url.replace("%3D", "=")).token());
  }

  @Test
  public void rejectsMalformedPairingLinks() {
    String[] invalid = {
      null,
      "",
      "plain text",
      "https://example.org/",
      "https://example.org/app.apk",
      "file:///sdcard/app.apk",
      "http://192.168.1.20/app.apk?airreload_pairing=1&token=abcdefghijklmnopqrstuvwxyzABCDEFG0123456789",
      "http://192.168.1.20/pair/app.apk?airreload_pairing=1&token=abcdefghijklmnopqrstuvwxyzABCDEFG0123456789",
      "http://192.168.1.20/pair/endpoint/extra?airreload_pairing=1&token=abcdefghijklmnopqrstuvwxyzABCDEFG0123456789",
      "http://192.168.1.20/pair/endpoint?airreload_pairing=1",
      "http://192.168.1.20/pair/endpoint?airreload_pairing=1&token=short",
      "http://192.168.1.20/pair/endpoint?airreload_pairing=1&token=abcdefghijklmnopqrstuvwxyzABCDEFG0123456789%3D%3D",
      "http://192.168.1.20/pair/endpoint?airreload_pairing=1&token=abcdefghijklmnopqrstuvwxyzABCDEFG0123456789&extra=1",
      "http://192.168.1.20/not-pair?airreload_pairing=1&token=abcdefghijklmnopqrstuvwxyzABCDEFG0123456789"
    };
    for (String value : invalid) {
      try {
        PairingUrl.parse(value);
        fail("Accepted invalid pairing value: " + value);
      } catch (IllegalArgumentException expected) {
        // Expected.
      }
    }
  }
}
