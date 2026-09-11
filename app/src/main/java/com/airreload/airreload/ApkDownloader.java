package com.airreload.airreload;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;

/** Streams downloads with bounded size, redirects, and network timeouts. */
public final class ApkDownloader {
  static final long MAX_BYTES = 512L * 1024L * 1024L;

  private ApkDownloader() {}

  public interface Progress {
    void update(long read, long total) throws IOException;
  }

  public static void download(String link, File target, Progress progress) throws IOException {
    URI uri = ApkUrl.parse(link);
    HttpURLConnection connection = null;
    try {
      for (int redirects = 0; ; redirects++) {
        connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty(
            "Accept", "application/vnd.android.package-archive, application/octet-stream");
        connection.setRequestProperty("User-Agent", "Airreload-Go/1.0 Android");
        int code = connection.getResponseCode();
        if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
          if (redirects >= 5) {
            throw new IOException("That link redirects too many times.");
          }
          String location = connection.getHeaderField("Location");
          if (location == null || location.trim().isEmpty()) {
            throw new IOException("The server returned an incomplete redirect.");
          }
          URI next = ApkUrl.parse(uri.resolve(location).toString());
          if ("https".equalsIgnoreCase(uri.getScheme())
              && !"https".equalsIgnoreCase(next.getScheme())) {
            throw new IOException("An HTTPS download cannot redirect to an unencrypted HTTP link.");
          }
          uri = next;
          connection.disconnect();
          continue;
        }
        if (code != HttpURLConnection.HTTP_OK) {
          throw new IOException("The download server returned HTTP " + code + ".");
        }
        break;
      }

      long declaredLength = connection.getContentLengthLong();
      if (declaredLength > MAX_BYTES) {
        throw new IOException("This app exceeds Airreload Go’s 512 MB limit.");
      }

      long bytesRead = 0;
      try (InputStream input = connection.getInputStream();
          OutputStream output = new FileOutputStream(target)) {
        byte[] buffer = new byte[64 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
          bytesRead += count;
          if (bytesRead > MAX_BYTES) {
            throw new IOException("This app exceeds Airreload Go’s 512 MB limit.");
          }
          output.write(buffer, 0, count);
          progress.update(bytesRead, declaredLength);
        }
      }

      if (bytesRead == 0 || (declaredLength >= 0 && bytesRead != declaredLength)) {
        throw new IOException("The download was incomplete. Scan the code and try again.");
      }
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }
}
