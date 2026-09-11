package com.airreload.airreload;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class State {
  static final String CHANGED = "com.airreload.airreload.STATE_CHANGED";

  private State() {}

  static SharedPreferences prefs(Context context) {
    return context.getSharedPreferences("airreload_state", Context.MODE_PRIVATE);
  }

  static void update(Context context, String phase, String message, int progress) {
    prefs(context)
        .edit()
        .putString("phase", phase)
        .putString("message", message)
        .putInt("progress", progress)
        .apply();
    context.sendBroadcast(new Intent(CHANGED).setPackage(context.getPackageName()));
  }

  static boolean busy(Context context) {
    String phase = prefs(context).getString("phase", "idle");
    return "downloading".equals(phase)
        || "installing".equals(phase)
        || "awaiting_install".equals(phase);
  }

  static void recordInstalled(Context context, String packageName) {
    if (packageName == null || packageName.trim().isEmpty()) {
      return;
    }
    Set<String> existing = prefs(context).getStringSet("installed", Collections.emptySet());
    HashSet<String> packages = new HashSet<>(existing);
    packages.add(packageName);
    prefs(context).edit().putStringSet("installed", packages).apply();
  }

  static Set<String> installedPackages(Context context) {
    return new HashSet<>(prefs(context).getStringSet("installed", Collections.emptySet()));
  }
}

