package com.airreload.airreload;

import java.net.URI;
import java.net.URISyntaxException;

/** Validates QR contents and every redirect before a connection is opened. */
public final class ApkUrl {
  private ApkUrl() {}

  public static URI parse(String text) {
    if (text == null || text.trim().isEmpty()) {
      throw new IllegalArgumentException("This QR code is empty.");
    }
    try {
      URI uri = new URI(text.trim());
      String scheme = uri.getScheme();
      if ((scheme == null
              || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)))
          || uri.getHost() == null
          || uri.getHost().isEmpty()) {
        throw new IllegalArgumentException("Use a direct HTTP or HTTPS link to an APK.");
      }
      if (uri.getRawUserInfo() != null || uri.getFragment() != null) {
        throw new IllegalArgumentException("The app link cannot include credentials or a fragment.");
      }
      if (uri.getPort() == 0 || uri.getPort() > 65535) {
        throw new IllegalArgumentException("The app link has an invalid port.");
      }
      return uri;
    } catch (URISyntaxException exception) {
      throw new IllegalArgumentException("This QR code does not contain a valid app link.");
    }
  }
}

