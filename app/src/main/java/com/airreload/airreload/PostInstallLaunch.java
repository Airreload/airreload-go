package com.airreload.airreload;

/** Chooses the post-install experience without depending on Android lifecycle state. */
final class PostInstallLaunch {
  enum Action {
    NONE,
    ASK,
    OPEN,
    UNAVAILABLE
  }

  private PostInstallLaunch() {}

  static Action decide(String packageName, boolean alwaysOpen, boolean launchable) {
    if (packageName == null || packageName.trim().isEmpty()) {
      return Action.NONE;
    }
    if (!launchable) {
      return Action.UNAVAILABLE;
    }
    return alwaysOpen ? Action.OPEN : Action.ASK;
  }
}
