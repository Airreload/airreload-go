package com.airreload.airreload;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class LauncherIconControllerTest {
  @Test
  public void explicitThemeSelectsMatchingIcon() {
    assertEquals(
        "com.airreload.airreload.LauncherLight",
        LauncherIconController.aliasForTheme("light"));
    assertEquals(
        "com.airreload.airreload.LauncherDark",
        LauncherIconController.aliasForTheme("dark"));
  }

  @Test
  public void systemAndUnknownThemesUseAdaptiveSystemIcon() {
    assertEquals(
        "com.airreload.airreload.LauncherSystem",
        LauncherIconController.aliasForTheme("system"));
    assertEquals(
        "com.airreload.airreload.LauncherSystem",
        LauncherIconController.aliasForTheme("unexpected"));
  }
}
