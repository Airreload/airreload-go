package com.airreload.airreload;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.lang.reflect.Field;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class NavigationUiTest {
  private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  private MainActivity activity;
  private String originalTheme;

  @Before public void launch() {
    originalTheme = State.prefs(instrumentation.getTargetContext()).getString("theme_mode", "system");
    Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    activity = (MainActivity) instrumentation.startActivitySync(intent);
    settle();
  }

  @After public void close() {
    instrumentation.runOnMainSync(() -> activity.finish());
    State.prefs(instrumentation.getTargetContext()).edit().putString("theme_mode", originalTheme).commit();
  }

  @Test public void historyIsFullScreenAndBackRestoresHome() {
    click("HISTORY");
    settle();
    instrumentation.runOnMainSync(() -> {
      assertNotNull(find(activity.getWindow().getDecorView(), "Download history"));
      assertEquals(View.GONE, navigation(activity.getWindow().getDecorView()).getVisibility());
      activity.getOnBackPressedDispatcher().onBackPressed();
    });
    settle();
    instrumentation.runOnMainSync(() -> {
      assertNotNull(find(activity.getWindow().getDecorView(), "Airreload Go"));
      assertEquals(View.VISIBLE, navigation(activity.getWindow().getDecorView()).getVisibility());
    });
  }

  @Test public void themeChangesKeepTheActivityAndSettingsPage() {
    click("Settings");
    settle();
    for (String mode : new String[] {"Dark", "Light", "System"}) {
      click("Theme");
      settle();
      instrumentation.runOnMainSync(() -> {
        BottomSheetDialog sheet = sheet();
        View label = find(sheet.getWindow().getDecorView(), mode);
        assertNotNull(label);
        clickAncestor(label);
      });
      settle();
      instrumentation.runOnMainSync(() -> {
        assertFalse("Theme change must not recreate the activity", activity.isDestroyed());
        assertNotNull(find(activity.getWindow().getDecorView(), "Theme"));
        assertNotNull(find(activity.getWindow().getDecorView(), mode));
        int night = activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (mode.equals("Dark")) assertEquals(Configuration.UI_MODE_NIGHT_YES, night);
        if (mode.equals("Light")) assertEquals(Configuration.UI_MODE_NIGHT_NO, night);
        assertNull("Theme overlay must be released", field("themeOverlay"));
      });
    }
  }

  @Test public void inlineUrlValidatesAndCollapsesWithoutStartingDownload() {
    click("Enter URL manually");
    settle();
    instrumentation.runOnMainSync(() -> {
      assertNull("URL entry must remain inline", sheet());
      View decor = activity.getWindow().getDecorView();
      assertTrue(find(decor, "Download app").isShown());
      clickAncestor(find(decor, "Download app"));
      assertTrue(find(decor, "Enter an app URL.").isShown());
      assertFalse(State.busy(activity));
    });
    click("Enter URL manually");
    settle();
    instrumentation.runOnMainSync(() -> {
      assertFalse(find(activity.getWindow().getDecorView(), "Download app").isShown());
      assertNull(field("activeSheet"));
    });
  }

  @Test public void interruptedTransitionsLeaveOnlyOnePage() {
    instrumentation.runOnMainSync(() -> {
      AppRouter router = (AppRouter) field("router");
      for (int i = 0; i < 8; i++) {
        router.navigate(AppRouter.Route.HISTORY);
        router.back();
        router.navigate(AppRouter.Route.SETTINGS);
        router.navigate(AppRouter.Route.HOME);
      }
    });
    settle();
    instrumentation.runOnMainSync(() -> {
      ViewGroup host = (ViewGroup) field("pageHost");
      assertEquals(1, host.getChildCount());
      assertEquals(1f, host.getChildAt(0).getAlpha(), .001f);
      assertEquals(0f, host.getChildAt(0).getTranslationX(), .001f);
    });
  }

  @Test public void urlActionRemainsReachableAboveTheKeyboard() {
    click("Enter URL manually");
    settle();
    instrumentation.runOnMainSync(() -> {
      java.util.ArrayList<View> fields = new java.util.ArrayList<>();
      activity.getWindow().getDecorView().findViewsWithText(fields, "App download URL",
          View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
      EditText input = (EditText) fields.get(0);
      input.requestFocus();
      activity.getSystemService(InputMethodManager.class).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
    });
    settle();
    instrumentation.runOnMainSync(() -> {
      View decor = activity.getWindow().getDecorView();
      WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(decor);
      assertTrue("Keyboard must be shown for this check", insets.isVisible(WindowInsetsCompat.Type.ime()));
      Rect visible = new Rect();
      decor.getWindowVisibleDisplayFrame(visible);
      View submit = find(decor, "Download app");
      Rect button = new Rect();
      assertTrue("Download action must be visible", submit.getGlobalVisibleRect(button));
      assertTrue("Download action must sit above the keyboard", button.bottom <= visible.bottom);
      assertEquals("Download action must not be clipped", submit.getHeight(), button.height());
    });
  }

  @Test public void recreationRestoresHistoryAndBackDestination() {
    click("HISTORY");
    settle();
    Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(MainActivity.class.getName(), null, false);
    instrumentation.runOnMainSync(() -> activity.recreate());
    MainActivity recreated = (MainActivity) instrumentation.waitForMonitorWithTimeout(monitor, 5000);
    instrumentation.removeMonitor(monitor);
    assertNotNull(recreated);
    activity = recreated;
    settle();
    instrumentation.runOnMainSync(() -> {
      assertNotNull(find(activity.getWindow().getDecorView(), "Download history"));
      activity.getOnBackPressedDispatcher().onBackPressed();
    });
    settle();
    instrumentation.runOnMainSync(() -> assertNotNull(find(activity.getWindow().getDecorView(), "Airreload Go")));
  }

  @Test public void scannedLinkRequiresConsentAndCancellationStartsNothing() {
    instrumentation.runOnMainSync(() -> {
      try {
        java.lang.reflect.Method accept = MainActivity.class.getDeclaredMethod("acceptLink", String.class);
        accept.setAccessible(true);
        accept.invoke(activity, "https://example.org/app.apk?token=private-test-token");
      } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    });
    settle();
    instrumentation.runOnMainSync(() -> {
      View decor = sheet().getWindow().getDecorView();
      assertNotNull(find(decor, "https://example.org"));
      assertNotNull(find(decor, "Download and review"));
      assertFalse(State.busy(activity));
      assertNull(State.prefs(activity).getString("queued_url", null));
      sheet().cancel();
    });
    settle();
    instrumentation.runOnMainSync(() -> {
      assertFalse(State.busy(activity));
      assertNull(State.prefs(activity).getString("queued_url", null));
      assertNull(field("activeSheet"));
    });
  }

  @Test public void inlineDraftSurvivesCollapseAndNavigationAndStillRequiresConsent() {
    click("Enter URL manually");
    settle();
    instrumentation.runOnMainSync(() -> urlInput().setText("https://example.org/app.apk"));
    click("Enter URL manually");
    settle();
    click("Enter URL manually");
    settle();
    click("Settings");
    settle();
    click("Home");
    settle();
    instrumentation.runOnMainSync(() -> {
      assertTrue(urlInput().isShown());
      assertEquals("https://example.org/app.apk", urlInput().getText().toString());
    });
    click("Download app");
    settle();
    instrumentation.runOnMainSync(() -> {
      assertNotNull(sheet());
      assertNotNull(find(sheet().getWindow().getDecorView(), "https://example.org"));
      assertFalse(State.busy(activity));
      assertNull(State.prefs(activity).getString("queued_url", null));
    });
  }

  private EditText urlInput() {
    java.util.ArrayList<View> fields = new java.util.ArrayList<>();
    activity.getWindow().getDecorView().findViewsWithText(fields, "App download URL",
        View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
    return (EditText) fields.get(0);
  }

  private void click(String text) {
    instrumentation.runOnMainSync(() -> clickAncestor(find(activity.getWindow().getDecorView(), text)));
  }

  private void clickAncestor(View view) {
    assertNotNull("Expected control", view);
    while (!view.isClickable() && view.getParent() instanceof View) view = (View) view.getParent();
    assertTrue("Control must respond to a click", view.performClick());
  }

  private View find(View root, String text) {
    if (root instanceof TextView && text.contentEquals(((TextView) root).getText())) return root;
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) {
        View match = find(group.getChildAt(i), text);
        if (match != null) return match;
      }
    }
    return null;
  }

  private BottomNavigationView navigation(View root) {
    if (root instanceof BottomNavigationView) return (BottomNavigationView) root;
    if (root instanceof ViewGroup) {
      for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
        BottomNavigationView match = navigation(((ViewGroup) root).getChildAt(i));
        if (match != null) return match;
      }
    }
    return null;
  }

  private Object field(String name) {
    try {
      Field field = MainActivity.class.getDeclaredField(name);
      field.setAccessible(true);
      return field.get(activity);
    } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
  }

  private BottomSheetDialog sheet() { return (BottomSheetDialog) field("activeSheet"); }

  private void settle() {
    instrumentation.waitForIdleSync();
    SystemClock.sleep(800);
    instrumentation.waitForIdleSync();
  }
}
