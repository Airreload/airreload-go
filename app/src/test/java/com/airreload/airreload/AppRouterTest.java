package com.airreload.airreload;

import static org.junit.Assert.*;
import org.junit.Test;

public final class AppRouterTest {
  @Test public void historyReturnsToItsOrigin() {
    AppRouter router = new AppRouter();
    router.navigate(AppRouter.Route.SETTINGS);
    router.navigate(AppRouter.Route.HISTORY);
    assertTrue(router.back());
    assertEquals(AppRouter.Route.SETTINGS, router.current());
    assertTrue(router.back());
    assertEquals(AppRouter.Route.HOME, router.current());
    assertFalse(router.back());
  }

  @Test public void tabReselectionDoesNotDuplicateTheBackStack() {
    AppRouter router = new AppRouter();
    router.navigate(AppRouter.Route.SETTINGS);
    router.navigate(AppRouter.Route.SETTINGS);
    router.navigate(AppRouter.Route.HOME);
    assertFalse(router.canGoBack());
    router.navigate(AppRouter.Route.HISTORY);
    assertTrue(router.back());
    assertFalse(router.back());
  }

  @Test public void restoresHistoryAndItsParentAfterRecreation() {
    AppRouter original = new AppRouter();
    original.navigate(AppRouter.Route.SETTINGS);
    original.navigate(AppRouter.Route.HISTORY);
    AppRouter restored = new AppRouter();
    restored.restore(original.save());
    assertEquals(AppRouter.Route.HISTORY, restored.current());
    restored.back();
    assertEquals(AppRouter.Route.SETTINGS, restored.current());
    restored.restore("invalid");
    assertEquals(AppRouter.Route.HOME, restored.current());
  }

  @Test public void reportsDirectionAndIgnoresSameDestination() {
    AppRouter router = new AppRouter();
    java.util.List<String> changes = new java.util.ArrayList<>();
    router.setListener((from, to, back) -> changes.add(from + ":" + to + ":" + back));
    router.navigate(AppRouter.Route.HISTORY);
    router.navigate(AppRouter.Route.HISTORY);
    router.back();
    assertEquals(java.util.Arrays.asList("HOME:HISTORY:false", "HISTORY:HOME:true"), changes);
  }
}
