package com.airreload.airreload;

import java.util.ArrayList;
import java.util.List;

/** Owns destinations and back behavior independently of the rendered views. */
public final class AppRouter {
  public enum Route { HOME, SETTINGS, HISTORY }
  public interface Listener {
    void onRouteChanged(Route previous, Route current, boolean back);
  }

  private final List<Route> stack = new ArrayList<>();
  private Listener listener;

  public AppRouter() { stack.add(Route.HOME); }

  public Route current() { return stack.get(stack.size() - 1); }
  public boolean canGoBack() { return stack.size() > 1; }
  public void setListener(Listener listener) { this.listener = listener; }

  public void navigate(Route route) {
    Route previous = current();
    if (previous == route) return;
    if (route != Route.HISTORY) {
      stack.clear();
      stack.add(Route.HOME);
    }
    if (route != Route.HOME) stack.add(route);
    notifyChange(previous, route == Route.HOME);
  }

  public boolean back() {
    if (!canGoBack()) return false;
    Route previous = current();
    stack.remove(stack.size() - 1);
    notifyChange(previous, true);
    return true;
  }

  public String save() {
    StringBuilder saved = new StringBuilder();
    for (Route route : stack) {
      if (saved.length() > 0) saved.append(',');
      saved.append(route.name());
    }
    return saved.toString();
  }

  public void restore(String saved) {
    stack.clear();
    stack.add(Route.HOME);
    if (saved == null) return;
    // Only accept stacks the router itself can produce.
    if ("HOME,SETTINGS".equals(saved) || "HOME,SETTINGS,HISTORY".equals(saved)) {
      stack.add(Route.SETTINGS);
    }
    if ("HOME,HISTORY".equals(saved) || "HOME,SETTINGS,HISTORY".equals(saved)) {
      stack.add(Route.HISTORY);
    }
  }

  private void notifyChange(Route previous, boolean back) {
    if (listener != null) listener.onRouteChanged(previous, current(), back);
  }
}
