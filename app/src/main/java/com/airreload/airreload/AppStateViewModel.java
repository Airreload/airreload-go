package com.airreload.airreload;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.airreload.shared.history.DownloadOutcomes;
import com.airreload.shared.history.DownloadRecord;
import java.util.List;

/** Owns observable app state and reconciles active installs with Android once per second. */
public final class AppStateViewModel extends AndroidViewModel {
  private static final long POLL_INTERVAL_MS = 1000;

  private final MutableLiveData<AppUiState> state = new MutableLiveData<>();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Thread pairingWorker;
  private int pairingGeneration;
  private String pendingDownload;
  private final BroadcastReceiver stateChanges =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          refreshNow();
        }
      };
  private final BroadcastReceiver packageChanges =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          refreshNow();
        }
      };
  private final Runnable poll =
      new Runnable() {
        @Override
        public void run() {
          reconcileActiveInstall();
          refreshNow();
        }
      };

  public AppStateViewModel(@NonNull Application application) {
    super(application);
    ContextCompat.registerReceiver(
        application,
        stateChanges,
        new IntentFilter(State.CHANGED),
        ContextCompat.RECEIVER_NOT_EXPORTED);
    IntentFilter packages = new IntentFilter();
    packages.addAction(Intent.ACTION_PACKAGE_ADDED);
    packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
    packages.addAction(Intent.ACTION_PACKAGE_CHANGED);
    packages.addDataScheme("package");
    ContextCompat.registerReceiver(
        application, packageChanges, packages, ContextCompat.RECEIVER_EXPORTED);
    if ("pairing".equals(State.prefs(application).getString("phase", ""))) {
      State.update(application, "error",
          PairingIdentity.savedSession(application) != null
              ? "Connection paused. Return to the same Wi-Fi and tap Reconnect to computer. Keep Airreload running on your computer."
              : "Pairing was interrupted. Scan the QR code on your computer to reconnect.", 0);
    }
    restoreLegacyConflictRecovery();
    refreshNow();
  }

  private void restoreLegacyConflictRecovery() {
    Context context = getApplication();
    // Older releases saved this message but discarded the failed install's package and record ID.
    if (!"error".equals(State.prefs(context).getString("phase", ""))
        || !"This app conflicts with an installed version or its signing certificate."
            .equals(State.prefs(context).getString("message", ""))
        || !State.prefs(context).getString(State.UNINSTALL_PACKAGE, "").isEmpty()
        || !State.prefs(context).getString(State.RETRY_DOWNLOAD, "").isEmpty()) return;
    List<DownloadRecord> history = DownloadHistory.all(context);
    if (history.isEmpty()) return;
    // Only the latest download can belong to the last failure; never search older failed apps.
    DownloadRecord latest = history.get(0);
    if (!DownloadOutcomes.FAILED.equals(latest.getOutcome())
        || !DownloadHistory.hasArtifact(context, latest)
        || context.getPackageName().equals(latest.getPackageName())) return;
    try {
      context.getPackageManager().getPackageInfo(latest.getPackageName(), 0);
      State.update(context, "error", context.getString(R.string.install_error_conflict), 0,
          latest.getPackageName(), latest.getId());
    } catch (PackageManager.NameNotFoundException ignored) {
      // No installed app is available to uninstall.
    }
  }

  void startPairing(PairingUrl pairing) {
    cancelPairing();
    final int generation = pairingGeneration;
    State.prefs(getApplication()).edit()
        .remove("package").remove("package_baseline_update")
        .remove("queued_url").remove("queued_from_pairing").apply();
    State.update(getApplication(), "pairing", "Connecting to " + pairing.source() + "…", -1);
    pairingWorker = PairingClient.pair(getApplication(), pairing, new PairingClient.Callback() {
      @Override public void building(String message) {
        handler.post(() -> {
          if (generation != pairingGeneration) return;
          State.update(getApplication(), "pairing", message, -1);
        });
      }

      @Override public void ready(String downloadUrl) {
        handler.post(() -> {
          if (generation != pairingGeneration) return;
          pairingWorker = null;
          pendingDownload = downloadUrl;
          State.update(getApplication(), "idle", "Your Airreload debug APK is ready to download.", 0);
          refreshNow();
        });
      }

      @Override public void failed(String message) {
        handler.post(() -> {
          if (generation != pairingGeneration) return;
          pairingWorker = null;
          State.update(getApplication(), "error", message, 0);
        });
      }
    });
    refreshNow();
  }

  void cancelPairing() {
    pairingGeneration++;
    if (pairingWorker != null) pairingWorker.interrupt();
    pairingWorker = null;
    pendingDownload = null;
  }

  String takePendingDownload() {
    String result = pendingDownload;
    pendingDownload = null;
    return result;
  }

  LiveData<AppUiState> state() {
    return state;
  }

  void refreshNow() {
    Context context = getApplication();
    reconcileUninstall();
    AppUiState current =
        new AppUiState(
            State.prefs(context).getString("phase", "idle"),
            State.prefs(context).getString("message", ""),
            State.prefs(context).getInt("progress", -1),
            State.prefs(context).getString(State.UNINSTALL_PACKAGE, ""));
    state.setValue(current);
    handler.removeCallbacks(poll);
    if (current.busy) {
      handler.postDelayed(poll, POLL_INTERVAL_MS);
    }
  }

  private void reconcileUninstall() {
    Context context = getApplication();
    if (!"uninstalling".equals(State.prefs(context).getString("phase", ""))) return;
    String packageName = State.prefs(context).getString(State.UNINSTALL_PACKAGE, "");
    if (packageName.isEmpty()) return;
    try {
      context.getPackageManager().getPackageInfo(packageName, 0);
    } catch (PackageManager.NameNotFoundException removed) {
      State.update(context, "ready_to_reinstall", context.getString(R.string.reinstall_preparing), -1,
          packageName, State.prefs(context).getString(State.RETRY_DOWNLOAD, ""));
    }
  }

  private void reconcileActiveInstall() {
    Context context = getApplication();
    if (!State.busy(context) || "pairing".equals(State.prefs(context).getString("phase", ""))) {
      return;
    }
    String packageName = State.prefs(context).getString("package", "");
    if (packageName.isEmpty()) {
      return;
    }
    long baseline = State.prefs(context).getLong("package_baseline_update", Long.MIN_VALUE);
    if (baseline == Long.MIN_VALUE) {
      return;
    }
    try {
      PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
      boolean newlyPresent = baseline < 0;
      boolean updated = baseline >= 0 && info.lastUpdateTime > baseline;
      if (!newlyPresent && !updated) {
        return;
      }
      State.recordInstalled(context, packageName);
      DownloadHistory.markInstalled(context, packageName);
      Confirmation.clear(context);
      State.prefs(context)
          .edit()
          .remove("session")
          .remove("package")
          .remove("package_baseline_update")
          .apply();
      State.update(context, "success", "Installed successfully.", 100);
    } catch (PackageManager.NameNotFoundException ignored) {
      // A new package is not complete yet. Keep polling while the install is active.
    }
  }

  @Override
  protected void onCleared() {
    cancelPairing();
    handler.removeCallbacksAndMessages(null);
    getApplication().unregisterReceiver(stateChanges);
    getApplication().unregisterReceiver(packageChanges);
    super.onCleared();
  }
}
