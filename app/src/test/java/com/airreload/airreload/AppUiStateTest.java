package com.airreload.airreload;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AppUiStateTest {
  @Test
  public void activeInstallationPhasesAreBusy() {
    assertTrue(new AppUiState("downloading", "", 10).busy);
    assertTrue(new AppUiState("installing", "", -1).busy);
    assertTrue(new AppUiState("awaiting_install", "", 100).busy);
  }

  @Test
  public void terminalAndPermissionPhasesAreNotBusy() {
    assertFalse(new AppUiState("idle", "", 0).busy);
    assertFalse(new AppUiState("success", "", 100).busy);
    assertFalse(new AppUiState("error", "", 0).busy);
    assertFalse(new AppUiState("permission_needed", "", 0).busy);
  }
}
