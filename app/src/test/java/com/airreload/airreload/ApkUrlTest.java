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
}

