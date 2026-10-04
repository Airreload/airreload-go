package com.airreload.airreload;

final class AppUiState {
  final String phase;
  final String message;
  final int progress;
  final boolean busy;
  final String uninstallPackage;

  AppUiState(String phase, String message, int progress) {
    this(phase, message, progress, "");
  }

  AppUiState(String phase, String message, int progress, String uninstallPackage) {
    this.phase = phase;
    this.message = message;
    this.progress = progress;
    this.busy = isBusy(phase);
    this.uninstallPackage = "error".equals(phase) ? uninstallPackage : "";
  }

  static boolean isBusy(String phase) {
    return "pairing".equals(phase)
            || "uninstalling".equals(phase)
            || "ready_to_reinstall".equals(phase)
            || "downloading".equals(phase)
            || "installing".equals(phase)
            || "awaiting_install".equals(phase);
  }
}
