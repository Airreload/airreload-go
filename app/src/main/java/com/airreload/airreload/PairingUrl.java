package com.airreload.airreload;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Strict parser for Airreload CLI's one-phone, short-lived pairing QR URLs. */
public final class PairingUrl {
  private final URI uri;
  private final String token;

  private PairingUrl(URI uri, String token) {
    this.uri = uri;
    this.token = token;
  }

  public static PairingUrl parse(String value) {
    URI uri = ApkUrl.parse(value);
    if (!uri.getRawPath().matches("/pair/[A-Za-z0-9_-]+") || uri.getRawQuery() == null) {
      throw new IllegalArgumentException("This is not a valid Airreload pairing code.");
    }
    String[] parts = uri.getRawQuery().split("&", -1);
    String token = null;
    boolean pairing = false;
    if (parts.length != 2) {
      throw new IllegalArgumentException("This pairing code has unexpected parameters.");
    }
    for (String part : parts) {
      int equals = part.indexOf('=');
      if (equals < 1) {
        throw new IllegalArgumentException("This pairing code has invalid parameters.");
      }
      String key = decode(part.substring(0, equals));
      String parameter = decode(part.substring(equals + 1));
      if ("airreload_pairing".equals(key) && "1".equals(parameter)) {
        pairing = true;
      } else if ("token".equals(key) && token == null) {
        token = parameter;
      } else {
        throw new IllegalArgumentException("This pairing code has unexpected parameters.");
      }
    }
    // Dart's base64UrlEncode keeps the trailing '=' for a 32-byte secret.
    // Preserve it verbatim: the CLI authenticates the exact token string.
    if (!pairing || token == null || !token.matches("[A-Za-z0-9_-]{43}=?")) {
      throw new IllegalArgumentException("This pairing code is incomplete or has expired.");
    }
    return new PairingUrl(uri, token);
  }

  private static String decode(String value) {
    // The Charset overload requires newer Android versions than our minSdk.
    try {
      return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
    } catch (java.io.UnsupportedEncodingException impossible) {
      throw new AssertionError(impossible);
    }
  }

  public URI uri() {
    return uri;
  }

  public String token() {
    return token;
  }

  public String source() {
    return uri.getScheme() + "://" + uri.getRawAuthority();
  }
}
