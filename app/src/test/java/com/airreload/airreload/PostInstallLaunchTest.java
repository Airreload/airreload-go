package com.airreload.airreload;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class PostInstallLaunchTest {
  @Test
  public void missingPendingPackageDoesNothing() {
    assertEquals(PostInstallLaunch.Action.NONE, PostInstallLaunch.decide("", false, true));
    assertEquals(PostInstallLaunch.Action.NONE, PostInstallLaunch.decide(null, true, true));
  }

  @Test
  public void firstSuccessfulInstallAsksBeforeOpening() {
    assertEquals(PostInstallLaunch.Action.ASK, PostInstallLaunch.decide("example.app", false, true));
  }

  @Test
  public void rememberedChoiceOpensLaunchableApps() {
    assertEquals(PostInstallLaunch.Action.OPEN, PostInstallLaunch.decide("example.app", true, true));
  }

  @Test
  public void appsWithoutLauncherScreensStayAvailableAsInstalled() {
    assertEquals(
        PostInstallLaunch.Action.UNAVAILABLE,
        PostInstallLaunch.decide("example.service", true, false));
  }
}
