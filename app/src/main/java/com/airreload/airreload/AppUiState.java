package com.airreload.airreload;

final class AppUiState {
  final String phase;
  final String message;
  final int progress;
  final boolean busy;

  AppUiState(String phase, String message, int progress) {
    this.phase = phase;
    this.message = message;
    this.progress = progress;
    this.busy = isBusy(phase);
  }

  static boolean isBusy(String phase) {
    return "pairing".equals(phase)
            || "downloading".equals(phase)
            || "installing".equals(phase)
            || "awaiting_install".equals(phase);
  }
}
