package com.airreload.airreload;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Application;
import android.graphics.Paint;
import android.os.SystemClock;
import com.airreload.shared.history.DownloadRecord;
import com.airreload.shared.history.DownloadRecordCodec;
import com.airreload.shared.history.DownloadOutcomes;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.UUID;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStore;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class InstallRecoveryUiTest {
  private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  private final Context context = instrumentation.getTargetContext();
  private MainActivity activity;
  private Map<String, ?> originalState;
  private Instrumentation.ActivityMonitor monitor;
  private File savedApk;
  private String downloadId;

  @Before public void prepare() throws Exception {
    originalState = State.prefs(context).getAll();
    instrumentation.getUiAutomation().executeShellCommand(
        "appops set " + context.getPackageName() + " REQUEST_INSTALL_PACKAGES allow").close();
    downloadId = UUID.randomUUID().toString();
    File directory = new File(context.getFilesDir(), "download-history");
    directory.mkdirs();
    savedApk = new File(directory, downloadId + ".apk");
    try (FileOutputStream output = new FileOutputStream(savedApk)) {
      output.write(new byte[] {'P', 'K', 3, 4});
    }
    DownloadRecord record = new DownloadRecord(downloadId, System.currentTimeMillis(),
        instrumentation.getContext().getPackageName(), "Recovery fixture", "1", 1,
        "localhost", DownloadOutcomes.DOWNLOADED, savedApk.getName(), savedApk.length());
    Set<String> records = new HashSet<>();
    records.add(DownloadRecordCodec.INSTANCE.encode(record));
    State.prefs(context).edit().putStringSet("download_history_v1", records)
        .putString("download_history_current", downloadId).commit();
    State.prefs(context).edit().remove(State.PENDING_LAUNCH)
        .putString("phase", "idle").putBoolean("onboarding_seen", true).commit();
  }

  @After public void restore() {
    if (monitor != null) instrumentation.removeMonitor(monitor);
    if (activity != null) instrumentation.runOnMainSync(() -> activity.finish());
    SharedPreferences.Editor editor = State.prefs(context).edit().clear();
    for (Map.Entry<String, ?> entry : originalState.entrySet()) {
      Object value = entry.getValue();
      String key = entry.getKey();
      if (value instanceof String) editor.putString(key, (String) value);
      else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
      else if (value instanceof Integer) editor.putInt(key, (Integer) value);
      else if (value instanceof Long) editor.putLong(key, (Long) value);
      else if (value instanceof Float) editor.putFloat(key, (Float) value);
      else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
    }
    editor.commit();
    if (savedApk != null) savedApk.delete();
  }

  @Test public void downgradeRetainsTargetAndOpensAndroidConfirmation() {
    String target = instrumentation.getContext().getPackageName();
    failInstall(target, PackageInstaller.STATUS_FAILURE_INVALID,
        "INSTALL_FAILED_VERSION_DOWNGRADE: Update version code 1 is older than current 489");
    assertEquals(target, State.prefs(context).getString(State.UNINSTALL_PACKAGE, ""));
    assertFalse(State.prefs(context).contains("package"));
    assertEquals(downloadId, State.prefs(context).getString(State.RETRY_DOWNLOAD, ""));
    assertEquals(context.getString(R.string.install_error_downgrade),
        State.prefs(context).getString("message", ""));
    launch();
    Intent[] opened = new Intent[1];
    monitor = new Instrumentation.ActivityMonitor() {
      @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
        if (!Intent.ACTION_UNINSTALL_PACKAGE.equals(intent.getAction())) return null;
        opened[0] = intent;
        return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null);
      }
    };
    instrumentation.addMonitor(monitor);
    instrumentation.runOnMainSync(() -> {
      View uninstall = find(activity.getWindow().getDecorView(), "Uninstall and reinstall");
      assertNotNull(uninstall);
      assertTrue(uninstall.isShown());
      uninstall.performClick();
      assertNotNull(opened[0]);
      assertEquals("package:" + target, opened[0].getDataString());
      assertTrue(opened[0].getBooleanExtra(Intent.EXTRA_RETURN_RESULT, false));
      assertTrue((((TextView) uninstall).getPaintFlags() & Paint.UNDERLINE_TEXT_FLAG) != 0);
    });
    instrumentation.waitForIdleSync();
    instrumentation.runOnMainSync(() -> {
      assertEquals("error", State.prefs(context).getString("phase", ""));
      assertEquals(context.getString(R.string.reinstall_cancelled),
          State.prefs(context).getString("message", ""));
      assertTrue(find(activity.getWindow().getDecorView(), "Uninstall and reinstall").isShown());
      State.update(activity, "error", "Download failed", 0);
      new ViewModelProvider(activity).get(AppStateViewModel.class).refreshNow();
    });
    instrumentation.waitForIdleSync();
    instrumentation.runOnMainSync(() ->
        assertFalse(find(activity.getWindow().getDecorView(), "Uninstall and reinstall").isShown()));
    assertEquals("", State.prefs(context).getString(State.RETRY_DOWNLOAD, ""));
  }

  @Test public void storageFailureDoesNotOfferUninstall() {
    failInstall(instrumentation.getContext().getPackageName(),
        PackageInstaller.STATUS_FAILURE_STORAGE, "Insufficient storage");
    launch();
    instrumentation.runOnMainSync(() ->
        assertFalse(find(activity.getWindow().getDecorView(), "Uninstall and reinstall").isShown()));
  }

  @Test public void signingConflictOffersRecoveryEvenWithoutLibraryEntry() {
    State.prefs(context).edit().remove("installed").commit();
    String target = instrumentation.getContext().getPackageName();
    failInstall(target, PackageInstaller.STATUS_FAILURE_CONFLICT,
        "INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match previously installed version");
    launch();
    assertRecoveryTargets(target);
  }

  @Test public void legacyConflictRestoresRecoveryFromLatestFailedDownload() {
    String target = instrumentation.getContext().getPackageName();
    DownloadHistory.markFailed(context, target);
    State.update(context, "error",
        "This app conflicts with an installed version or its signing certificate.", 0);
    launch();
    assertRecoveryTargets(target);
  }

  @Test public void legacyConflictDoesNotReuseAnOlderFailedDownload() {
    String target = instrumentation.getContext().getPackageName();
    DownloadHistory.markFailed(context, target);
    DownloadRecord failed = DownloadHistory.find(context, downloadId);
    DownloadRecord newer = new DownloadRecord(UUID.randomUUID().toString(),
        failed.getDownloadedAt() + 1, target, "Newer download", "2", 2,
        "localhost", DownloadOutcomes.CANCELLED, "", 0);
    Set<String> records = new HashSet<>();
    records.add(DownloadRecordCodec.INSTANCE.encode(failed));
    records.add(DownloadRecordCodec.INSTANCE.encode(newer));
    State.prefs(context).edit().putStringSet("download_history_v1", records).commit();
    State.update(context, "error",
        "This app conflicts with an installed version or its signing certificate.", 0);
    launch();
    instrumentation.runOnMainSync(() ->
        assertFalse(find(activity.getWindow().getDecorView(), "Uninstall and reinstall").isShown()));
    assertEquals("", State.prefs(context).getString(State.RETRY_DOWNLOAD, ""));
  }

  @Test public void legacyConflictWithoutSavedApkDoesNotOfferRecovery() {
    DownloadHistory.markFailed(context, instrumentation.getContext().getPackageName());
    savedApk.delete();
    State.update(context, "error",
        "This app conflicts with an installed version or its signing certificate.", 0);
    launch();
    instrumentation.runOnMainSync(() ->
        assertFalse(find(activity.getWindow().getDecorView(), "Uninstall and reinstall").isShown()));
  }

  private void assertRecoveryTargets(String target) {
    assertEquals(target, State.prefs(context).getString(State.UNINSTALL_PACKAGE, ""));
    assertEquals(downloadId, State.prefs(context).getString(State.RETRY_DOWNLOAD, ""));
    assertEquals(context.getString(R.string.install_error_conflict),
        State.prefs(context).getString("message", ""));
    instrumentation.runOnMainSync(() -> {
      TextView action = (TextView) find(activity.getWindow().getDecorView(), "Uninstall and reinstall");
      assertTrue(action.isShown());
      assertTrue((action.getPaintFlags() & Paint.UNDERLINE_TEXT_FLAG) != 0);
    });
  }

  @Test public void missingPackageDoesNotOfferUninstall() {
    failInstall("com.airreload.missing.recoveryfixture", PackageInstaller.STATUS_FAILURE_CONFLICT, null);
    launch();
    instrumentation.runOnMainSync(() ->
        assertFalse(find(activity.getWindow().getDecorView(), "Uninstall and reinstall").isShown()));
  }


  @Test public void pollingDetectsRemovalAndPreservesExactDownload() {
    ViewModelStore store = new ViewModelStore();
    instrumentation.runOnMainSync(() -> {
      State.update(context, "uninstalling", "Waiting", -1,
          instrumentation.getContext().getPackageName(), downloadId);
      new ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(
          (Application) context.getApplicationContext())).get(AppStateViewModel.class);
    });
    instrumentation.waitForIdleSync();
    assertEquals("uninstalling", State.prefs(context).getString("phase", ""));
    // Change package visibility without broadcasting: only the one-second poll can detect it.
    State.prefs(context).edit().putString(State.UNINSTALL_PACKAGE,
        "com.airreload.missing.recoveryfixture").commit();
    SystemClock.sleep(1500);
    assertEquals("ready_to_reinstall", State.prefs(context).getString("phase", ""));
    assertEquals(downloadId, State.prefs(context).getString(State.RETRY_DOWNLOAD, ""));
    instrumentation.runOnMainSync(store::clear);
  }

  @Test public void missingArchiveNeverOpensUninstall() {
    failInstall(instrumentation.getContext().getPackageName(),
        PackageInstaller.STATUS_FAILURE_CONFLICT, null);
    savedApk.delete();
    launch();
    instrumentation.runOnMainSync(() ->
        find(activity.getWindow().getDecorView(), "Uninstall and reinstall").performClick());
    instrumentation.waitForIdleSync();
    assertEquals(context.getString(R.string.reinstall_missing),
        State.prefs(context).getString("message", ""));
    assertEquals("error", State.prefs(context).getString("phase", ""));
  }

  @Test public void activeRetryKeepsItsArchiveAndReusesHistoryEntry() {
    DownloadRecord record = DownloadHistory.find(context, downloadId);
    State.update(context, "uninstalling", "Waiting", -1,
        record.getPackageName(), downloadId);
    assertEquals(0, DownloadHistory.delete(context, java.util.Collections.singleton(downloadId)));
    DownloadHistory.retry(context, record);
    DownloadHistory.markInstalled(context, record.getPackageName());
    assertTrue(savedApk.isFile());
    assertEquals(1, DownloadHistory.all(context).size());
    assertEquals(DownloadOutcomes.INSTALLED, DownloadHistory.find(context, downloadId).getOutcome());
  }

  private void failInstall(String packageName, int status, String detail) {
    State.prefs(context).edit().putInt("session", 12345)
        .putString("package", packageName).commit();
    instrumentation.runOnMainSync(() -> new InstallResultReceiver().onReceive(context,
        new Intent().putExtra(PackageInstaller.EXTRA_SESSION_ID, 12345)
            .putExtra(PackageInstaller.EXTRA_STATUS, status)
            .putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, detail)));
  }

  private void launch() {
    activity = (MainActivity) instrumentation.startActivitySync(
        new Intent(context, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    instrumentation.waitForIdleSync();
  }

  private View find(View view, String label) {
    if (view instanceof TextView && label.contentEquals(((TextView) view).getText())) return view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View found = find(group.getChildAt(i), label);
        if (found != null) return found;
      }
    }
    return null;
  }
}
