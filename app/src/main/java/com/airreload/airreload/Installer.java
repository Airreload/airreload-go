package com.airreload.airreload;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.os.Build;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class Installer {
  private Installer() {}

  static void cancel(Context context) {
    int sessionId = State.prefs(context).getInt("session", -1);
    DownloadHistory.markCancelled(context);
    Confirmation.clear(context);
    State.prefs(context)
        .edit()
        .remove("session")
        .remove("queued_url")
        .remove("package")
        .remove("package_baseline_update")
        .apply();
    if (sessionId >= 0) {
      try {
        context.getPackageManager().getPackageInstaller().abandonSession(sessionId);
      } catch (RuntimeException ignored) {
        // A committed or expired session may already be gone.
      }
    }
  }

  @SuppressLint("ApplySharedPref")
  static int install(Context context, File apk) throws IOException {
    PackageInfo info = ApkValidator.inspect(context, apk);
    if (context.getPackageName().equals(info.packageName)) {
      throw new IOException("Open the Airreload Go APK directly when updating Airreload Go itself.");
    }
    if (!context.getPackageManager().canRequestPackageInstalls()) {
      throw new IOException("Allow Airreload Go to install apps in Android settings, then try again.");
    }

    PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
    PackageInstaller.SessionParams params =
        new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
    params.setAppPackageName(info.packageName);
    params.setSize(apk.length());
    if (Build.VERSION.SDK_INT >= 31) {
      params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
    }
    int sessionId = packageInstaller.createSession(params);
    long baselineUpdate = -1;
    try {
      baselineUpdate =
          context.getPackageManager().getPackageInfo(info.packageName, 0).lastUpdateTime;
    } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
      // A negative baseline identifies a first-time install.
    }
    State.prefs(context)
        .edit()
        .putInt("session", sessionId)
        .putString("package", info.packageName)
        .putLong("package_baseline_update", baselineUpdate)
        .commit();

    boolean committed = false;
    try (PackageInstaller.Session session = packageInstaller.openSession(sessionId)) {
      try (InputStream input = new FileInputStream(apk);
          OutputStream output = session.openWrite("base.apk", 0, apk.length())) {
        byte[] buffer = new byte[64 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
          output.write(buffer, 0, count);
        }
        session.fsync(output);
      }
      DownloadHistory.archiveDownloaded(
          context, info, apk, State.prefs(context).getString("last_url", ""));
      State.update(context, "installing", "Handing your app to Android…", 100);
      Intent callback =
          new Intent(context, InstallResultReceiver.class)
              .setAction("com.airreload.airreload.INSTALL_RESULT." + sessionId);
      int flags = PendingIntent.FLAG_UPDATE_CURRENT;
      if (Build.VERSION.SDK_INT >= 31) {
        flags |= PendingIntent.FLAG_MUTABLE;
      }
      PendingIntent pending = PendingIntent.getBroadcast(context, sessionId, callback, flags);
      session.commit(pending.getIntentSender());
      committed = true;
      return sessionId;
    } finally {
      if (!committed) {
        DownloadHistory.markFailed(context, info.packageName);
        packageInstaller.abandonSession(sessionId);
      }
    }
  }
}
