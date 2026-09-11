package com.airreload.airreload;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class InstallService extends Service {
  static final String CHANNEL = "downloads";
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private volatile boolean cancelled;
  private boolean running;

  @Override
  public void onCreate() {
    super.onCreate();
    getSystemService(NotificationManager.class)
        .createNotificationChannel(
            new NotificationChannel(
                CHANNEL,
                getString(R.string.notification_downloads),
                NotificationManager.IMPORTANCE_LOW));
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    startForeground(201, notification(getString(R.string.notification_preparing), 0));
    if (running) {
      return START_NOT_STICKY;
    }
    running = true;
    String url = intent == null ? null : intent.getStringExtra("url");
    executor.execute(
        () -> {
          File file = new File(getCacheDir(), "airreload-" + UUID.randomUUID() + ".apk");
          try {
            State.update(this, "downloading", "Reaching the download server…", -1);
            final long[] lastUpdate = {0};
            ApkDownloader.download(
                url,
                file,
                (read, total) -> {
                  if (cancelled || Thread.currentThread().isInterrupted()) {
                    throw new IOException("Download cancelled.");
                  }
                  long now = SystemClock.elapsedRealtime();
                  if (now - lastUpdate[0] < 250) {
                    return;
                  }
                  lastUpdate[0] = now;
                  int percent = total > 0 ? (int) Math.min(100, 100 * read / total) : -1;
                  String message =
                      String.format(
                          Locale.US,
                          total > 0 ? "Downloading · %.1f of %.1f MB" : "Downloading · %.1f MB",
                          read / 1048576.0,
                          total > 0 ? total / 1048576.0 : 0);
                  State.update(this, "downloading", message, percent);
                  updateNotification(message, percent);
                });
            if (cancelled) {
              throw new IOException("Download cancelled.");
            }
            State.update(this, "installing", "Checking the APK and its signature…", -1);
            Installer.install(this, file);
            updateNotification(getString(R.string.notification_preparing_install), -1);
          } catch (Exception exception) {
            if (!cancelled) {
              String message = exception.getMessage();
              State.update(
                  this,
                  "error",
                  message == null || message.trim().isEmpty()
                      ? "The download didn’t finish. Please try again."
                      : message,
                  0);
            }
            stopSelf();
          } finally {
            // PackageInstaller owns a complete copy after commit.
            if (!file.delete() && file.exists()) {
              file.deleteOnExit();
            }
          }
        });
    return START_NOT_STICKY;
  }

  private void updateNotification(String text, int progress) {
    if (Build.VERSION.SDK_INT < 33
        || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
      getSystemService(NotificationManager.class).notify(201, notification(text, progress));
    }
  }

  private Notification notification(String text, int progress) {
    PendingIntent open =
        PendingIntent.getActivity(
            this,
            201,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    return new Notification.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(text)
        .setContentIntent(open)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setProgress(100, Math.max(0, progress), progress < 0)
        .build();
  }

  @Override
  public void onTimeout(int startId, int foregroundServiceType) {
    State.update(this, "error", "The download timed out. Scan the code and try again.", 0);
    stopSelf();
  }

  @Override
  public void onDestroy() {
    cancelled = true;
    executor.shutdownNow();
    stopForeground(STOP_FOREGROUND_REMOVE);
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}

