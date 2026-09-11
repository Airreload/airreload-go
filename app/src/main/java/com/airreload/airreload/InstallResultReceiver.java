package com.airreload.airreload;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

public final class InstallResultReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    int sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
    if (sessionId != State.prefs(context).getInt("session", -2)) {
      return;
    }
    int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
    if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
      Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
      if (confirmation != null) {
        Confirmation.save(context, sessionId, confirmation);
        Confirmation.notifyReady(context);
        State.update(
            context,
            "awaiting_install",
            "Download complete. Android needs your confirmation to install it.",
            100);
        context.stopService(new Intent(context, InstallService.class));
        return;
      }
      Installer.cancel(context);
      State.update(context, "error", "Android could not open the install screen. Try again.", 0);
    } else if (status == PackageInstaller.STATUS_SUCCESS) {
      String packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);
      if (packageName == null) {
        packageName = State.prefs(context).getString("package", "");
      }
      State.recordInstalled(context, packageName);
      DownloadHistory.markInstalled(context, packageName);
      State.prefs(context).edit().remove("pending_launch").apply();
      State.update(context, "success", "Installed. Tap the app in your library to open it.", 100);
    } else {
      if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
        DownloadHistory.markCancelled(context);
      } else {
        DownloadHistory.markFailed(
            context, State.prefs(context).getString("package", ""));
      }
      State.update(context, "error", failureMessage(status, intent), 0);
    }
    Confirmation.clear(context);
    State.prefs(context)
        .edit()
        .remove("session")
        .remove("package")
        .remove("package_baseline_update")
        .apply();
    context.stopService(new Intent(context, InstallService.class));
  }

  private static String failureMessage(int status, Intent intent) {
    if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
      return "Installation cancelled. Nothing was added to your library.";
    }
    if (status == PackageInstaller.STATUS_FAILURE_STORAGE) {
      return "There isn’t enough device storage. Free some space and try again.";
    }
    if (status == PackageInstaller.STATUS_FAILURE_CONFLICT) {
      return "This app conflicts with an installed version or its signing certificate.";
    }
    if (status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE) {
      return "This APK isn’t compatible with this Android version or device architecture.";
    }
    if (status == PackageInstaller.STATUS_FAILURE_BLOCKED) {
      return "Android blocked the installation. Check your app-install settings and device policy.";
    }
    String detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
    return detail == null || detail.trim().isEmpty()
        ? "Installation didn’t finish. Scan the code and try again."
        : "Installation didn’t finish: " + detail;
  }
}
