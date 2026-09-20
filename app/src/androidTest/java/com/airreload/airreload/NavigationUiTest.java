package com.airreload.airreload;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.airreload.shared.history.DownloadOutcomes;
import com.airreload.shared.history.DownloadRecord;
import com.airreload.shared.history.DownloadRecordCodec;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class NavigationUiTest {
  private static final String PAIRING_LINK = "http://192.168.1.20:8080/pair/endpoint"
      + "?airreload_pairing=1&token=abcdefghijklmnopqrstuvwxyzABCDEFG0123456789%3D";
  private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  private MainActivity activity;
  private String originalTheme;
  private boolean originalAutoOpen;
  private String originalPendingLaunch;
  private boolean hadHistory;
  private Set<String> originalHistory;
  private boolean hadLegacyImported;
  private boolean originalLegacyImported;

  @Before public void launch() {
    SharedPreferences preferences = State.prefs(instrumentation.getTargetContext());
    originalTheme = preferences.getString("theme_mode", "system");
    originalAutoOpen = preferences.getBoolean(State.AUTO_OPEN_AFTER_INSTALL, false);
    originalPendingLaunch = preferences.getString(State.PENDING_LAUNCH, null);
    hadHistory = preferences.contains("download_history_v1");
    originalHistory =
        new HashSet<>(preferences.getStringSet("download_history_v1", new HashSet<>()));
    hadLegacyImported = preferences.contains("download_history_legacy_imported");
    originalLegacyImported = preferences.getBoolean("download_history_legacy_imported", false);
    Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    activity = (MainActivity) instrumentation.startActivitySync(intent);
    settle();
  }

  @After public void close() {
    instrumentation.runOnMainSync(() -> activity.finish());
    SharedPreferences.Editor editor =
        State.prefs(instrumentation.getTargetContext())
            .edit()
            .putString("theme_mode", originalTheme)
            .putBoolean(State.AUTO_OPEN_AFTER_INSTALL, originalAutoOpen);
    if (originalPendingLaunch == null) {
      editor.remove(State.PENDING_LAUNCH);
    } else {
      editor.putString(State.PENDING_LAUNCH, originalPendingLaunch);
    }
    if (hadHistory) {
      editor.putStringSet("download_history_v1", originalHistory);
    } else {
      editor.remove("download_history_v1");
    }
    if (hadLegacyImported) {
      editor.putBoolean("download_history_legacy_imported", originalLegacyImported);
    } else {
      editor.remove("download_history_legacy_imported");
    }
    editor.commit();
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

  @Test public void historyLongPressSelectionExitsAtZeroWithoutRebuildingCards() {
    seedHistory(8);
    click("HISTORY");
    settle();

    View[] selectedCard = new View[1];
    View[] additionalCard = new View[1];
    int[] scrollPosition = new int[1];
    instrumentation.runOnMainSync(
        () -> {
          ScrollView scroll = (ScrollView) field("currentScroll");
          scroll.scrollTo(0, 300);
          scrollPosition[0] = scroll.getScrollY();
          selectedCard[0] = longClickableAncestor(find(scroll, "History test 4"));
          assertTrue(selectedCard[0].performLongClick());
        });
    settle();

    instrumentation.runOnMainSync(
        () -> {
          View decor = activity.getWindow().getDecorView();
          View currentCard = longClickableAncestor(find(decor, "History test 4"));
          assertSame("Selection must preserve the card view", selectedCard[0], currentCard);
          assertTrue(currentCard.isSelected());
          assertEquals("Selected", ViewCompat.getStateDescription(currentCard));
          assertTrue(((View) field("historySelectionBar")).isShown());
          assertNotNull(find(decor, "1 selected"));
          assertEquals(scrollPosition[0], ((ScrollView) field("currentScroll")).getScrollY());
          additionalCard[0] = longClickableAncestor(find(decor, "History test 5"));
          assertTrue(additionalCard[0].performClick());
        });
    settle();

    instrumentation.runOnMainSync(
        () -> {
          View decor = activity.getWindow().getDecorView();
          View firstCard = longClickableAncestor(find(decor, "History test 4"));
          View secondCard = longClickableAncestor(find(decor, "History test 5"));
          assertSame(selectedCard[0], firstCard);
          assertSame(additionalCard[0], secondCard);
          assertTrue(firstCard.isSelected());
          assertTrue(secondCard.isSelected());
          assertNotNull(find(decor, "2 selected"));
          assertEquals(scrollPosition[0], ((ScrollView) field("currentScroll")).getScrollY());
          assertTrue(secondCard.performClick());
        });
    settle();

    instrumentation.runOnMainSync(
        () -> {
          View decor = activity.getWindow().getDecorView();
          View firstCard = longClickableAncestor(find(decor, "History test 4"));
          assertTrue(firstCard.isSelected());
          assertFalse(additionalCard[0].isSelected());
          assertTrue(((View) field("historySelectionBar")).isShown());
          assertNotNull(find(decor, "1 selected"));
          assertTrue(firstCard.performClick());
        });
    settle();

    instrumentation.runOnMainSync(
        () -> {
          View decor = activity.getWindow().getDecorView();
          View currentCard = longClickableAncestor(find(decor, "History test 4"));
          assertSame("Deselection must preserve the card view", selectedCard[0], currentCard);
          assertFalse(currentCard.isSelected());
          assertNull(ViewCompat.getStateDescription(currentCard));
          assertFalse((Boolean) field("historySelectionMode"));
          assertEquals(View.GONE, ((View) field("historySelectionBar")).getVisibility());
          assertEquals("SELECT ALL", ((TextView) field("historySelectAction")).getText());
          assertNull("Zero-selection UI must not remain visible", findShown(decor, "0 selected"));
          assertEquals(scrollPosition[0], ((ScrollView) field("currentScroll")).getScrollY());
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
        if (mode.equals("Dark")) {
          assertEquals(Configuration.UI_MODE_NIGHT_YES, night);
          assertEquals(Color.rgb(23, 33, 43), field("CREAM"));
          assertEquals(Color.rgb(35, 46, 60), field("SURFACE"));
          assertEquals(Color.rgb(112, 185, 237), field("GREEN"));
          assertEquals(Color.rgb(23, 33, 43), activity.getWindow().getStatusBarColor());
          assertEquals(Color.rgb(23, 33, 43), activity.getWindow().getNavigationBarColor());
        }
        if (mode.equals("Light")) {
          assertEquals(Configuration.UI_MODE_NIGHT_NO, night);
          assertEquals(Color.rgb(247, 248, 251), field("CREAM"));
          assertEquals(Color.WHITE, field("SURFACE"));
        }
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
      assertTrue(find(decor, "Pair with computer").isShown());
      clickAncestor(find(decor, "Pair with computer"));
      assertTrue(find(decor, "Enter an Airreload pairing link. Direct APK links are not supported.").isShown());
      assertFalse(State.busy(activity));
    });
    click("Enter URL manually");
    settle();
    instrumentation.runOnMainSync(() -> {
      assertFalse(find(activity.getWindow().getDecorView(), "Pair with computer").isShown());
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
      activity.getWindow().getDecorView().findViewsWithText(fields, "Airreload pairing URL",
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
      View submit = find(decor, "Pair with computer");
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
        accept.invoke(activity, PAIRING_LINK);
      } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    });
    settle();
    instrumentation.runOnMainSync(() -> {
      View decor = sheet().getWindow().getDecorView();
      assertNotNull(find(decor, "http://192.168.1.20:8080"));
      assertNotNull(find(decor, "Pair and download"));
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
    instrumentation.runOnMainSync(() -> urlInput().setText(PAIRING_LINK));
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
      assertEquals(PAIRING_LINK, urlInput().getText().toString());
    });
    click("Pair with computer");
    settle();
    instrumentation.runOnMainSync(() -> {
      assertNotNull(sheet());
      assertNotNull(find(sheet().getWindow().getDecorView(), "http://192.168.1.20:8080"));
      assertFalse(State.busy(activity));
      assertNull(State.prefs(activity).getString("queued_url", null));
    });
  }

  @Test public void scannerRejectsApkUrlsAndUnrelatedCodesWithoutTakingAction() {
    for (String input : new String[] {"https://example.org/app.apk", "https://example.org", "hello"}) {
      instrumentation.runOnMainSync(() -> {
        try {
          java.lang.reflect.Method accept = MainActivity.class.getDeclaredMethod("acceptLink", String.class);
          accept.setAccessible(true);
          accept.invoke(activity, input);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        assertNull(sheet());
        assertFalse(State.busy(activity));
        assertNull(State.prefs(activity).getString("queued_url", null));
        assertEquals("error", State.prefs(activity).getString("phase", ""));
      });
    }
  }

  @Test public void manualEntryRejectsApkUrlsAndUnrelatedTextWithoutTakingAction() {
    click("Enter URL manually");
    settle();
    for (String input : new String[] {"https://example.org/app.apk", "https://example.org", "hello"}) {
      instrumentation.runOnMainSync(() -> {
        urlInput().setText(input);
        clickAncestor(find(activity.getWindow().getDecorView(), "Pair with computer"));
        assertNull(sheet());
        assertFalse(State.busy(activity));
        assertNull(State.prefs(activity).getString("queued_url", null));
        assertTrue(find(activity.getWindow().getDecorView(),
            "Enter an Airreload pairing link. Direct APK links are not supported.").isShown());
      });
    }
  }

  @Test public void postInstallPromptOffersAndRemembersAutoOpen() {
    instrumentation.runOnMainSync(() -> {
      State.prefs(activity)
          .edit()
          .putBoolean(State.AUTO_OPEN_AFTER_INSTALL, false)
          .putString(State.PENDING_LAUNCH, activity.getPackageName())
          .commit();
      invoke("maybeHandlePendingLaunch");
    });
    settle();
    instrumentation.runOnMainSync(() -> {
      View decor = sheet().getWindow().getDecorView();
      assertNotNull(find(decor, "Installed successfully"));
      assertNotNull(find(decor, "Airreload Go is ready"));
      View choice = find(decor, "Always open apps after installation");
      assertTrue(choice instanceof CheckBox);
      ((CheckBox) choice).setChecked(true);
      assertTrue(State.prefs(activity).getBoolean(State.AUTO_OPEN_AFTER_INSTALL, false));
      clickAncestor(find(decor, "Not now"));
    });
    settle();
    instrumentation.runOnMainSync(
        () -> assertFalse(State.prefs(activity).contains(State.PENDING_LAUNCH)));
  }

  @Test public void autoOpenPreferenceCanBeChangedInSettings() {
    State.prefs(activity).edit().putBoolean(State.AUTO_OPEN_AFTER_INSTALL, false).commit();
    click("Settings");
    settle();
    click("Open installed apps automatically");
    instrumentation.runOnMainSync(
        () -> assertTrue(State.prefs(activity).getBoolean(State.AUTO_OPEN_AFTER_INSTALL, false)));
  }

  private EditText urlInput() {
    java.util.ArrayList<View> fields = new java.util.ArrayList<>();
    activity.getWindow().getDecorView().findViewsWithText(fields, "Airreload pairing URL",
        View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
    return (EditText) fields.get(0);
  }

  private void seedHistory(int count) {
    Set<String> encoded = new HashSet<>();
    for (int index = 0; index < count; index++) {
      DownloadRecord record =
          new DownloadRecord(
              "test-history-" + index,
              10_000L - index,
              "com.airreload.test.missing." + index,
              "History test " + index,
              "1.0." + index,
              index + 1,
              "example.org",
              DownloadOutcomes.DOWNLOADED,
              "",
              0);
      encoded.add(DownloadRecordCodec.INSTANCE.encode(record));
    }
    State.prefs(instrumentation.getTargetContext())
        .edit()
        .putStringSet("download_history_v1", encoded)
        .putBoolean("download_history_legacy_imported", true)
        .commit();
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

  private View findShown(View root, String text) {
    View match = find(root, text);
    return match != null && match.isShown() ? match : null;
  }

  private View longClickableAncestor(View view) {
    assertNotNull("Expected history item", view);
    while (!view.isLongClickable() && view.getParent() instanceof View) {
      view = (View) view.getParent();
    }
    assertTrue("History item must respond to a long press", view.isLongClickable());
    return view;
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

  private void invoke(String name) {
    try {
      java.lang.reflect.Method method = MainActivity.class.getDeclaredMethod(name);
      method.setAccessible(true);
      method.invoke(activity);
    } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
  }

  private BottomSheetDialog sheet() { return (BottomSheetDialog) field("activeSheet"); }

  private void settle() {
    instrumentation.waitForIdleSync();
    SystemClock.sleep(800);
    instrumentation.waitForIdleSync();
  }
}
