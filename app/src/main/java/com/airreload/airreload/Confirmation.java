package com.airreload.airreload;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Preserves Android's confirmation intent, including its Binder token, in a PendingIntent. */
final class Confirmation {
  private static final int NOTIFICATION_ID = 202;
  private static PendingIntent activeToken;
  private static int activeSession = -1;

  private Confirmation() {}

  @SuppressLint("ApplySharedPref")
  static void save(Context context, int sessionId, Intent confirmationIntent) {
    confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    activeToken =
        PendingIntent.getActivity(
            context,
            sessionId,
            confirmationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    activeSession = sessionId;
    State.prefs(context)
        .edit()
        .putString("confirmation_identity", confirmationIntent.toUri(0))
        .putBoolean("confirmation_shown", false)
        .commit();
  }

  static PendingIntent get(Context context) {
    int sessionId = State.prefs(context).getInt("session", -1);
    if (activeToken != null && activeSession == sessionId) {
      return activeToken;
    }
    String identity = State.prefs(context).getString("confirmation_identity", null);
    if (identity == null) {
      return null;
    }
    try {
      activeToken =
          PendingIntent.getActivity(
              context,
              sessionId,
              Intent.parseUri(identity, 0),
              PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
      activeSession = sessionId;
      return activeToken;
    } catch (java.net.URISyntaxException exception) {
      return null;
    }
  }

  static void clear(Context context) {
    PendingIntent token = get(context);
    if (token != null) {
      token.cancel();
    }
    activeToken = null;
    activeSession = -1;
    State.prefs(context)
        .edit()
        .remove("confirmation_identity")
        .remove("confirmation_shown")
        .apply();
    context.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
  }

  static void notifyReady(Context context) {
    NotificationManager manager = context.getSystemService(NotificationManager.class);
    manager.createNotificationChannel(
        new NotificationChannel(
            "install_ready",
            context.getString(R.string.notification_ready),
            NotificationManager.IMPORTANCE_DEFAULT));
    Intent open =
        new Intent(context, MainActivity.class)
            .setAction("com.airreload.airreload.CONTINUE_INSTALL")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("install_token", get(context));
    PendingIntent pending =
        PendingIntent.getActivity(
            context, 202, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    if (Build.VERSION.SDK_INT < 33
        || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
      manager.notify(
          NOTIFICATION_ID,
          new Notification.Builder(context, "install_ready")
              .setSmallIcon(R.drawable.ic_notification)
              .setContentTitle(context.getString(R.string.notification_ready_title))
              .setContentText(context.getString(R.string.notification_ready_body))
              .setContentIntent(pending)
              .setAutoCancel(true)
              .build());
    }
  }
}
