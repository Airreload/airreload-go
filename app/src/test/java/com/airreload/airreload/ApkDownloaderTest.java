package com.airreload.airreload;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ApkDownloaderTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private static final Queue<Response> responses = new ArrayDeque<>();
  private static final List<String> visited = new ArrayList<>();

  @BeforeClass
  public static void installTestTransport() {
    URL.setURLStreamHandlerFactory(
        protocol ->
            ("https".equals(protocol) || "http".equals(protocol))
                ? new URLStreamHandler() {
                  @Override
                  protected URLConnection openConnection(URL url) {
                    visited.add(url.toString());
                    Response response = responses.remove();
                    return new HttpURLConnection(url) {
                      @Override
                      public void disconnect() {}

                      @Override
                      public boolean usingProxy() {
                        return false;
                      }

                      @Override
                      public void connect() {}

                      @Override
                      public int getResponseCode() {
                        return response.status;
                      }

                      @Override
                      public String getHeaderField(String key) {
                        return "Location".equals(key) ? response.location : null;
                      }

                      @Override
                      public long getContentLengthLong() {
                        return response.length;
                      }

                      @Override
                      public InputStream getInputStream() {
                        return new ByteArrayInputStream(response.body);
                      }
                    };
                  }
                }
                : null);
  }

  @Before
  public void reset() {
    responses.clear();
    visited.clear();
  }

  @Test
  public void downloadsThroughValidatedRelativeRedirect() throws Exception {
    responses.add(new Response(302, 0, "/artifact?id=42", new byte[0]));
    responses.add(new Response(200, -1, null, new byte[] {0x50, 0x4b, 3, 4}));
    File output = folder.newFile();
    ApkDownloader.download("https://example.org/start", output, (read, total) -> {});
    assertArrayEquals(new byte[] {0x50, 0x4b, 3, 4}, Files.readAllBytes(output.toPath()));
    assertEquals("https://example.org/artifact?id=42", visited.get(1));
  }

  @Test
  public void rejectsRedirectWithCredentials() throws Exception {
    responses.add(
        new Response(302, 0, "https://user:secret@example.org/app.apk", new byte[0]));
    expectFailure("credentials");
    assertEquals(1, visited.size());
  }

  @Test
  public void rejectsHttpsDowngradeBeforeConnectingToHttp() throws Exception {
    responses.add(new Response(302, 0, "http://example.org/app.apk", new byte[0]));
    expectFailure("unencrypted HTTP");
    assertEquals(1, visited.size());
  }

  @Test
  public void permitsExplicitHttpForLocalDevelopment() throws Exception {
    responses.add(new Response(200, 4, null, new byte[] {0x50, 0x4b, 3, 4}));
    File output = folder.newFile();
    ApkDownloader.download("http://192.168.1.2:8080/app.apk", output, (read, total) -> {});
    assertEquals(4, output.length());
  }

  @Test
  public void rejectsEmptyAndTruncatedBodies() throws Exception {
    responses.add(new Response(200, -1, null, new byte[0]));
    expectFailure("incomplete");
    responses.add(new Response(200, 20, null, new byte[] {1, 2}));
    expectFailure("incomplete");
  }

  @Test
  public void rejectsOversizedDeclaredBody() throws Exception {
    responses.add(new Response(200, ApkDownloader.MAX_BYTES + 1, null, new byte[0]));
    expectFailure("512 MB");
  }

  @Test
  public void capsRedirectsAtFive() throws Exception {
    for (int index = 0; index < 6; index++) {
      responses.add(new Response(302, 0, "/again", new byte[0]));
    }
    expectFailure("too many");
    assertEquals(6, visited.size());
  }

  @Test
  public void surfacesHttpErrors() throws Exception {
    responses.add(new Response(404, 0, null, new byte[0]));
    expectFailure("HTTP 404");
  }

  private void expectFailure(String expectedText) throws Exception {
    try {
      ApkDownloader.download("https://example.org/app", folder.newFile(), (read, total) -> {});
      fail("Download should fail");
    } catch (IOException | IllegalArgumentException exception) {
      assertTrue(exception.getMessage(), exception.getMessage().contains(expectedText));
    }
  }

  private static final class Response {
    final int status;
    final long length;
    final String location;
    final byte[] body;

    Response(int status, long length, String location, byte[] body) {
      this.status = status;
      this.length = length;
      this.location = location;
      this.body = body;
    }
  }
}
