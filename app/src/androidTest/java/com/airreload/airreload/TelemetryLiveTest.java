package com.airreload.airreload;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.materialswitch.MaterialSwitch;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Opt-in live smoke test. Run only on a disposable emulator; sends real PostHog events. */
@RunWith(AndroidJUnit4.class)
public final class TelemetryLiveTest {
  private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  private MainActivity activity;

  @Test public void settingsOptOutSurvivesRelaunchAndReenableRotatesIdentity() {
    assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("livePosthog")));
    Context context = instrumentation.getTargetContext();
    SharedPreferences prefs = context.getSharedPreferences("airreload_analytics", Context.MODE_PRIVATE);
    assertTrue("Use an enabled disposable test installation", Telemetry.enabled(context));
    try {
      launch();
      SystemClock.sleep(3000);
      String firstId = prefs.getString("installation_id", null);
      assertNotNull(firstId);
      android.util.Log.i("TelemetryLiveTest", "enabled installation=" + firstId);
      instrumentation.runOnMainSync(() -> {
        clickLabel("Settings");
        MaterialSwitch toggle = toggle(activity.getWindow().getDecorView());
        assertNotNull(toggle);
        assertTrue(toggle.isChecked());
        toggle.performClick();
        assertFalse(toggle.isChecked());
      });
      assertFalse(Telemetry.enabled(context));
      assertNull(prefs.getString("installation_id", null));
      finish();
      launch();
      // Both activity startup and an explicit capture attempt must remain suppressed.
      Telemetry.capture(context, Analytics.Event.PAIRING_STARTED);
      SystemClock.sleep(3000);
      assertFalse(prefs.getBoolean("enabled", true));
      assertNull(prefs.getString("installation_id", null));
      instrumentation.runOnMainSync(() -> {
        clickLabel("Settings");
        MaterialSwitch toggle = toggle(activity.getWindow().getDecorView());
        assertNotNull(toggle);
        assertFalse(toggle.isChecked());
        toggle.performClick();
        assertTrue(toggle.isChecked());
      });
      finish();
      launch();
      SystemClock.sleep(3000);
      String secondId = prefs.getString("installation_id", null);
      assertNotNull(secondId);
      assertNotEquals(firstId, secondId);
      android.util.Log.i("TelemetryLiveTest", "reenabled installation=" + secondId);
    } finally {
      finish();
    }
  }

  private void launch() {
    activity = (MainActivity) instrumentation.startActivitySync(new Intent(
        instrumentation.getTargetContext(), MainActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    instrumentation.waitForIdleSync();
  }

  private void finish() {
    if (activity != null) instrumentation.runOnMainSync(() -> activity.finish());
    instrumentation.waitForIdleSync();
    activity = null;
  }

  private void clickLabel(String label) {
    View view = findLabel(activity.getWindow().getDecorView(), label);
    assertNotNull(view);
    while (!view.isClickable() && view.getParent() instanceof View) view = (View) view.getParent();
    assertTrue(view.performClick());
  }

  private View findLabel(View view, String label) {
    if (view instanceof TextView && label.contentEquals(((TextView) view).getText())) return view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View found = findLabel(group.getChildAt(i), label);
        if (found != null) return found;
      }
    }
    return null;
  }

  private MaterialSwitch toggle(View view) {
    if (view instanceof MaterialSwitch && "Share usage analytics".contentEquals(
        view.getContentDescription() == null ? "" : view.getContentDescription())) return (MaterialSwitch) view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        MaterialSwitch found = toggle(group.getChildAt(i));
        if (found != null) return found;
      }
    }
    return null;
  }
}
