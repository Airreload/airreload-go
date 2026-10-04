package com.airreload.airreload;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.PackageInstaller;
import org.junit.Test;

public final class InstallResultReceiverTest {
  @Test public void downgradeAndConflictsOfferUninstall() {
    assertTrue(InstallResultReceiver.canResolveByUninstalling(
        PackageInstaller.STATUS_FAILURE_INVALID,
        "INSTALL_FAILED_VERSION_DOWNGRADE: Update version code 1 is older than current 489"));
    assertTrue(InstallResultReceiver.canResolveByUninstalling(
        PackageInstaller.STATUS_FAILURE,
        "INSTALL_FAILED_VERSION_DOWNGRADE: Update version code 1 is older than current 489"));
    assertTrue(InstallResultReceiver.canResolveByUninstalling(
        PackageInstaller.STATUS_FAILURE_CONFLICT, null));
  }

  @Test public void otherFailuresDoNotSuggestRemovingAnApp() {
    for (int status : new int[] {
        PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED,
        PackageInstaller.STATUS_FAILURE_STORAGE, PackageInstaller.STATUS_FAILURE_BLOCKED,
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE}) {
      assertFalse(InstallResultReceiver.canResolveByUninstalling(status, null));
    }
    assertFalse(InstallResultReceiver.canResolveByUninstalling(
        PackageInstaller.STATUS_FAILURE, null));
    assertFalse(InstallResultReceiver.canResolveByUninstalling(
        PackageInstaller.STATUS_FAILURE, "INSTALL_FAILED_INVALID_APK"));
    assertFalse(InstallResultReceiver.canResolveByUninstalling(
        PackageInstaller.STATUS_FAILURE_INVALID, "INSTALL_FAILED_INVALID_APK"));
  }
}
