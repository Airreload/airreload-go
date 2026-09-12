package com.airreload.airreload;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

final class LauncherIconController {
  private static final String SYSTEM_ALIAS = "com.airreload.airreload.LauncherSystem";
  private static final String LIGHT_ALIAS = "com.airreload.airreload.LauncherLight";
  private static final String DARK_ALIAS = "com.airreload.airreload.LauncherDark";

  private LauncherIconController() {}

  static String aliasForTheme(String themeMode) {
    if ("light".equals(themeMode)) return LIGHT_ALIAS;
    if ("dark".equals(themeMode)) return DARK_ALIAS;
    return SYSTEM_ALIAS;
  }

  static void sync(Context context, String themeMode) {
    PackageManager packageManager = context.getPackageManager();
    ComponentName system = new ComponentName(context, SYSTEM_ALIAS);
    ComponentName light = new ComponentName(context, LIGHT_ALIAS);
    ComponentName dark = new ComponentName(context, DARK_ALIAS);
    String desiredAlias = aliasForTheme(themeMode);
    ComponentName desired = new ComponentName(context, desiredAlias);

    // Enable the replacement first so the launcher always has one available entry point.
    setEnabled(packageManager, desired, true, SYSTEM_ALIAS.equals(desiredAlias));
    if (!SYSTEM_ALIAS.equals(desiredAlias)) setEnabled(packageManager, system, false, true);
    if (!LIGHT_ALIAS.equals(desiredAlias)) setEnabled(packageManager, light, false, false);
    if (!DARK_ALIAS.equals(desiredAlias)) setEnabled(packageManager, dark, false, false);
  }

  private static void setEnabled(
      PackageManager packageManager,
      ComponentName component,
      boolean enabled,
      boolean enabledByDefault) {
    int current = packageManager.getComponentEnabledSetting(component);
    boolean currentlyEnabled =
        current == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            || (current == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && enabledByDefault);
    if (currentlyEnabled == enabled) return;

    packageManager.setComponentEnabledSetting(
        component,
        enabled
            ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP);
  }
}
