package com.airreload.airreload;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.PathInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.airreload.shared.history.DownloadOutcomes;
import com.airreload.shared.history.DownloadRecord;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.shape.MaterialShapeDrawable;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MainActivity extends AppCompatActivity {
  private static final String THEME_SYSTEM = "system";
  private static final String THEME_LIGHT = "light";
  private static final String THEME_DARK = "dark";

  private int CREAM;
  private int SURFACE;
  private int FOREST;
  private int GREEN;
  private int MINT;
  private int PALE_MINT;
  private int LIME;
  private int MUTED;
  private int BORDER;
  private int ERROR;
  private int ERROR_BG;

  private LinearLayout library;
  private FrameLayout pageHost;
  private View appContent;
  private BottomNavigationView bottomNavigation;
  private static final int HOME_TAB = 1001;
  private static final int SETTINGS_TAB = 1002;
  private final AppRouter router = new AppRouter();
  private final PageTransitions pageTransitions = new PageTransitions();
  private final Map<AppRouter.Route, Integer> scrollPositions = new HashMap<>();
  private ScrollView currentScroll;
  private boolean applyingTheme;
  private View themeOverlay;
  private BottomSheetDialog activeSheet;
  private TextView libraryCount;
  private LinearLayout historyRoot;
  private LinearLayout historyList;
  private LinearLayout historySelectionBar;
  private TextView historySummary;
  private TextView historySelectionCount;
  private TextView historySelectAction;
  private TextView historySelectAllAction;
  private View historyEmptyState;
  private final Map<String, HistoryCardBinding> historyCards = new HashMap<>();
  private final Set<String> selectedHistory = new HashSet<>();
  private OnBackPressedCallback historyBack;
  private boolean historyPage;
  private boolean historySelectionMode;
  private EditText search;
  private Button scanButton;
  private Button urlSubmit;
  private LinearLayout flowStatus;
  private ProgressBar flowSpinner;
  private ProgressBar flowProgress;
  private TextView flowTitle;
  private TextView flowDetail;
  private TextView flowAction;
  private boolean urlExpanded;
  private String urlDraft = "";
  private AppStateViewModel appState;
  private AppUiState currentState = new AppUiState("idle", "", -1);
  private String lastTerminalState = "";
  private boolean foreground;
  private boolean onboardingVisible;
  private String promptedLaunchPackage;
  private String query = "";

  private ActivityResultLauncher<ScanOptions> scanner;
  private ActivityResultLauncher<String> cameraPermission;
  private ActivityResultLauncher<Intent> sourceSettings;
  private ActivityResultLauncher<String> notificationPermission;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    applyThemePreference();
    super.onCreate(savedInstanceState);
    // Do not resume a direct APK URL saved by versions before pairing-only mode.
    if (!State.prefs(this).getBoolean("queued_from_pairing", false)) {
      State.prefs(this).edit().remove("queued_url").apply();
    }
    syncLauncherIcon();
    configurePalette();
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    configureSystemBars();
    configureLaunchers();
    if (savedInstanceState != null) {
      router.restore(savedInstanceState.getString("route"));
      query = savedInstanceState.getString("query", "");
      urlExpanded = savedInstanceState.getBoolean("url_expanded", false);
      urlDraft = savedInstanceState.getString("url_draft", "");
      for (AppRouter.Route route : AppRouter.Route.values()) {
        scrollPositions.put(route, savedInstanceState.getInt("scroll_" + route, 0));
      }
    }
    buildUi();
    historyBack =
        new OnBackPressedCallback(false) {
          @Override
          public void handleOnBackPressed() {
            if (historySelectionMode) {
              clearHistorySelection();
            } else {
              router.back();
            }
          }
    };
    getOnBackPressedDispatcher().addCallback(this, historyBack);
    historyBack.setEnabled(router.canGoBack());
    router.setListener((previous, current, back) -> renderRoute(previous, back, true));
    appState = new ViewModelProvider(this).get(AppStateViewModel.class);
    appState.state().observe(this, this::renderState);
  }

  private void applyThemePreference() {
    String mode = State.prefs(this).getString("theme_mode", THEME_SYSTEM);
    int nightMode;
    if (THEME_LIGHT.equals(mode)) {
      nightMode = AppCompatDelegate.MODE_NIGHT_NO;
    } else if (THEME_DARK.equals(mode)) {
      nightMode = AppCompatDelegate.MODE_NIGHT_YES;
    } else {
      nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }
    getDelegate().setLocalNightMode(nightMode);
  }

  private void configurePalette() {
    String mode = State.prefs(this).getString("theme_mode", THEME_SYSTEM);
    boolean systemDark =
        (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
    boolean dark = THEME_DARK.equals(mode) || (THEME_SYSTEM.equals(mode) && systemDark);
    if (dark) {
      CREAM = Color.rgb(23, 33, 43);
      SURFACE = Color.rgb(35, 46, 60);
      FOREST = Color.rgb(237, 242, 246);
      GREEN = Color.rgb(112, 185, 237);
      MINT = Color.rgb(188, 202, 213);
      PALE_MINT = Color.rgb(37, 58, 77);
      LIME = Color.rgb(112, 185, 237);
      MUTED = Color.rgb(142, 158, 172);
      BORDER = Color.rgb(53, 68, 84);
      ERROR = Color.rgb(255, 142, 136);
      ERROR_BG = Color.rgb(48, 25, 28);
    } else {
      CREAM = Color.rgb(247, 248, 251);
      SURFACE = Color.WHITE;
      FOREST = Color.rgb(24, 27, 33);
      GREEN = Color.rgb(4, 104, 215);
      MINT = Color.rgb(91, 99, 112);
      PALE_MINT = Color.rgb(229, 243, 255);
      LIME = Color.rgb(84, 197, 248);
      MUTED = Color.rgb(101, 109, 121);
      BORDER = Color.rgb(218, 222, 230);
      ERROR = Color.rgb(180, 48, 48);
      ERROR_BG = Color.rgb(255, 239, 239);
    }
  }

  private void configureSystemBars() {
    boolean light = Color.red(CREAM) > 128;
    getWindow().setStatusBarColor(CREAM);
    getWindow().setNavigationBarColor(CREAM);
    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
        .setAppearanceLightStatusBars(light);
    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
        .setAppearanceLightNavigationBars(light);
  }

  private void setThemeMode(String mode) {
    if (mode.equals(State.prefs(this).getString("theme_mode", THEME_SYSTEM))) {
      return;
    }
    State.prefs(this).edit().putString("theme_mode", mode).apply();
    applyingTheme = true;
    applyThemePreference();
    syncLauncherIcon();
    applyingTheme = false;
    rebuildTheme();
  }

  @Override
  public void onConfigurationChanged(Configuration configuration) {
    super.onConfigurationChanged(configuration);
    syncLauncherIcon();
    if (!applyingTheme && pageHost != null) rebuildTheme();
  }

  private void syncLauncherIcon() {
    String mode = State.prefs(this).getString("theme_mode", THEME_SYSTEM);
    LauncherIconController.sync(this, mode);
  }

  private void rebuildTheme() {
    pageTransitions.finish();
    finishThemeTransition();
    if (activeSheet != null) activeSheet.dismiss();
    if (currentScroll != null) scrollPositions.put(router.current(), currentScroll.getScrollY());
    View previous = appContent;
    configurePalette();
    configureSystemBars();
    buildUi();
    if (!ValueAnimator.areAnimatorsEnabled() || previous == null) return;
    FrameLayout stage = (FrameLayout) appContent;
    themeOverlay = previous;
    previous.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    // Block touches on the fading page while keeping its already-rendered colors intact.
    ((ViewGroup) previous).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    View touchGuard = new View(this);
    touchGuard.setClickable(true);
    ((FrameLayout) previous).addView(touchGuard, new FrameLayout.LayoutParams(-1, -1));
    stage.addView(previous, new FrameLayout.LayoutParams(-1, -1));
    previous.animate().alpha(0f).setDuration(320)
        .setInterpolator(new PathInterpolator(.2f, 0f, 0f, 1f))
        .withEndAction(this::finishThemeTransition).start();
  }

  private void finishThemeTransition() {
    if (themeOverlay == null) return;
    View overlay = themeOverlay;
    themeOverlay = null;
    overlay.animate().withEndAction(null);
    overlay.animate().cancel();
    if (overlay.getParent() instanceof ViewGroup) ((ViewGroup) overlay.getParent()).removeView(overlay);
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    state.putString("route", router.save());
    state.putString("query", query);
    state.putBoolean("url_expanded", urlExpanded);
    state.putString("url_draft", urlDraft);
    if (currentScroll != null) scrollPositions.put(router.current(), currentScroll.getScrollY());
    for (Map.Entry<AppRouter.Route, Integer> entry : scrollPositions.entrySet()) {
      state.putInt("scroll_" + entry.getKey(), entry.getValue());
    }
  }

  @Override
  protected void onDestroy() {
    router.setListener(null);
    pageTransitions.finish();
    finishThemeTransition();
    if (activeSheet != null) {
      activeSheet.setOnDismissListener(null);
      activeSheet.dismiss();
    }
    super.onDestroy();
  }

  private void configureLaunchers() {
    scanner =
        registerForActivityResult(
            new ScanContract(),
            result -> {
              if (result.getContents() != null) {
                acceptLink(result.getContents());
              }
            });
    cameraPermission =
        registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
              if (granted) {
                openCamera();
              } else {
                showMessage(
                    "Camera access is off",
                    "Allow camera access in Android Settings to scan a QR code.");
              }
            });
    sourceSettings =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              boolean granted = getPackageManager().canRequestPackageInstalls();
              State.prefs(this).edit().putBoolean("onboarding_seen", true).apply();
              if (granted) {
                String queued = State.prefs(this).getString("queued_url", null);
                if (queued == null) {
                  State.update(
                      this,
                      "idle",
                      "All set. Scan an Airreload pairing QR code whenever you’re ready.",
                      0);
                }
                maybeAskNotificationsAndContinue();
              } else if (State.prefs(this).getString("queued_url", null) != null) {
                State.update(
                    this,
                    "permission_needed",
                    "Allow from this source is still off. Your scanned link is saved safely.",
                    0);
              } else {
                State.update(
                    this,
                    "idle",
                    "Install permission is still off. Airreload Go will ask again when you scan.",
                    0);
              }
              refreshStatus();
            });
    notificationPermission =
        registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> continueQueuedDownload());
  }

  @Override
  protected void onResume() {
    super.onResume();
    foreground = true;
    recoverInterruptedFlow();
    if (appState != null) {
      appState.refreshNow();
    }
    maybeAskNotificationsAndContinue();
    showPendingInstall(false);
    maybeHandlePendingLaunch();
  }

  @Override
  protected void onPause() {
    foreground = false;
    super.onPause();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    pageHost.post(() -> showPendingInstall(true));
  }

  private void recoverInterruptedFlow() {
    // Pairing has no Android install session; the ViewModel owns its lifetime.
    if ("pairing".equals(State.prefs(this).getString("phase", ""))) return;
    if (!State.busy(this)) {
      return;
    }
    int sessionId = State.prefs(this).getInt("session", -1);
    boolean serviceAlive = false;
    ActivityManager manager = getSystemService(ActivityManager.class);
    for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
      if (InstallService.class.getName().equals(service.service.getClassName())) {
        serviceAlive = true;
        break;
      }
    }
    boolean sessionAlive =
        sessionId >= 0
            && getPackageManager().getPackageInstaller().getSessionInfo(sessionId) != null;
    if (!serviceAlive && !sessionAlive) {
      Installer.cancel(this);
      State.update(
          this,
          "error",
          "The previous install expired or was interrupted. Scan the code to start fresh.",
          0);
    }
  }

  private void buildUi() {
    FrameLayout stage = new FrameLayout(this);
    appContent = stage;
    stage.setBackgroundColor(CREAM);
    LinearLayout shell = column();
    stage.addView(
        shell,
        new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    pageHost = new FrameLayout(this);
    shell.addView(
        pageHost,
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    bottomNavigation = buildBottomNavigation();
    shell.addView(bottomNavigation);

    ViewCompat.setOnApplyWindowInsetsListener(
        stage,
        (view, insets) -> {
          androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
          int keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
          shell.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard));
          if (bottomNavigation != null) {
            bottomNavigation.setVisibility(keyboard > 0 || historyPage ? View.GONE : View.VISIBLE);
          }
          return insets;
        });
    setContentView(stage);
    renderRoute(null, false, false);
  }

  private BottomNavigationView buildBottomNavigation() {
    BottomNavigationView navigation = new BottomNavigationView(this);
    navigation.setBackgroundColor(CREAM);
    navigation.setElevation(0f);
    // The shell already owns the system bar insets.
    ViewCompat.setOnApplyWindowInsetsListener(navigation, (view, insets) -> insets);
    navigation.setItemHorizontalTranslationEnabled(false);
    navigation.setItemIconSize(dp(24));
    navigation.setItemActiveIndicatorColor(ColorStateList.valueOf(PALE_MINT));
    navigation.setItemActiveIndicatorWidth(dp(64));
    navigation.setItemActiveIndicatorHeight(dp(32));
    ColorStateList tint = new ColorStateList(
        new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
        new int[] {GREEN, MUTED});
    navigation.setItemIconTintList(tint);
    navigation.setItemTextColor(tint);
    navigation.getMenu().add(0, HOME_TAB, 0, "Home").setIcon(R.drawable.ic_home);
    navigation.getMenu().add(0, SETTINGS_TAB, 1, "Settings").setIcon(R.drawable.ic_settings);
    navigation.setOnItemSelectedListener(item -> {
      router.navigate(item.getItemId() == HOME_TAB ? AppRouter.Route.HOME : AppRouter.Route.SETTINGS);
      return true;
    });
    navigation.setOnItemReselectedListener(item -> {
      if (currentScroll != null) currentScroll.smoothScrollTo(0, 0);
    });
    navigation.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(80)));
    return navigation;
  }

  private void selectTab(boolean home) {
    bottomNavigation.getMenu().findItem(home ? HOME_TAB : SETTINGS_TAB).setChecked(true);
  }

  private void renderRoute(AppRouter.Route previousRoute, boolean back, boolean animate) {
    pageTransitions.finish();
    if (previousRoute != null && currentScroll != null) {
      scrollPositions.put(previousRoute, currentScroll.getScrollY());
    }
    View previous = pageHost.getChildCount() == 0 ? null : pageHost.getChildAt(0);
    library = null;
    search = null;
    scanButton = null;
    urlSubmit = null;
    flowStatus = null;
    clearHistoryCardBindings();
    historyRoot = null;
    historyList = null;
    historyEmptyState = null;
    switch (router.current()) {
      case HOME: showHomePage(); break;
      case SETTINGS: showSettingsPage(); break;
      case HISTORY: showHistoryPage(); break;
    }
    if (historyBack != null) historyBack.setEnabled(router.canGoBack());
    ScrollView scroll = currentScroll;
    int scrollY = scrollPositions.getOrDefault(router.current(), 0);
    scroll.post(() -> scroll.scrollTo(0, scrollY));
    if (animate) pageTransitions.play(pageHost, previous, pageHost.getChildAt(0), back);
  }

  private void showHomePage() {
    if (pageHost == null) {
      return;
    }
    historyPage = false;
    historySelectionMode = false;
    selectedHistory.clear();
    historyList = null;
    if (historyBack != null) {
      historyBack.setEnabled(false);
    }
    bottomNavigation.setVisibility(View.VISIBLE);
    selectTab(true);
    pageHost.removeAllViews();
    LinearLayout page = column();
    page.setBackgroundColor(CREAM);
    pageHost.addView(
        page,
        new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    MaxWidthColumn stickyHeader = new MaxWidthColumn(this);
    stickyHeader.setOrientation(LinearLayout.VERTICAL);
    stickyHeader.setPadding(dp(24), dp(14), dp(24), dp(14));
    stickyHeader.setBackgroundColor(CREAM);
    stickyHeader.addView(buildCompactHeader());
    page.addView(
        stickyHeader,
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    ScrollView scroll = new ScrollView(this);
    currentScroll = scroll;
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    MaxWidthColumn root = new MaxWidthColumn(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(24), dp(20), dp(24), dp(30));
    scroll.addView(
        root,
        new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    page.addView(
        scroll,
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

    root.addView(buildActionCard());
    root.addView(buildFlowStatus());
    refreshFlowStatus();
    root.addView(space(30));
    root.addView(buildLibraryHeader());
    root.addView(space(12));
    root.addView(buildSearch());
    root.addView(space(12));
    library = column();
    root.addView(library);
    refreshLibrary();
  }

  private View buildCompactHeader() {
    LinearLayout header = row();
    header.setGravity(Gravity.CENTER_VERTICAL);
    TextView name = text("Airreload Go", 23, FOREST, true);
    LinearLayout.LayoutParams nameParams =
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    header.addView(name, nameParams);
    TextView history = text("HISTORY", 11, MUTED, true);
    history.setLetterSpacing(.08f);
    history.setGravity(Gravity.CENTER);
    history.setContentDescription("Open download history");
    history.setFocusable(true);
    history.setBackground(outline(SURFACE, BORDER, 10));
    history.setOnClickListener(view -> router.navigate(AppRouter.Route.HISTORY));
    header.addView(history, new LinearLayout.LayoutParams(dp(82), dp(40)));
    return header;
  }

  private View buildFlowStatus() {
    flowStatus = column();
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
    params.topMargin = dp(12);
    flowStatus.setLayoutParams(params);
    flowStatus.setPadding(dp(14), dp(6), dp(4), dp(6));
    LinearLayout line = row();
    line.setGravity(Gravity.CENTER_VERTICAL);
    flowSpinner = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
    flowSpinner.setIndeterminateTintList(ColorStateList.valueOf(GREEN));
    flowSpinner.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(dp(18), dp(18));
    spinnerParams.setMarginEnd(dp(12));
    line.addView(flowSpinner, spinnerParams);
    LinearLayout copy = column();
    flowTitle = text("", 13, GREEN, true);
    flowTitle.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    flowDetail = text("", 12, MUTED, false);
    copy.addView(flowTitle);
    copy.addView(flowDetail);
    line.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
    flowAction = text("×", 24, MUTED, false);
    flowAction.setGravity(Gravity.CENTER);
    flowAction.setFocusable(true);
    flowAction.setOnClickListener(view -> {
      if ("error".equals(currentState.phase)) {
        State.update(this, "idle", "", 0);
        refreshStatus();
      } else {
        cancelCurrentFlow();
      }
    });
    line.addView(flowAction, new LinearLayout.LayoutParams(dp(48), dp(48)));
    flowStatus.addView(line);
    flowProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    flowProgress.setProgressTintList(ColorStateList.valueOf(GREEN));
    flowProgress.setProgressBackgroundTintList(ColorStateList.valueOf(BORDER));
    LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(3));
    progressParams.setMarginEnd(dp(10));
    progressParams.bottomMargin = dp(4);
    flowStatus.addView(flowProgress, progressParams);
    return flowStatus;
  }

  private void refreshFlowStatus() {
    if (flowStatus == null) return;
    boolean pairing = "pairing".equals(currentState.phase);
    boolean downloading = "downloading".equals(currentState.phase);
    boolean error = "error".equals(currentState.phase);
    flowStatus.setVisibility(pairing || downloading || error ? View.VISIBLE : View.GONE);
    if (!pairing && !downloading && !error) return;
    String title = error ? "Something went wrong"
        : pairing ? "Pairing…"
        : currentState.progress >= 0 ? "Downloading · " + currentState.progress + "%" : "Downloading…";
    if (!title.contentEquals(flowTitle.getText())) flowTitle.setText(title);
    flowTitle.setTextColor(error ? ERROR : GREEN);
    String detail = currentState.message;
    if (!detail.contentEquals(flowDetail.getText())) flowDetail.setText(detail);
    flowDetail.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);
    flowDetail.setTextColor(error ? ERROR : MUTED);
    flowStatus.setBackground(round(error ? ERROR_BG : PALE_MINT, 12));
    flowSpinner.setVisibility(error ? View.GONE : View.VISIBLE);
    flowProgress.setVisibility(downloading ? View.VISIBLE : View.GONE);
    flowProgress.setIndeterminate(currentState.progress < 0);
    flowProgress.setProgress(Math.max(0, currentState.progress));
    flowAction.setContentDescription(error ? "Dismiss error" : pairing ? "Cancel pairing" : "Cancel download");
  }

  private View buildActionCard() {
    LinearLayout card = column();
    card.setBackground(outline(SURFACE, BORDER, 15));
    Button manual = actionButton("Enter URL manually", R.drawable.ic_link);
    card.addView(manual, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));
    LinearLayout form = buildInlineUrlForm();
    form.setVisibility(urlExpanded ? View.VISIBLE : View.GONE);
    card.addView(form);
    updateUrlDisclosure(manual);
    manual.setOnClickListener(view -> {
      if (ValueAnimator.areAnimatorsEnabled() && card.getParent() instanceof ViewGroup) {
        AutoTransition transition = new AutoTransition();
        transition.setDuration(220);
        transition.setInterpolator(new PathInterpolator(.2f, 0f, 0f, 1f));
        TransitionManager.beginDelayedTransition((ViewGroup) card.getParent(), transition);
      }
      urlExpanded = !urlExpanded;
      form.setVisibility(urlExpanded ? View.VISIBLE : View.GONE);
      updateUrlDisclosure(manual);
      if (!urlExpanded) {
        form.clearFocus();
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(form.getWindowToken(), 0);
      }
    });
    card.addView(divider());

    scanButton = actionButton("Scan pairing QR code", R.drawable.ic_qr_code);
    scanButton.setOnClickListener(view -> requestCameraAndScan());
    card.addView(scanButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));
    return card;
  }

  private void updateUrlDisclosure(Button manual) {
    Drawable chevron = ContextCompat.getDrawable(this,
        urlExpanded ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down).mutate();
    chevron.setTint(MUTED);
    chevron.setBounds(0, 0, dp(20), dp(20));
    Drawable link = manual.getCompoundDrawablesRelative()[0];
    manual.setCompoundDrawablesRelative(link, null, chevron, null);
    ViewCompat.setStateDescription(manual, urlExpanded ? "Expanded" : "Collapsed");
  }

  private Button actionButton(String label, int iconResource) {
    Button action = button(label, Color.TRANSPARENT, FOREST);
    action.setGravity(Gravity.CENTER_VERTICAL);
    action.setPadding(dp(20), 0, dp(20), 0);
    action.setBackgroundColor(Color.TRANSPARENT);
    Drawable icon = ContextCompat.getDrawable(this, iconResource).mutate();
    icon.setTint(GREEN);
    icon.setBounds(0, 0, dp(24), dp(24));
    action.setCompoundDrawablesRelative(icon, null, null, null);
    action.setCompoundDrawablePadding(dp(14));
    return action;
  }

  private View divider() {
    View divider = new View(this);
    divider.setBackgroundColor(BORDER);
    divider.setLayoutParams(
        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
    return divider;
  }

  private void requestCameraAndScan() {
    if (!ready()) {
      return;
    }
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        == PackageManager.PERMISSION_GRANTED) {
      openCamera();
    } else {
      cameraPermission.launch(Manifest.permission.CAMERA);
    }
  }

  private LinearLayout buildInlineUrlForm() {
    LinearLayout content = column();
    content.setPadding(dp(20), dp(4), dp(20), dp(20));
    EditText input = new EditText(this);
    input.setSingleLine(true);
    input.setHint("Paste your Airreload pairing link");
    input.setContentDescription("Airreload pairing URL");
    input.setText(urlDraft);
    input.setTextSize(16);
    input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
    input.setImeOptions(EditorInfo.IME_ACTION_GO);
    input.setTextColor(FOREST);
    input.setHintTextColor(MUTED);
    input.setPadding(dp(18), dp(16), dp(18), dp(16));
    input.setBackground(outline(CREAM, BORDER, 16));
    input.setOnFocusChangeListener((view, focused) ->
        view.setBackground(outline(CREAM, focused ? GREEN : BORDER, 16)));
    content.addView(input, new LinearLayout.LayoutParams(-1, dp(60)));
    TextView error = text("", 13, ERROR, false);
    error.setPadding(0, dp(8), 0, 0);
    error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    error.setVisibility(View.GONE);
    content.addView(error);
    input.addTextChangedListener(new TextWatcher() {
      @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
      @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
        urlDraft = value.toString();
        error.setVisibility(View.GONE);
        input.setBackground(outline(CREAM, input.hasFocus() ? GREEN : BORDER, 16));
      }
      @Override public void afterTextChanged(Editable value) {}
    });
    content.addView(space(14));
    Button submit = button("Pair with computer", GREEN, Color.red(CREAM) > 128 ? Color.WHITE : CREAM);
    urlSubmit = submit;
    submit.setEnabled(!currentState.busy);
    submit.setAlpha(currentState.busy ? .48f : 1f);
    content.addView(submit, new LinearLayout.LayoutParams(-1, dp(56)));
    Runnable download = () -> {
      if (!ready()) return;
      String link = input.getText().toString().trim();
      try {
        PairingUrl.parse(link);
      } catch (IllegalArgumentException invalid) {
        error.setText("Enter an Airreload pairing link. Direct APK links are not supported.");
        error.setVisibility(View.VISIBLE);
        input.setBackground(outline(CREAM, ERROR, 16));
        return;
      }
      getSystemService(InputMethodManager.class).hideSoftInputFromWindow(input.getWindowToken(), 0);
      input.clearFocus();
      acceptLink(link);
    };
    submit.setOnClickListener(view -> download.run());
    input.setOnEditorActionListener((view, actionId, event) -> {
      if (actionId != EditorInfo.IME_ACTION_GO) return false;
      download.run();
      return true;
    });
    return content;
  }

  private LinearLayout sheetContent(BottomSheetDialog sheet, String title) {
    LinearLayout content = column();
    content.setPadding(dp(24), dp(12), dp(24), dp(24));
    View handle = new View(this);
    handle.setBackground(round(BORDER, 4));
    handle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(32), dp(4));
    handleParams.gravity = Gravity.CENTER_HORIZONTAL;
    handleParams.bottomMargin = dp(20);
    content.addView(handle, handleParams);
    LinearLayout header = row();
    header.setGravity(Gravity.CENTER_VERTICAL);
    TextView heading = text(title, 24, FOREST, true);
    ViewCompat.setAccessibilityHeading(heading, true);
    header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
    TextView close = text("×", 26, MUTED, false);
    close.setGravity(Gravity.CENTER);
    close.setContentDescription("Close " + title);
    close.setFocusable(true);
    close.setBackground(round(CREAM, 24));
    close.setOnClickListener(view -> sheet.dismiss());
    header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
    content.addView(header);
    sheet.setTitle(title);
    return content;
  }

  private void showSheet(BottomSheetDialog sheet, LinearLayout content) {
    if (activeSheet != null) activeSheet.dismiss();
    activeSheet = sheet;
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(false);
    scroll.addView(content);
    sheet.setContentView(scroll);
    sheet.setDismissWithAnimation(ValueAnimator.areAnimatorsEnabled());
    sheet.getBehavior().setSkipCollapsed(true);
    sheet.getBehavior().setMaxWidth(dp(640));
    sheet.setOnDismissListener(dialog -> { if (activeSheet == sheet) activeSheet = null; });
    sheet.setOnShowListener(dialog -> {
      FrameLayout surface = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
      if (surface != null) {
        Drawable background = surface.getBackground();
        if (background instanceof MaterialShapeDrawable) {
          ((MaterialShapeDrawable) background).setFillColor(ColorStateList.valueOf(SURFACE));
        }
      }
      sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
    });
    Window window = sheet.getWindow();
    if (window != null) {
      window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
      WindowCompat.setDecorFitsSystemWindows(window, false);
    }
    ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
      androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
      int keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
      view.setPadding(dp(24) + bars.left, dp(12), dp(24) + bars.right,
          dp(24) + Math.max(bars.bottom, keyboard));
      return insets;
    });
    sheet.show();
  }

  private String themeLabel(String mode) {
    return THEME_LIGHT.equals(mode) ? "Light" : THEME_DARK.equals(mode) ? "Dark" : "System";
  }

  private void showThemeSheet() {
    BottomSheetDialog sheet = new BottomSheetDialog(this);
    LinearLayout content = sheetContent(sheet, "Appearance");
    content.addView(space(16));
    String selected = State.prefs(this).getString("theme_mode", THEME_SYSTEM);
    for (String mode : new String[] {THEME_SYSTEM, THEME_LIGHT, THEME_DARK}) {
      boolean checked = mode.equals(selected);
      LinearLayout option = row();
      option.setGravity(Gravity.CENTER_VERTICAL);
      option.setPadding(dp(18), dp(12), dp(18), dp(12));
      option.setBackground(round(checked ? PALE_MINT : CREAM, 18));
      LinearLayout labels = column();
      labels.addView(text(themeLabel(mode), 16, FOREST, true));
      if (THEME_SYSTEM.equals(mode)) {
        labels.addView(space(3));
        labels.addView(text("Match your device", 13, MUTED, false));
      }
      option.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
      android.widget.RadioButton radio = new android.widget.RadioButton(this);
      radio.setChecked(checked);
      radio.setButtonTintList(ColorStateList.valueOf(checked ? GREEN : MUTED));
      radio.setClickable(false);
      radio.setFocusable(false);
      radio.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      option.addView(radio, new LinearLayout.LayoutParams(dp(40), dp(40)));
      option.setFocusable(true);
      option.setContentDescription(themeLabel(mode) + (checked ? ", selected" : ""));
      option.setOnClickListener(view -> {
        sheet.setOnDismissListener(dialog -> {
          activeSheet = null;
          if (!isFinishing() && !isDestroyed()) setThemeMode(mode);
        });
        sheet.dismiss();
      });
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
      params.bottomMargin = dp(10);
      content.addView(option, params);
    }
    showSheet(sheet, content);
  }

  private void showSettingsPage() {
    historyPage = false;
    historySelectionMode = false;
    selectedHistory.clear();
    historyList = null;
    if (historyBack != null) {
      historyBack.setEnabled(false);
    }
    bottomNavigation.setVisibility(View.VISIBLE);
    selectTab(false);
    pageHost.removeAllViews();
    ScrollView scroll = new ScrollView(this);
    currentScroll = scroll;
    scroll.setFillViewport(true);
    MaxWidthColumn root = new MaxWidthColumn(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(24), dp(24), dp(24), dp(32));
    scroll.addView(
        root,
        new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    pageHost.addView(
        scroll,
        new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    root.addView(text("Settings", 30, FOREST, true));
    root.addView(space(38));
    root.addView(text("Appearance", 14, MUTED, true));
    root.addView(space(12));
    LinearLayout appearance = column();
    appearance.setBackground(outline(SURFACE, BORDER, 15));
    String themeMode = State.prefs(this).getString("theme_mode", THEME_SYSTEM);
    appearance.addView(settingsRow("", "Theme", themeLabel(themeMode), view -> showThemeSheet()));
    root.addView(appearance);
    root.addView(space(34));
    root.addView(text("After installation", 14, MUTED, true));
    root.addView(space(12));
    LinearLayout afterInstallation = column();
    afterInstallation.setBackground(outline(SURFACE, BORDER, 15));
    afterInstallation.addView(autoOpenSettingsRow());
    root.addView(afterInstallation);
    root.addView(space(34));
    root.addView(text("Permissions", 14, MUTED, true));
    root.addView(space(12));
    LinearLayout permissions = column();
    permissions.setBackground(outline(SURFACE, BORDER, 15));
    permissions.addView(
        settingsRow("↓", "Install unknown apps", "Open Android settings", view -> openInstallSettings()));
    root.addView(permissions);
    root.addView(space(34));
    root.addView(text("App info", 14, MUTED, true));
    root.addView(space(12));
    LinearLayout info = column();
    info.setBackground(outline(SURFACE, BORDER, 15));
    info.addView(settingsRow("", "Version", "1.1.0", null));
    info.addView(divider());
    info.addView(settingsRow("", "Supported Android", "8.0+", null));
    root.addView(info);
  }

  private void showHistoryPage() {
    historyPage = true;
    historySelectionMode = false;
    selectedHistory.clear();
    library = null;
    bottomNavigation.setVisibility(View.GONE);
    selectTab(true);
    if (historyBack != null) {
      historyBack.setEnabled(true);
    }
    pageHost.removeAllViews();
    ScrollView scroll = new ScrollView(this);
    currentScroll = scroll;
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    MaxWidthColumn root = new MaxWidthColumn(this);
    historyRoot = root;
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(24), dp(18), dp(24), dp(32));
    scroll.addView(
        root,
        new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    pageHost.addView(
        scroll,
        new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    LinearLayout header = row();
    header.setGravity(Gravity.CENTER_VERTICAL);
    TextView back = text("‹", 34, FOREST, false);
    back.setGravity(Gravity.CENTER);
    back.setContentDescription("Back to home");
    back.setFocusable(true);
    back.setBackground(outline(SURFACE, BORDER, 12));
    back.setOnClickListener(view -> router.back());
    header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
    TextView title = text("Download history", 24, FOREST, true);
    LinearLayout.LayoutParams titleParams =
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    titleParams.setMarginStart(dp(14));
    header.addView(title, titleParams);
    historySelectAction = text(getString(R.string.history_select_all), 11, GREEN, true);
    historySelectAction.setLetterSpacing(.08f);
    historySelectAction.setGravity(Gravity.CENTER);
    historySelectAction.setFocusable(true);
    historySelectAction.setBackground(outline(SURFACE, BORDER, 10));
    historySelectAction.setOnClickListener(
        view -> {
          if (historySelectionMode) {
            clearHistorySelection();
          } else {
            toggleSelectAllHistory();
          }
        });
    header.addView(historySelectAction, new LinearLayout.LayoutParams(dp(96), dp(42)));
    root.addView(header);
    root.addView(space(18));

    historySummary = text("", 14, MUTED, false);
    historySummary.setLineSpacing(dp(3), 1f);
    root.addView(historySummary);
    root.addView(space(18));
    historySelectionBar = buildHistorySelectionBar();
    root.addView(historySelectionBar);
    historyList = column();
    root.addView(historyList);
    refreshHistory();
  }

  private LinearLayout buildHistorySelectionBar() {
    LinearLayout bar = row();
    bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.setPadding(dp(14), dp(8), dp(8), dp(8));
    bar.setBackground(outline(SURFACE, GREEN, 14));
    historySelectionCount = text("", 14, FOREST, true);
    historySelectionCount.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    bar.addView(
        historySelectionCount,
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    historySelectAllAction = text(getString(R.string.history_select_all), 11, GREEN, true);
    historySelectAllAction.setGravity(Gravity.CENTER);
    historySelectAllAction.setMinWidth(dp(96));
    historySelectAllAction.setMinHeight(dp(48));
    historySelectAllAction.setOnClickListener(view -> toggleSelectAllHistory());
    bar.addView(historySelectAllAction);
    TextView delete = text(getString(R.string.history_delete), 11, ERROR, true);
    delete.setGravity(Gravity.CENTER);
    delete.setMinWidth(dp(74));
    delete.setMinHeight(dp(48));
    delete.setOnClickListener(view -> confirmHistoryDeletion());
    bar.addView(delete);
    bar.setVisibility(View.GONE);
    LinearLayout.LayoutParams params =
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.bottomMargin = dp(12);
    bar.setLayoutParams(params);
    return bar;
  }

  private void clearHistorySelection() {
    selectedHistory.clear();
    refreshHistory(true);
  }

  private void toggleSelectAllHistory() {
    List<DownloadRecord> entries = DownloadHistory.all(this);
    Set<String> availableIds = new HashSet<>();
    for (DownloadRecord entry : entries) {
      availableIds.add(entry.getId());
    }
    selectedHistory.retainAll(availableIds);
    if (selectedHistory.size() == entries.size()) {
      selectedHistory.clear();
    } else {
      selectedHistory.clear();
      for (DownloadRecord entry : entries) {
        selectedHistory.add(entry.getId());
      }
    }
    refreshHistory(true);
  }

  private void toggleHistoryEntry(String id) {
    if (!selectedHistory.add(id)) {
      selectedHistory.remove(id);
    }
    refreshHistory(true);
  }

  private void refreshHistory() {
    refreshHistory(false);
  }

  private void refreshHistory(boolean animateSelection) {
    if (!historyPage || historyList == null) {
      return;
    }
    List<DownloadRecord> entries = DownloadHistory.all(this);
    Set<String> availableIds = new HashSet<>();
    long bytes = 0;
    for (DownloadRecord entry : entries) {
      availableIds.add(entry.getId());
      if (DownloadHistory.hasArtifact(this, entry)) {
        bytes += entry.getArtifactBytes();
      }
    }
    selectedHistory.retainAll(availableIds);
    boolean nextSelectionMode = !selectedHistory.isEmpty();
    boolean selectionModeChanged = historySelectionMode != nextSelectionMode;
    if (animateSelection
        && selectionModeChanged
        && historyRoot != null
        && ViewCompat.isLaidOut(historyRoot)
        && ValueAnimator.areAnimatorsEnabled()) {
      AutoTransition transition = new AutoTransition();
      transition.setDuration(220);
      transition.setInterpolator(new PathInterpolator(.2f, 0f, 0f, 1f));
      TransitionManager.beginDelayedTransition(historyRoot, transition);
    }
    historySelectionMode = nextSelectionMode;
    historySummary.setText(
        entries.isEmpty()
            ? "Validated APK downloads will appear here."
            : entries.size()
                + (entries.size() == 1 ? " download" : " downloads")
                + " · "
                + formatBytes(bytes)
                + " saved\nDelete saved APKs here without uninstalling their apps.");
    historySelectAction.setEnabled(!entries.isEmpty());
    historySelectAction.setAlpha(entries.isEmpty() ? .45f : 1f);
    historySelectAction.setText(
        historySelectionMode
            ? getString(R.string.history_done)
            : getString(R.string.history_select_all));
    historySelectionBar.setVisibility(historySelectionMode ? View.VISIBLE : View.GONE);
    if (historySelectionMode) {
      int count = selectedHistory.size();
      historySelectionCount.setText(
          getResources().getQuantityString(R.plurals.history_selected_count, count, count));
      historySelectAllAction.setText(
          count == entries.size()
              ? getString(R.string.history_clear_selection)
              : getString(R.string.history_select_all));
    }

    if (entries.isEmpty()) {
      clearHistoryCardBindings();
      if (historyEmptyState == null) {
        historyList.removeAllViews();
        historyEmptyState = buildHistoryEmptyState();
        historyList.addView(historyEmptyState);
      }
      return;
    }
    historyEmptyState = null;
    boolean rebuildCards = historyCards.size() != entries.size();
    List<Boolean> installedStates = new ArrayList<>(entries.size());
    List<Boolean> artifactStates = new ArrayList<>(entries.size());
    for (int index = 0; index < entries.size(); index++) {
      DownloadRecord entry = entries.get(index);
      boolean installed = isHistoryPackageInstalled(entry);
      boolean hasArtifact = DownloadHistory.hasArtifact(this, entry);
      installedStates.add(installed);
      artifactStates.add(hasArtifact);
      HistoryCardBinding binding = historyCards.get(entry.getId());
      if (binding == null
          || !binding.matches(entry, installed, hasArtifact)
          || historyList.getChildCount() != entries.size()
          || historyList.getChildAt(index) != binding.card) {
        rebuildCards = true;
      }
    }
    if (rebuildCards) {
      clearHistoryCardBindings();
      historyList.removeAllViews();
      for (int index = 0; index < entries.size(); index++) {
        DownloadRecord entry = entries.get(index);
        HistoryCardBinding binding =
            buildHistoryCard(entry, installedStates.get(index), artifactStates.get(index));
        historyCards.put(entry.getId(), binding);
        historyList.addView(binding.card, libraryCardParams());
      }
    }
    for (DownloadRecord entry : entries) {
      updateHistoryCardSelection(
          historyCards.get(entry.getId()),
          selectedHistory.contains(entry.getId()),
          historySelectionMode,
          animateSelection);
    }
  }

  private HistoryCardBinding buildHistoryCard(
      DownloadRecord entry, boolean installed, boolean hasArtifact) {
    LinearLayout card = row();
    card.setGravity(Gravity.CENTER_VERTICAL);
    card.setPadding(dp(15), dp(15), dp(14), dp(15));
    card.setBackground(outline(SURFACE, BORDER, 17));
    card.setMinimumHeight(dp(92));
    card.setTag(entry.getId());

    TextView selection = text("○", 22, MUTED, true);
    selection.setGravity(Gravity.CENTER);
    selection.setVisibility(View.GONE);
    selection.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    card.addView(selection, new LinearLayout.LayoutParams(dp(40), dp(48)));

    Drawable drawable = ContextCompat.getDrawable(this, android.R.drawable.sym_def_app_icon);
    if (installed) {
      try {
        ApplicationInfo info = getPackageManager().getApplicationInfo(entry.getPackageName(), 0);
        drawable = getPackageManager().getApplicationIcon(info);
      } catch (PackageManager.NameNotFoundException ignored) {
        // A package can disappear between refreshing the list and loading its icon.
      }
    }
    ImageView icon = new ImageView(this);
    icon.setImageDrawable(drawable);
    icon.setAlpha(installed ? 1f : .55f);
    icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    card.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

    LinearLayout details = column();
    LinearLayout.LayoutParams detailsParams =
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    detailsParams.setMarginStart(dp(14));
    card.addView(details, detailsParams);
    TextView name =
        text(
            entry.getLabel().isEmpty() ? entry.getPackageName() : entry.getLabel(),
            16,
            FOREST,
            true);
    name.setMaxLines(2);
    details.addView(name);
    details.addView(text(historyMetadata(entry, hasArtifact), 12, MUTED, false));
    String state = historyState(entry, installed, hasArtifact);
    details.addView(text(state, 12, installed ? GREEN : MUTED, true));

    TextView saved = text(hasArtifact ? "APK" : "LOG", 10, MUTED, true);
    saved.setGravity(Gravity.CENTER);
    saved.setBackground(round(PALE_MINT, 10));
    card.addView(saved, new LinearLayout.LayoutParams(dp(44), dp(30)));
    card.setFocusable(true);
    card.setOnClickListener(
        view -> {
          if (historySelectionMode) {
            toggleHistoryEntry(entry.getId());
          }
        });
    card.setOnLongClickListener(
        view -> {
          if (!historySelectionMode) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
          }
          toggleHistoryEntry(entry.getId());
          return true;
        });
    card.setClickable(false);
    String label = entry.getLabel().isEmpty() ? entry.getPackageName() : entry.getLabel();
    card.setContentDescription(label + ". " + state);
    return new HistoryCardBinding(
        entry, installed, hasArtifact, card, selection, saved, label, state);
  }

  private boolean isHistoryPackageInstalled(DownloadRecord entry) {
    try {
      getPackageManager().getApplicationInfo(entry.getPackageName(), 0);
      return true;
    } catch (PackageManager.NameNotFoundException ignored) {
      return false;
    }
  }

  private void updateHistoryCardSelection(
      HistoryCardBinding binding, boolean selected, boolean selectionMode, boolean animate) {
    boolean selectionChanged = binding.selected != selected;
    boolean modeChanged = binding.selectionMode != selectionMode;
    binding.selected = selected;
    binding.selectionMode = selectionMode;
    binding.card.setSelected(selected);
    binding.card.setClickable(selectionMode);
    binding.selection.setText(selected ? "✓" : "○");
    binding.selection.setTextColor(selected ? GREEN : MUTED);
    binding.selection.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
    binding.saved.setVisibility(selectionMode ? View.GONE : View.VISIBLE);
    binding.card.setContentDescription(
        binding.label
            + ". "
            + binding.state
            + (selectionMode ? "" : ". " + getString(R.string.history_long_press_to_select)));
    ViewCompat.setStateDescription(
        binding.card,
        selectionMode
            ? getString(
                selected ? R.string.history_item_selected : R.string.history_item_not_selected)
            : null);

    int startFill = selected ? SURFACE : PALE_MINT;
    int endFill = selected ? PALE_MINT : SURFACE;
    int startStroke = selected ? BORDER : GREEN;
    int endStroke = selected ? GREEN : BORDER;
    boolean shouldAnimate =
        animate
            && selectionChanged
            && ValueAnimator.areAnimatorsEnabled()
            && ViewCompat.isLaidOut(binding.card);
    if (binding.backgroundAnimator != null) {
      binding.backgroundAnimator.cancel();
      binding.backgroundAnimator = null;
    }
    if (shouldAnimate) {
      ArgbEvaluator colors = new ArgbEvaluator();
      ValueAnimator background = ValueAnimator.ofFloat(0f, 1f);
      background.setDuration(220);
      background.setInterpolator(new PathInterpolator(.2f, 0f, 0f, 1f));
      background.addUpdateListener(
          animator -> {
            float progress = (float) animator.getAnimatedValue();
            int fill = (int) colors.evaluate(progress, startFill, endFill);
            int stroke = (int) colors.evaluate(progress, startStroke, endStroke);
            binding.card.setBackground(outline(fill, stroke, 17));
          });
      binding.backgroundAnimator = background;
      background.start();

      binding.card.animate().cancel();
      binding.card.setScaleX(.985f);
      binding.card.setScaleY(.985f);
      binding
          .card
          .animate()
          .scaleX(1f)
          .scaleY(1f)
          .setDuration(180)
          .setInterpolator(new PathInterpolator(.2f, 0f, 0f, 1f))
          .start();
      if (selectionMode) {
        binding.selection.animate().cancel();
        binding.selection.setAlpha(.35f);
        binding.selection.setScaleX(.72f);
        binding.selection.setScaleY(.72f);
        binding
            .selection
            .animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(180)
            .setInterpolator(new PathInterpolator(.2f, 0f, 0f, 1f))
            .start();
      }
    } else {
      binding.card.setBackground(outline(endFill, endStroke, 17));
      if (modeChanged) {
        binding.selection.setAlpha(1f);
        binding.selection.setScaleX(1f);
        binding.selection.setScaleY(1f);
      }
    }
  }

  private void clearHistoryCardBindings() {
    for (HistoryCardBinding binding : historyCards.values()) {
      if (binding.backgroundAnimator != null) {
        binding.backgroundAnimator.cancel();
      }
      binding.card.animate().cancel();
      binding.selection.animate().cancel();
    }
    historyCards.clear();
  }

  private View buildHistoryEmptyState() {
    LinearLayout empty = column();
    empty.setGravity(Gravity.CENTER);
    empty.setPadding(dp(24), dp(42), dp(24), dp(42));
    empty.setBackground(outline(SURFACE, BORDER, 18));
    TextView glyph = text("↧", 38, GREEN, true);
    glyph.setGravity(Gravity.CENTER);
    empty.addView(glyph);
    empty.addView(space(8));
    TextView title = text("No downloads yet", 18, FOREST, true);
    title.setGravity(Gravity.CENTER);
    empty.addView(title);
    empty.addView(space(6));
    TextView body =
        text("Your next validated APK will be saved here until you delete it.", 14, MUTED, false);
    body.setGravity(Gravity.CENTER);
    empty.addView(body);
    return empty;
  }

  private String historyMetadata(DownloadRecord entry, boolean hasArtifact) {
    List<String> parts = new ArrayList<>();
    if (!entry.getVersionName().isEmpty()) {
      parts.add("v" + entry.getVersionName());
    } else if (entry.getVersionCode() > 0) {
      parts.add("build " + entry.getVersionCode());
    }
    if (entry.getDownloadedAt() > 0) {
      parts.add(
          DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
              .format(new Date(entry.getDownloadedAt())));
    } else {
      parts.add("Saved before history was added");
    }
    if (hasArtifact) {
      parts.add(formatBytes(entry.getArtifactBytes()));
    }
    if (!entry.getSourceHost().isEmpty()) {
      parts.add(entry.getSourceHost());
    }
    return String.join(" · ", parts);
  }

  private String historyState(DownloadRecord entry, boolean installed, boolean saved) {
    if (installed) {
      return saved ? "Installed · APK saved" : "Installed · history only";
    }
    if (DownloadOutcomes.INSTALLED.equals(entry.getOutcome())) {
      return saved ? "Removed from device · APK saved" : "Removed from device";
    }
    if (DownloadOutcomes.CANCELLED.equals(entry.getOutcome())) {
      return saved ? "Install cancelled · APK saved" : "Install cancelled";
    }
    if (DownloadOutcomes.FAILED.equals(entry.getOutcome())) {
      return saved ? "Install didn’t finish · APK saved" : "Install didn’t finish";
    }
    return saved ? "Downloaded · APK saved" : "Downloaded";
  }

  private void confirmHistoryDeletion() {
    if (selectedHistory.isEmpty()) {
      showMessage(
          getString(R.string.history_nothing_selected_title),
          getString(R.string.history_nothing_selected_body));
      return;
    }
    List<DownloadRecord> entries = DownloadHistory.all(this);
    long bytes = 0;
    for (DownloadRecord entry : entries) {
      if (selectedHistory.contains(entry.getId()) && DownloadHistory.hasArtifact(this, entry)) {
        bytes += entry.getArtifactBytes();
      }
    }
    int count = selectedHistory.size();
    long selectedBytes = bytes;
    new AlertDialog.Builder(this)
        .setTitle(getResources().getQuantityString(R.plurals.history_delete_title, count, count))
        .setMessage(getString(R.string.history_delete_message, formatBytes(selectedBytes)))
        .setNegativeButton(R.string.history_keep, null)
        .setPositiveButton(
            getString(R.string.history_delete),
            (dialog, which) -> {
              int removed = DownloadHistory.delete(this, new HashSet<>(selectedHistory));
              selectedHistory.clear();
              refreshHistory(true);
              showMessage(
                  getString(R.string.history_deleted_title),
                  getResources()
                      .getQuantityString(R.plurals.history_deleted_body, removed, removed));
            })
        .show();
  }

  private String formatBytes(long bytes) {
    if (bytes < 1024) {
      return bytes + " B";
    }
    if (bytes < 1024L * 1024L) {
      return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
    }
    return String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576.0);
  }

  private View settingsRow(
      String icon, String label, String value, View.OnClickListener listener) {
    LinearLayout item = row();
    item.setGravity(Gravity.CENTER_VERTICAL);
    item.setPadding(dp(20), 0, dp(20), 0);
    if (!icon.isEmpty()) {
      TextView iconView = text(icon, 20, FOREST, false);
      iconView.setGravity(Gravity.CENTER);
      item.addView(iconView, new LinearLayout.LayoutParams(dp(34), dp(64)));
    }
    TextView labelView = text(label, 16, FOREST, false);
    item.addView(
        labelView,
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    TextView valueView = text(value, 14, value.isEmpty() ? MUTED : GREEN, false);
    item.addView(valueView);
    item.setMinimumHeight(dp(64));
    item.setFocusable(listener != null);
    item.setOnClickListener(listener);
    return item;
  }

  private View autoOpenSettingsRow() {
    LinearLayout item = row();
    item.setGravity(Gravity.CENTER_VERTICAL);
    item.setPadding(dp(20), dp(12), dp(14), dp(12));
    LinearLayout labels = column();
    labels.addView(text("Open installed apps automatically", 16, FOREST, false));
    labels.addView(space(3));
    TextView description =
        text("Launch the app as soon as Android finishes installing it.", 13, MUTED, false);
    description.setLineSpacing(dp(2), 1f);
    labels.addView(description);
    LinearLayout.LayoutParams labelsParams =
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    labelsParams.setMarginEnd(dp(12));
    item.addView(labels, labelsParams);
    MaterialSwitch toggle = new MaterialSwitch(this);
    toggle.setChecked(State.prefs(this).getBoolean(State.AUTO_OPEN_AFTER_INSTALL, false));
    toggle.setClickable(false);
    toggle.setFocusable(false);
    toggle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    item.addView(toggle, new LinearLayout.LayoutParams(dp(52), dp(48)));
    item.setMinimumHeight(dp(88));
    item.setFocusable(true);
    updateAutoOpenAccessibility(item, toggle.isChecked());
    item.setOnClickListener(
        view -> {
          boolean enabled = !toggle.isChecked();
          toggle.setChecked(enabled);
          State.prefs(this).edit().putBoolean(State.AUTO_OPEN_AFTER_INSTALL, enabled).apply();
          updateAutoOpenAccessibility(item, enabled);
        });
    return item;
  }

  private void updateAutoOpenAccessibility(View item, boolean enabled) {
    item.setContentDescription(
        "Open installed apps automatically. "
            + (enabled ? "On." : "Off.")
            + " Launch the app as soon as Android finishes installing it.");
    ViewCompat.setStateDescription(item, enabled ? "On" : "Off");
  }

  private View buildLibraryHeader() {
    LinearLayout row = row();
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.addView(
        text("Apps", 16, FOREST, true),
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    libraryCount = text("0 apps", 12, MUTED, true);
    libraryCount.setPadding(dp(10), dp(6), dp(10), dp(6));
    libraryCount.setBackground(round(SURFACE, 20));
    row.addView(libraryCount);
    return row;
  }

  private View buildSearch() {
    search = new EditText(this);
    search.setSingleLine(true);
    search.setTextSize(16);
    search.setTextColor(FOREST);
    search.setHintTextColor(MUTED);
    search.setHint("Search apps");
    search.setContentDescription("Search your Airreload Go library");
    search.setInputType(InputType.TYPE_CLASS_TEXT);
    search.setMinimumHeight(dp(58));
    search.setPadding(dp(18), dp(12), dp(18), dp(12));
    search.setBackground(outline(SURFACE, BORDER, 16));
    search.setOnFocusChangeListener(
        (view, focused) -> view.setBackground(outline(SURFACE, focused ? GREEN : BORDER, 16)));
    search.setText(query);
    search.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence value, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence value, int start, int before, int count) {
            query = value.toString();
            refreshLibrary();
          }

          @Override
          public void afterTextChanged(Editable value) {}
        });
    return search;
  }

  private void showOnboarding() {
    if (isFinishing() || isDestroyed() || onboardingVisible) {
      return;
    }
    onboardingVisible = true;
    if (appContent != null) {
      appContent.setVisibility(View.INVISIBLE);
    }
    Dialog dialog = new Dialog(this);
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    dialog.setCancelable(false);
    LinearLayout page = column();
    page.setGravity(Gravity.CENTER_HORIZONTAL);
    page.setPadding(dp(24), dp(30), dp(24), dp(26));
    page.setBackgroundColor(CREAM);
    OnboardingArt art = new OnboardingArt();
    page.addView(art, new LinearLayout.LayoutParams(dp(250), dp(210)));
    page.addView(space(12));
    TextView eyebrow = text("ONE-TIME SETUP", 11, GREEN, true);
    eyebrow.setLetterSpacing(.15f);
    page.addView(eyebrow);
    page.addView(space(9));
    TextView title = text("Let Airreload Go hand apps to Android", 29, FOREST, true);
    title.setGravity(Gravity.CENTER);
    page.addView(title);
    page.addView(space(12));
    TextView explanation =
        text(
            "Android calls this “Allow from this source.” It only lets Airreload Go open Android’s normal install screen—you still approve every app.",
            15,
            MUTED,
            false);
    explanation.setGravity(Gravity.CENTER);
    explanation.setLineSpacing(dp(3), 1f);
    page.addView(explanation);
    page.addView(space(22));
    page.addView(onboardingPoint("1", "Airreload Go validates and downloads the APK"));
    page.addView(space(10));
    page.addView(onboardingPoint("2", "Android shows its mandatory Install button"));
    page.addView(space(10));
    page.addView(onboardingPoint("3", "Turn access off later whenever you like"));
    page.addView(space(26));
    Button open = button("Open Android settings", GREEN, Color.WHITE);
    open.setOnClickListener(
        view -> {
          State.prefs(this).edit().putBoolean("onboarding_seen", true).apply();
          onboardingVisible = false;
          dialog.dismiss();
          openInstallSettings();
        });
    page.addView(open, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
    page.addView(space(8));
    Button later = button("I’ll do this later", CREAM, MUTED);
    later.setOnClickListener(
        view -> {
          State.prefs(this).edit().putBoolean("onboarding_seen", true).apply();
          onboardingVisible = false;
          dialog.dismiss();
          State.update(
              this,
              "idle",
              "No problem. Airreload Go will ask again after your first scan.",
              0);
        });
    page.addView(later, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
    ScrollView onboardingScroll = new ScrollView(this);
    onboardingScroll.setFillViewport(true);
    onboardingScroll.setClipToPadding(false);
    onboardingScroll.setBackgroundColor(CREAM);
    onboardingScroll.addView(
        page,
        new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    dialog.setContentView(onboardingScroll);
    dialog.setOnDismissListener(
        ignored -> {
          onboardingVisible = false;
          if (appContent != null) {
            appContent.setVisibility(View.VISIBLE);
          }
        });
    Window window = dialog.getWindow();
    if (window != null) {
      window.setBackgroundDrawable(new ColorDrawable(CREAM));
      window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
    }
    dialog.show();
    if (window != null) {
      window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
      window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      window.setStatusBarColor(CREAM);
      window.setNavigationBarColor(Color.rgb(20, 25, 31));
      WindowCompat.setDecorFitsSystemWindows(window, false);
      WindowCompat.getInsetsController(window, window.getDecorView()).setAppearanceLightStatusBars(false);
      WindowCompat.getInsetsController(window, window.getDecorView()).setAppearanceLightNavigationBars(false);
      ViewCompat.setOnApplyWindowInsetsListener(
          onboardingScroll,
          (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
          });
    }
  }

  private View onboardingPoint(String number, String label) {
    LinearLayout row = row();
    row.setGravity(Gravity.CENTER_VERTICAL);
    TextView badge = text(number, 13, FOREST, true);
    badge.setGravity(Gravity.CENTER);
    badge.setBackground(round(MINT, 20));
    row.addView(badge, new LinearLayout.LayoutParams(dp(34), dp(34)));
    TextView copy = text(label, 14, FOREST, true);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    params.setMarginStart(dp(12));
    row.addView(copy, params);
    return row;
  }

  private boolean ready() {
    return !State.busy(this);
  }

  private void openCamera() {
    ScanOptions options =
        new ScanOptions()
            .setCaptureActivity(ScanActivity.class)
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt(getString(R.string.scanner_prompt))
            .setBeepEnabled(false)
            .setOrientationLocked(false)
            .setBarcodeImageEnabled(false);
    scanner.launch(options);
  }

  private void acceptLink(String raw) {
    if (!ready()) {
      return;
    }
    try {
      PairingUrl pairing = PairingUrl.parse(raw);
      State.prefs(this).edit().remove("queued_url").remove("queued_from_pairing").apply();
      confirmPairing(pairing);
    } catch (IllegalArgumentException exception) {
      State.update(this, "error",
          "Use an Airreload pairing QR code or pairing link. Direct APK links and other codes are not supported.", 0);
    }
    refreshStatus();
  }

  private void confirmPairing(PairingUrl pairing) {
    BottomSheetDialog sheet = new BottomSheetDialog(this);
    LinearLayout content = sheetContent(sheet, "Confirm pairing");
    content.addView(space(20));
    TextView source = text(pairing.source(), 18, FOREST, true);
    source.setTextDirection(View.TEXT_DIRECTION_LTR);
    source.setTextIsSelectable(true);
    content.addView(source);
    content.addView(space(12));
    content.addView(
        text(
            "Pair with this Airreload computer? It will build an APK for your phone, and Airreload Go will automatically download it when ready. Android will still ask you to approve installation.",
            15,
            MUTED,
            false));
    if ("http".equalsIgnoreCase(pairing.uri().getScheme())) {
      content.addView(space(12));
      content.addView(
          text(
              "Pairing uses an unencrypted local-network connection. Confirm only on a trusted development network.",
              14,
              ERROR,
              false));
    }
    content.addView(space(24));
    Button confirm = button("Pair and download", GREEN, Color.red(CREAM) > 128 ? Color.WHITE : CREAM);
    confirm.setOnClickListener(
        view -> {
          confirm.setEnabled(false);
          sheet.dismiss();
          appState.startPairing(pairing);
        });
    content.addView(confirm, new LinearLayout.LayoutParams(-1, dp(56)));
    showSheet(sheet, content);
  }

  private void openInstallSettings() {
    boolean hasQueuedLink = State.prefs(this).getString("queued_url", null) != null;
    State.update(
        this,
        hasQueuedLink ? "permission_needed" : "idle",
        "Turn on Allow from this source, then come back."
            + (hasQueuedLink ? " Your scan is saved and will resume automatically." : ""),
        0);
    try {
      sourceSettings.launch(
          new Intent(
              Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
              Uri.parse("package:" + getPackageName())));
    } catch (ActivityNotFoundException exception) {
      State.update(
          this,
          hasQueuedLink ? "permission_needed" : "idle",
          "Open Settings → Special app access → Install unknown apps → Airreload Go.",
          0);
    }
  }

  private void queueAuthorizedDownload(String link) {
    if (!ready() || isFinishing() || isDestroyed()) return;
    try {
      String url = ApkUrl.parse(link).toString();
      State.prefs(this).edit().putString("queued_url", url)
          .putBoolean("queued_from_pairing", true).apply();
      if (!getPackageManager().canRequestPackageInstalls()) {
        openInstallSettings();
      } else {
        maybeAskNotificationsAndContinue();
      }
    } catch (IllegalArgumentException exception) {
      State.update(this, "error", exception.getMessage(), 0);
    }
  }

  private void maybeAskNotificationsAndContinue() {
    if (State.prefs(this).getString("queued_url", null) == null
        || !State.prefs(this).getBoolean("queued_from_pairing", false)
        || State.busy(this)
        || !getPackageManager().canRequestPackageInstalls()) {
      return;
    }
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        && !State.prefs(this).getBoolean("notification_asked", false)) {
      State.prefs(this).edit().putBoolean("notification_asked", true).apply();
      notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
      return;
    }
    continueQueuedDownload();
  }

  private void continueQueuedDownload() {
    String url = State.prefs(this).getString("queued_url", null);
    if (url == null || !State.prefs(this).getBoolean("queued_from_pairing", false)
        || State.busy(this) || !getPackageManager().canRequestPackageInstalls()) {
      return;
    }
    State.prefs(this)
        .edit()
        .remove("queued_url")
        .remove("queued_from_pairing")
        .remove("session")
        .remove("package")
        .remove("package_baseline_update")
        .putString("last_url", url)
        .apply();
    State.update(this, "downloading", "Warming up the download…", -1);
    try {
      ContextCompat.startForegroundService(
          this, new Intent(this, InstallService.class).putExtra("url", url));
    } catch (RuntimeException exception) {
      State.update(
          this,
          "error",
          "Airreload Go couldn’t start the download. Return to the app and try again.",
          0);
    }
    refreshStatus();
  }

  private void showPendingInstall(boolean force) {
    if (!foreground
        || !"awaiting_install".equals(State.prefs(this).getString("phase", ""))) {
      return;
    }
    if (!force && State.prefs(this).getBoolean("confirmation_shown", false)) {
      return;
    }
    PendingIntent pending = Confirmation.get(this);
    if (pending == null) {
      Installer.cancel(this);
      State.update(this, "error", "The install session expired. Scan the code to try again.", 0);
      return;
    }
    State.prefs(this).edit().putBoolean("confirmation_shown", true).apply();
    try {
      pending.send();
      getSystemService(NotificationManager.class).cancel(202);
    } catch (PendingIntent.CanceledException exception) {
      Installer.cancel(this);
      State.update(this, "error", "The install session expired. Scan the code to try again.", 0);
    }
  }

  private void cancelCurrentFlow() {
    appState.cancelPairing();
    stopService(new Intent(this, InstallService.class));
    Installer.cancel(this);
    State.update(this, "idle", "Cancelled. Nothing was added to your library.", 0);
  }

  private void refreshStatus() {
    if (appState != null) {
      appState.refreshNow();
    }
  }

  private void renderState(AppUiState state) {
    if (isFinishing() || isDestroyed()) {
      return;
    }
    currentState = state;
    refreshFlowStatus();
    if (scanButton != null) {
      scanButton.setEnabled(!state.busy);
      scanButton.setAlpha(state.busy ? .48f : 1f);
    }
    if (urlSubmit != null) {
      urlSubmit.setEnabled(!state.busy);
      urlSubmit.setAlpha(state.busy ? .48f : 1f);
    }
    refreshLibrary();
    refreshHistory();
    if (foreground) {
      String pairedDownload = appState.takePendingDownload();
      // Pairing consent already authorizes this session's APK download.
      if (pairedDownload != null) queueAuthorizedDownload(pairedDownload);
      showPendingInstall(false);
      maybeHandlePendingLaunch();
    }
    String terminalKey = state.phase + "|" + state.message;
    if ("error".equals(state.phase) && !terminalKey.equals(lastTerminalState) && foreground) {
      lastTerminalState = terminalKey;
      if (flowStatus == null) showMessage(
          "Installation stopped",
          state.message.isEmpty() ? "The installation did not finish." : state.message);
    } else if ("idle".equals(state.phase) || state.busy) {
      lastTerminalState = "";
    }
  }

  private void refreshLibrary() {
    if (library == null) {
      return;
    }
    library.removeAllViews();
    PackageManager packageManager = getPackageManager();
    Set<String> packages = State.installedPackages(this);
    String activePackage =
        currentState.busy ? State.prefs(this).getString("package", "") : "";
    List<DownloadRecord> history = DownloadHistory.all(this);
    Map<String, Long> latestDownloads = new HashMap<>();
    for (DownloadRecord entry : history) {
      if (DownloadOutcomes.INSTALLED.equals(entry.getOutcome())) {
        latestDownloads.merge(entry.getPackageName(), entry.getDownloadedAt(), Math::max);
      }
    }
    List<LibraryApp> apps = new ArrayList<>();
    for (String packageName : packages) {
      if (currentState.busy && packageName.equals(activePackage)) {
        continue;
      }
      ApplicationInfo info = null;
      String label = packageName;
      boolean available = false;
      boolean enabled = false;
      Drawable icon = ContextCompat.getDrawable(this, android.R.drawable.sym_def_app_icon);
      try {
        info = packageManager.getApplicationInfo(packageName, 0);
        label = packageManager.getApplicationLabel(info).toString();
        icon = packageManager.getApplicationIcon(info);
        enabled = info.enabled;
        available = packageManager.getLaunchIntentForPackage(packageName) != null;
      } catch (PackageManager.NameNotFoundException ignored) {
        // Keep a friendly unavailable entry so removal is explicit rather than mysterious.
      }
      apps.add(
          new LibraryApp(
              packageName,
              label,
              icon,
              available,
              enabled,
              info != null,
              latestDownloads.getOrDefault(packageName, 0L)));
    }
    apps.sort(
        Comparator.comparingLong((LibraryApp app) -> app.downloadedAt)
            .reversed()
            .thenComparing(app -> app.label.toLowerCase(Locale.ROOT)));

    int visible = 0;
    String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
    if (currentState.busy && !"pairing".equals(currentState.phase)
        && !"downloading".equals(currentState.phase)) {
      visible++;
      library.addView(buildActiveAppCard(history), libraryCardParams());
    }
    for (LibraryApp app : apps) {
      if (!normalizedQuery.isEmpty()
          && !app.label.toLowerCase(Locale.ROOT).contains(normalizedQuery)
          && !app.packageName.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
        continue;
      }
      visible++;
      library.addView(buildLibraryCard(app), libraryCardParams());
    }
    libraryCount.setText(
        getResources().getQuantityString(
            R.plurals.library_app_count, packages.size(), packages.size()));
    search.setVisibility(packages.isEmpty() ? View.GONE : View.VISIBLE);
    if (visible == 0) {
      library.addView(buildEmptyState(packages.isEmpty(), !normalizedQuery.isEmpty()));
    }
  }

  private View buildActiveAppCard(List<DownloadRecord> history) {
    String packageName = State.prefs(this).getString("package", "");
    String label = "New app";
    for (DownloadRecord entry : history) {
      if (!packageName.isEmpty() && packageName.equals(entry.getPackageName())) {
        label = entry.getLabel().isEmpty() ? packageName : entry.getLabel();
        break;
      }
    }
    String status;
    if ("pairing".equals(currentState.phase)) {
      label = "Airreload pairing";
      status = currentState.message.isEmpty() ? "Waiting for your computer…" : currentState.message;
    } else if ("downloading".equals(currentState.phase)) {
      status = currentState.progress >= 0 ? "Downloading · " + currentState.progress + "%" : "Downloading";
    } else if ("awaiting_install".equals(currentState.phase)) {
      status = "Waiting for Android confirmation";
    } else {
      status = "Installing";
    }

    LinearLayout card = column();
    card.setPadding(dp(15), dp(15), dp(15), dp(14));
    card.setBackground(outline(PALE_MINT, GREEN, 17));
    LinearLayout top = row();
    top.setGravity(Gravity.CENTER_VERTICAL);
    TextView glyph = text("↓", 24, GREEN, true);
    glyph.setGravity(Gravity.CENTER);
    glyph.setBackground(round(SURFACE, 12));
    top.addView(glyph, new LinearLayout.LayoutParams(dp(48), dp(48)));
    LinearLayout details = column();
    LinearLayout.LayoutParams detailsParams =
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    detailsParams.setMarginStart(dp(14));
    top.addView(details, detailsParams);
    details.addView(text(label, 16, FOREST, true));
    details.addView(text(status, 13, GREEN, true));
    TextView cancel = text("×", 28, MUTED, false);
    cancel.setGravity(Gravity.CENTER);
    cancel.setContentDescription("pairing".equals(currentState.phase) ? "Cancel pairing" : "Cancel installation");
    cancel.setOnClickListener(view -> cancelCurrentFlow());
    cancel.setVisibility("installing".equals(currentState.phase) ? View.GONE : View.VISIBLE);
    top.addView(cancel, new LinearLayout.LayoutParams(dp(48), dp(48)));
    card.addView(top);
    ProgressBar progressBar =
        new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));
    progressBar.setProgressBackgroundTintList(
        android.content.res.ColorStateList.valueOf(SURFACE));
    progressBar.setIndeterminate(
        currentState.progress < 0 || !"downloading".equals(currentState.phase));
    progressBar.setProgress(Math.max(0, currentState.progress));
    LinearLayout.LayoutParams progressParams =
        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(5));
    progressParams.topMargin = dp(12);
    card.addView(progressBar, progressParams);
    if ("awaiting_install".equals(currentState.phase)) {
      card.setOnClickListener(view -> showPendingInstall(true));
      card.setContentDescription(label + ". Waiting for Android confirmation. Tap to continue.");
    } else {
      card.setContentDescription(label + ". " + status);
    }
    return card;
  }

  private View buildLibraryCard(LibraryApp app) {
    LinearLayout card = row();
    card.setGravity(Gravity.CENTER_VERTICAL);
    card.setPadding(dp(15), dp(15), dp(14), dp(15));
    card.setBackground(outline(SURFACE, BORDER, 17));
    card.setMinimumHeight(dp(78));
    ImageView icon = new ImageView(this);
    icon.setImageDrawable(app.icon);
    icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    icon.setAlpha(app.installed && app.enabled ? 1f : .45f);
    card.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

    LinearLayout details = column();
    LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    detailsParams.setMarginStart(dp(14));
    card.addView(details, detailsParams);
    TextView name = text(app.label, 16, FOREST, true);
    name.setMaxLines(2);
    details.addView(name);
    String meta;
    int metaColor;
    if (!app.installed) {
      meta = "Removed from this device";
      metaColor = ERROR;
    } else if (!app.enabled) {
      meta = "Disabled in Android settings";
      metaColor = ERROR;
    } else if (!app.launchable) {
      meta = "Installed · no launcher screen";
      metaColor = MUTED;
    } else {
      meta = "Ready to open";
      metaColor = GREEN;
    }
    TextView subtitle = text(meta, 12, metaColor, false);
    details.addView(subtitle);
    TextView arrow = text(app.launchable && app.enabled ? "↗" : "···", 22, app.launchable ? GREEN : MUTED, true);
    arrow.setGravity(Gravity.CENTER);
    card.addView(arrow, new LinearLayout.LayoutParams(dp(44), dp(48)));
    card.setContentDescription(
        (app.launchable && app.enabled ? "Open " : "Unavailable: ") + app.label + ". " + meta);
    card.setFocusable(true);
    card.setOnClickListener(view -> launchLibraryApp(app));
    return card;
  }

  private LinearLayout.LayoutParams libraryCardParams() {
    LinearLayout.LayoutParams params =
        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.bottomMargin = dp(10);
    return params;
  }

  private View buildEmptyState(boolean libraryEmpty, boolean hasQuery) {
    LinearLayout empty = column();
    empty.setGravity(Gravity.CENTER);
    empty.setPadding(dp(24), dp(34), dp(24), dp(34));
    empty.setBackground(outline(SURFACE, BORDER, 18));
    TextView glyph = text(libraryEmpty ? "⌁" : "○", 38, GREEN, true);
    glyph.setGravity(Gravity.CENTER);
    empty.addView(glyph);
    empty.addView(space(8));
    TextView title =
        text(
            libraryEmpty
                ? "No apps installed"
                : hasQuery ? "No matches here" : "Nothing to show",
            18,
            FOREST,
            true);
    title.setGravity(Gravity.CENTER);
    empty.addView(title);
    empty.addView(space(6));
    TextView body =
        text(
            libraryEmpty
                ? "Installed apps will appear here."
                : "Try another app name or package name.",
            14,
            MUTED,
            false);
    body.setGravity(Gravity.CENTER);
    empty.addView(body);
    return empty;
  }

  private void launchLibraryApp(LibraryApp app) {
    Intent intent = getPackageManager().getLaunchIntentForPackage(app.packageName);
    if (intent == null || !app.enabled) {
      showMessage(
          "This app can’t open right now",
          app.installed
              ? "It may be disabled or may not include a launcher screen. Airreload Go will keep it in your library."
              : "It was removed from this device. Install it again with Airreload Go to bring it back.");
      refreshLibrary();
      return;
    }
    try {
      startActivity(intent);
    } catch (RuntimeException exception) {
      showMessage(
          "This app didn’t open",
          "Android could not launch it. It may have been removed or disabled.");
      refreshLibrary();
    }
  }

  private void maybeHandlePendingLaunch() {
    if (!foreground || isFinishing() || isDestroyed()) {
      return;
    }
    String packageName = State.prefs(this).getString(State.PENDING_LAUNCH, "");
    if (packageName.isEmpty() || packageName.equals(promptedLaunchPackage)) {
      return;
    }
    Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
    PostInstallLaunch.Action action =
        PostInstallLaunch.decide(
            packageName,
            State.prefs(this).getBoolean(State.AUTO_OPEN_AFTER_INSTALL, false),
            launchIntent != null);
    if (action == PostInstallLaunch.Action.OPEN) {
      clearPendingLaunch(packageName);
      openInstalledPackage(packageName, launchIntent, true);
    } else if (action == PostInstallLaunch.Action.ASK) {
      showInstallSuccessSheet(packageName, launchIntent);
    } else if (action == PostInstallLaunch.Action.UNAVAILABLE) {
      clearPendingLaunch(packageName);
      showMessage(
          "Installed successfully",
          "This app doesn’t include a launcher screen, but it has been added to your library.");
    }
  }

  private void showInstallSuccessSheet(String packageName, Intent launchIntent) {
    promptedLaunchPackage = packageName;
    String label = packageName;
    Drawable appIcon = ContextCompat.getDrawable(this, android.R.drawable.sym_def_app_icon);
    try {
      ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
      label = getPackageManager().getApplicationLabel(info).toString();
      appIcon = getPackageManager().getApplicationIcon(info);
    } catch (PackageManager.NameNotFoundException ignored) {
      // The package was installed, so use safe fallbacks if Android has not indexed it yet.
    }

    BottomSheetDialog sheet = new BottomSheetDialog(this);
    LinearLayout content = sheetContent(sheet, "Installed successfully");
    content.addView(space(12));

    FrameLayout hero = new FrameLayout(this);
    hero.setBackground(round(PALE_MINT, 24));
    hero.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    ImageView icon = new ImageView(this);
    icon.setImageDrawable(appIcon);
    FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(72), dp(72), Gravity.CENTER);
    hero.addView(icon, iconParams);
    TextView check = text("✓", 16, Color.WHITE, true);
    check.setGravity(Gravity.CENTER);
    check.setBackground(round(GREEN, 20));
    FrameLayout.LayoutParams checkParams =
        new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER);
    checkParams.leftMargin = dp(58);
    checkParams.topMargin = dp(58);
    hero.addView(check, checkParams);
    LinearLayout.LayoutParams heroParams =
        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(156));
    content.addView(hero, heroParams);
    content.addView(space(22));

    TextView eyebrow = text("READY TO GO", 11, GREEN, true);
    eyebrow.setLetterSpacing(.14f);
    eyebrow.setGravity(Gravity.CENTER);
    content.addView(eyebrow);
    content.addView(space(8));
    TextView title = text(label + " is ready", 27, FOREST, true);
    title.setGravity(Gravity.CENTER);
    title.setMaxLines(2);
    ViewCompat.setAccessibilityHeading(title, true);
    content.addView(title);
    content.addView(space(9));
    TextView message =
        text("The installation finished successfully. You can jump straight into the app now.",
            15, MUTED, false);
    message.setGravity(Gravity.CENTER);
    message.setLineSpacing(dp(3), 1f);
    content.addView(message);
    content.addView(space(22));

    CheckBox remember = new CheckBox(this);
    remember.setText("Always open apps after installation");
    remember.setTextSize(15);
    remember.setTextColor(FOREST);
    remember.setButtonTintList(
        new ColorStateList(
            new int[][] {
              new int[] {android.R.attr.state_checked},
              new int[] {}
            },
            new int[] {GREEN, MUTED}));
    remember.setPadding(dp(14), dp(8), dp(14), dp(8));
    remember.setBackground(outline(CREAM, BORDER, 16));
    remember.setChecked(State.prefs(this).getBoolean(State.AUTO_OPEN_AFTER_INSTALL, false));
    remember.setOnCheckedChangeListener(
        (buttonView, checked) ->
            State.prefs(this).edit().putBoolean(State.AUTO_OPEN_AFTER_INSTALL, checked).apply());
    content.addView(remember, new LinearLayout.LayoutParams(-1, dp(60)));
    TextView hint = text("You can change this anytime in Settings.", 12, MUTED, false);
    hint.setPadding(dp(14), dp(6), dp(14), 0);
    content.addView(hint);
    content.addView(space(20));

    Button open = button("Open app", GREEN, Color.red(CREAM) > 128 ? Color.WHITE : CREAM);
    content.addView(open, new LinearLayout.LayoutParams(-1, dp(56)));
    content.addView(space(10));
    Button later = button("Not now", CREAM, FOREST);
    later.setBackground(outline(CREAM, BORDER, 16));
    content.addView(later, new LinearLayout.LayoutParams(-1, dp(52)));

    String appLabel = label;
    open.setContentDescription("Open " + appLabel);
    open.setOnClickListener(
        view -> {
          clearPendingLaunch(packageName);
          sheet.setOnDismissListener(
              dialog -> {
                activeSheet = null;
                promptedLaunchPackage = null;
                if (!isFinishing() && !isDestroyed()) {
                  openInstalledPackage(packageName, launchIntent, false);
                }
              });
          sheet.dismiss();
        });
    later.setOnClickListener(view -> sheet.dismiss());
    showSheet(sheet, content);
    sheet.setOnDismissListener(
        dialog -> {
          if (activeSheet == sheet) activeSheet = null;
          clearPendingLaunch(packageName);
          promptedLaunchPackage = null;
        });
  }

  private void openInstalledPackage(
      String packageName, Intent launchIntent, boolean automatic) {
    if (launchIntent == null) {
      showMessage(
          "Installed, but couldn’t open",
          "This app doesn’t include a launcher screen. It is still saved in your library.");
      return;
    }
    try {
      startActivity(launchIntent);
    } catch (RuntimeException exception) {
      showMessage(
          "Installed, but couldn’t open",
          automatic
              ? "Android couldn’t launch it automatically. Tap its card in your library to try again."
              : "Android couldn’t launch it. Tap its card in your library to try again.");
    }
  }

  private void clearPendingLaunch(String packageName) {
    if (packageName.equals(State.prefs(this).getString(State.PENDING_LAUNCH, ""))) {
      State.prefs(this).edit().remove(State.PENDING_LAUNCH).apply();
    }
  }

  private void showMessage(String title, String message) {
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("Got it", null)
        .show();
  }

  private Button button(String label, int background, int foregroundColor) {
    Button button = new Button(this);
    button.setText(label);
    button.setTextSize(15);
    button.setTextColor(foregroundColor);
    button.setAllCaps(false);
    button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    button.setBackground(round(background, 16));
    button.setMinHeight(0);
    button.setMinimumHeight(0);
    button.setGravity(Gravity.CENTER);
    button.setPadding(dp(16), 0, dp(16), 0);
    return button;
  }

  private TextView text(String value, int size, int color, boolean bold) {
    TextView text = new TextView(this);
    text.setText(value);
    text.setTextSize(size);
    text.setTextColor(color);
    text.setFontFeatureSettings("kern");
    if (bold) {
      text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }
    return text;
  }

  private LinearLayout row() {
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.HORIZONTAL);
    return layout;
  }

  private LinearLayout column() {
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    return layout;
  }

  private View space(int height) {
    View view = new View(this);
    view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
    return view;
  }

  private GradientDrawable round(int color, int radius) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setColor(color);
    drawable.setCornerRadius(dp(radius));
    return drawable;
  }

  private GradientDrawable outline(int color, int stroke, int radius) {
    GradientDrawable drawable = round(color, radius);
    drawable.setStroke(dp(1), stroke);
    return drawable;
  }

  private int dp(float value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private final class MaxWidthColumn extends LinearLayout {
    MaxWidthColumn(Context context) {
      super(context);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
      int available = MeasureSpec.getSize(widthMeasureSpec);
      int maximum = dp(760);
      int width = Math.min(available, maximum);
      super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
    }
  }

  private final class ScanArt extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    ScanArt() {
      super(MainActivity.this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      float scale = Math.min(getWidth() / 230f, getHeight() / 150f);
      float offsetX = (getWidth() / scale - 230f) / 2f;
      canvas.save();
      canvas.scale(scale, scale);
      canvas.translate(offsetX, 0);
      paint.setColor(MINT);
      canvas.drawCircle(115, 76, 70, paint);
      paint.setColor(FOREST);
      canvas.drawRoundRect(82, 10, 148, 138, 15, 15, paint);
      paint.setColor(Color.rgb(249, 252, 248));
      canvas.drawRoundRect(88, 18, 142, 127, 10, 10, paint);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(4);
      paint.setStrokeCap(Paint.Cap.ROUND);
      paint.setColor(GREEN);
      float[][] corners = {
        {96, 52, 96, 37, 111, 37},
        {119, 37, 134, 37, 134, 52},
        {134, 84, 134, 99, 119, 99},
        {111, 99, 96, 99, 96, 84}
      };
      for (float[] corner : corners) {
        path.reset();
        path.moveTo(corner[0], corner[1]);
        path.lineTo(corner[2], corner[3]);
        path.lineTo(corner[4], corner[5]);
        canvas.drawPath(path, paint);
      }
      paint.setStyle(Paint.Style.FILL);
      canvas.drawRect(105, 48, 115, 58, paint);
      canvas.drawRect(122, 48, 130, 56, paint);
      canvas.drawRect(105, 66, 113, 74, paint);
      canvas.drawRect(120, 70, 130, 80, paint);
      canvas.drawRect(108, 84, 116, 92, paint);
      paint.setColor(LIME);
      canvas.drawCircle(160, 35, 13, paint);
      paint.setColor(FOREST);
      paint.setStrokeWidth(3);
      paint.setStyle(Paint.Style.STROKE);
      canvas.drawLine(160, 29, 160, 40, paint);
      canvas.drawLine(155, 36, 160, 41, paint);
      canvas.drawLine(165, 36, 160, 41, paint);
      paint.setStyle(Paint.Style.FILL);
      canvas.drawRoundRect(48, 82, 78, 112, 8, 8, paint);
      paint.setColor(LIME);
      canvas.drawRect(56, 90, 63, 97, paint);
      canvas.drawRect(67, 90, 72, 95, paint);
      canvas.drawRect(56, 101, 62, 107, paint);
      canvas.drawRect(66, 100, 72, 107, paint);
      canvas.restore();
    }
  }

  private final class OnboardingArt extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    OnboardingArt() {
      super(MainActivity.this);
      setContentDescription("Airreload Go keeps Android in control of every installation");
    }

    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      float scale = Math.min(getWidth() / 250f, getHeight() / 210f);
      canvas.save();
      canvas.scale(scale, scale);
      paint.setColor(PALE_MINT);
      canvas.drawCircle(125, 105, 96, paint);
      paint.setColor(FOREST);
      canvas.drawRoundRect(78, 22, 172, 188, 22, 22, paint);
      paint.setColor(Color.rgb(249, 252, 248));
      canvas.drawRoundRect(86, 32, 164, 173, 15, 15, paint);
      paint.setColor(MINT);
      canvas.drawCircle(125, 93, 30, paint);
      paint.setColor(GREEN);
      canvas.drawRoundRect(108, 91, 142, 122, 8, 8, paint);
      canvas.drawRoundRect(114, 72, 136, 105, 14, 14, paint);
      paint.setColor(LIME);
      canvas.drawCircle(174, 51, 18, paint);
      paint.setColor(FOREST);
      paint.setStrokeWidth(4);
      paint.setStyle(Paint.Style.STROKE);
      canvas.drawLine(174, 43, 174, 59, paint);
      canvas.drawLine(168, 53, 174, 60, paint);
      canvas.drawLine(180, 53, 174, 60, paint);
      canvas.restore();
    }
  }

  private static final class HistoryCardBinding {
    final DownloadRecord entry;
    final boolean installed;
    final boolean hasArtifact;
    final LinearLayout card;
    final TextView selection;
    final TextView saved;
    final String label;
    final String state;
    boolean selected;
    boolean selectionMode;
    ValueAnimator backgroundAnimator;

    HistoryCardBinding(
        DownloadRecord entry,
        boolean installed,
        boolean hasArtifact,
        LinearLayout card,
        TextView selection,
        TextView saved,
        String label,
        String state) {
      this.entry = entry;
      this.installed = installed;
      this.hasArtifact = hasArtifact;
      this.card = card;
      this.selection = selection;
      this.saved = saved;
      this.label = label;
      this.state = state;
    }

    boolean matches(
        DownloadRecord candidate, boolean candidateInstalled, boolean candidateHasArtifact) {
      return entry.equals(candidate)
          && installed == candidateInstalled
          && hasArtifact == candidateHasArtifact;
    }
  }

  private static final class LibraryApp {
    final String packageName;
    final String label;
    final Drawable icon;
    final boolean launchable;
    final boolean enabled;
    final boolean installed;
    final long downloadedAt;

    LibraryApp(
        String packageName,
        String label,
        Drawable icon,
        boolean launchable,
        boolean enabled,
        boolean installed,
        long downloadedAt) {
      this.packageName = packageName;
      this.label = label;
      this.icon = icon;
      this.launchable = launchable;
      this.enabled = enabled;
      this.installed = installed;
      this.downloadedAt = downloadedAt;
    }
  }

}
