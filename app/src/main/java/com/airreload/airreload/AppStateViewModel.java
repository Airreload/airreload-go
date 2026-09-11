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

/** Owns observable app state and reconciles active installs with Android once per second. */
public final class AppStateViewModel extends AndroidViewModel {
  private static final long POLL_INTERVAL_MS = 1000;

  private final MutableLiveData<AppUiState> state = new MutableLiveData<>();
  private final Handler handler = new Handler(Looper.getMainLooper());
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
    refreshNow();
  }

  LiveData<AppUiState> state() {
    return state;
  }

  void refreshNow() {
    Context context = getApplication();
    AppUiState current =
        new AppUiState(
            State.prefs(context).getString("phase", "idle"),
            State.prefs(context).getString("message", ""),
            State.prefs(context).getInt("progress", -1));
    state.setValue(current);
    handler.removeCallbacks(poll);
    if (current.busy) {
      handler.postDelayed(poll, POLL_INTERVAL_MS);
    }
  }

  private void reconcileActiveInstall() {
    Context context = getApplication();
    if (!State.busy(context)) {
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
          .remove("pending_launch")
          .apply();
      State.update(context, "success", "Installed", 100);
    } catch (PackageManager.NameNotFoundException ignored) {
      // A new package is not complete yet. Keep polling while the install is active.
    }
  }

  @Override
  protected void onCleared() {
    handler.removeCallbacksAndMessages(null);
    getApplication().unregisterReceiver(stateChanges);
    getApplication().unregisterReceiver(packageChanges);
    super.onCleared();
  }
}
