package com.airreload.airreload;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** Performs cheap file checks before PackageInstaller performs authoritative platform validation. */
final class ApkValidator {
  private ApkValidator() {}

  static PackageInfo inspect(Context context, File apk) throws IOException {
    if (!apk.isFile() || apk.length() < 4) {
      throw invalidApk();
    }
    try (FileInputStream input = new FileInputStream(apk)) {
      byte[] magic = new byte[4];
      if (input.read(magic) != 4
          || magic[0] != 'P'
          || magic[1] != 'K'
          || magic[2] != 3
          || magic[3] != 4) {
        throw invalidApk();
      }
    }

    PackageInfo info =
        context
            .getPackageManager()
            .getPackageArchiveInfo(
                apk.getPath(),
                Build.VERSION.SDK_INT >= 28
                    ? PackageManager.GET_SIGNING_CERTIFICATES
                    : PackageManager.GET_SIGNATURES);
    boolean signed =
        info != null
            && (Build.VERSION.SDK_INT >= 28
                ? info.signingInfo != null
                    && info.signingInfo.getApkContentsSigners() != null
                    && info.signingInfo.getApkContentsSigners().length > 0
                : info.signatures != null && info.signatures.length > 0);
    if (!signed || info.packageName == null || info.packageName.isEmpty()) {
      throw invalidApk();
    }
    if (info.splitNames != null && info.splitNames.length > 0) {
      throw new IOException(
          "This file belongs to a split APK set. Airreload Go supports one standalone APK at a time.");
    }
    return info;
  }

  private static IOException invalidApk() {
    return new IOException(
        "That link did not return a valid signed APK. It may be a webpage, app bundle, or incomplete file.");
  }
}
