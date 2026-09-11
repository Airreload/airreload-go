package com.airreload.airreload;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import com.airreload.shared.history.DownloadOutcomes;
import com.airreload.shared.history.DownloadRecord;
import com.airreload.shared.history.DownloadRecordCodec;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class DownloadHistory {
  private static final String ENTRIES = "download_history_v1";
  private static final String CURRENT = "download_history_current";
  private static final String LEGACY_IMPORTED = "download_history_legacy_imported";
  private static final String DIRECTORY = "download-history";

  private DownloadHistory() {}

  @SuppressLint("ApplySharedPref")
  static void archiveDownloaded(Context context, PackageInfo info, File apk, String sourceUrl)
      throws IOException {
    String id = UUID.randomUUID().toString();
    String artifactName = id + ".apk";
    File directory = directory(context);
    if ((!directory.isDirectory() && !directory.mkdirs()) || !directory.isDirectory()) {
      throw new IOException("Airreload Go couldn’t create its download archive.");
    }
    String label = archiveLabel(context, info, apk);
    long versionCode =
        Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    File artifact = new File(directory, artifactName);
    move(apk, artifact);
    try {
      DownloadRecord entry =
          new DownloadRecord(
              id,
              System.currentTimeMillis(),
              info.packageName,
              label,
              info.versionName == null ? "" : info.versionName,
              versionCode,
              sourceHost(sourceUrl),
              DownloadOutcomes.DOWNLOADED,
              artifactName,
              artifact.length());
      List<DownloadRecord> entries = readStored(context);
      entries.add(entry);
      write(context, entries);
      State.prefs(context).edit().putString(CURRENT, entry.getId()).commit();
      notifyChanged(context);
    } catch (RuntimeException exception) {
      artifact.delete();
      throw new IOException("Airreload Go couldn’t save this download in history.", exception);
    }
  }

  static void markInstalled(Context context, String packageName) {
    updateCurrent(context, packageName, DownloadOutcomes.INSTALLED);
  }

  static void markCancelled(Context context) {
    updateCurrent(context, null, DownloadOutcomes.CANCELLED);
  }

  static void markFailed(Context context, String packageName) {
    updateCurrent(context, packageName, DownloadOutcomes.FAILED);
  }

  static List<DownloadRecord> all(Context context) {
    importLegacyLibrary(context);
    return readStored(context);
  }

  static long totalBytes(Context context) {
    long total = 0;
    for (DownloadRecord entry : all(context)) {
      File artifact = artifact(context, entry);
      if (artifact != null && artifact.isFile()) {
        total += artifact.length();
      }
    }
    return total;
  }

  static int delete(Context context, Set<String> ids) {
    if (ids.isEmpty()) {
      return 0;
    }
    List<DownloadRecord> entries = readStored(context);
    List<DownloadRecord> remaining = new ArrayList<>();
    int deleted = 0;
    for (DownloadRecord entry : entries) {
      if (!ids.contains(entry.getId())) {
        remaining.add(entry);
        continue;
      }
      File artifact = artifact(context, entry);
      if (artifact == null || !artifact.exists() || artifact.delete()) {
        deleted++;
      } else {
        remaining.add(entry);
      }
    }
    write(context, remaining);
    String current = State.prefs(context).getString(CURRENT, "");
    if (ids.contains(current)) {
      State.prefs(context).edit().remove(CURRENT).apply();
    }
    notifyChanged(context);
    return deleted;
  }

  static boolean hasArtifact(Context context, DownloadRecord entry) {
    File artifact = artifact(context, entry);
    return artifact != null && artifact.isFile();
  }

  private static void updateCurrent(Context context, String packageName, String outcome) {
    SharedPreferences prefs = State.prefs(context);
    String currentId = prefs.getString(CURRENT, "");
    List<DownloadRecord> entries = readStored(context);
    boolean changed = false;
    for (int index = 0; index < entries.size(); index++) {
      DownloadRecord entry = entries.get(index);
      boolean currentMatches = !currentId.isEmpty() && currentId.equals(entry.getId());
      boolean packageMatches =
          currentId.isEmpty()
              && packageName != null
              && packageName.equals(entry.getPackageName())
              && DownloadOutcomes.DOWNLOADED.equals(entry.getOutcome());
      if (currentMatches || packageMatches) {
        entries.set(index, entry.withOutcome(outcome));
        changed = true;
        break;
      }
    }
    prefs.edit().remove(CURRENT).apply();
    if (changed) {
      write(context, entries);
      notifyChanged(context);
    }
  }

  private static void importLegacyLibrary(Context context) {
    SharedPreferences prefs = State.prefs(context);
    if (prefs.getBoolean(LEGACY_IMPORTED, false)) {
      return;
    }
    List<DownloadRecord> entries = readStored(context);
    Set<String> represented = new HashSet<>();
    for (DownloadRecord entry : entries) {
      represented.add(entry.getPackageName());
    }
    PackageManager manager = context.getPackageManager();
    for (String packageName : State.installedPackages(context)) {
      if (represented.contains(packageName)) {
        continue;
      }
      String label = packageName;
      String versionName = "";
      long versionCode = 0;
      try {
        PackageInfo info = manager.getPackageInfo(packageName, 0);
        label = manager.getApplicationLabel(info.applicationInfo).toString();
        versionName = info.versionName;
        versionCode = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
      } catch (PackageManager.NameNotFoundException ignored) {
        // Removed legacy apps still belong in history under their package name.
      }
      entries.add(
          new DownloadRecord(
              "legacy-" + packageName,
              0,
              packageName,
              label,
              versionName == null ? "" : versionName,
              versionCode,
              "",
              DownloadOutcomes.INSTALLED,
              "",
              0));
    }
    write(context, entries);
    prefs.edit().putBoolean(LEGACY_IMPORTED, true).apply();
  }

  private static List<DownloadRecord> readStored(Context context) {
    Set<String> values = State.prefs(context).getStringSet(ENTRIES, Collections.emptySet());
    List<DownloadRecord> entries = new ArrayList<>();
    for (String value : values) {
      DownloadRecord entry = DownloadRecordCodec.INSTANCE.decode(value);
      if (entry != null && !entry.getPackageName().isEmpty()) {
        entries.add(entry);
      }
    }
    entries.sort(
        Comparator.comparingLong(DownloadRecord::getDownloadedAt).reversed());
    return entries;
  }

  @SuppressLint("ApplySharedPref")
  private static void write(Context context, List<DownloadRecord> entries) {
    HashSet<String> encoded = new HashSet<>();
    for (DownloadRecord entry : entries) {
      encoded.add(DownloadRecordCodec.INSTANCE.encode(entry));
    }
    State.prefs(context).edit().putStringSet(ENTRIES, encoded).commit();
  }

  private static String archiveLabel(Context context, PackageInfo info, File apk) {
    String label = info.packageName;
    ApplicationInfo applicationInfo = info.applicationInfo;
    if (applicationInfo == null) {
      return label;
    }
    applicationInfo.sourceDir = apk.getPath();
    applicationInfo.publicSourceDir = apk.getPath();
    try {
      return context.getPackageManager().getApplicationLabel(applicationInfo).toString();
    } catch (RuntimeException ignored) {
      return label;
    }
  }

  private static String sourceHost(String sourceUrl) {
    if (sourceUrl == null || sourceUrl.trim().isEmpty()) {
      return "";
    }
    try {
      String host = Uri.parse(sourceUrl).getHost();
      return host == null ? "" : host;
    } catch (RuntimeException ignored) {
      return "";
    }
  }

  private static File directory(Context context) {
    return new File(context.getFilesDir(), DIRECTORY);
  }

  private static File artifact(Context context, DownloadRecord entry) {
    if (!entry.getArtifactName().matches("[a-f0-9-]+\\.apk")) {
      return null;
    }
    return new File(directory(context), entry.getArtifactName());
  }

  private static void move(File source, File target) throws IOException {
    if (source.renameTo(target)) {
      return;
    }
    try (FileInputStream input = new FileInputStream(source);
        FileOutputStream output = new FileOutputStream(target)) {
      byte[] buffer = new byte[64 * 1024];
      int count;
      while ((count = input.read(buffer)) != -1) {
        output.write(buffer, 0, count);
      }
      output.getFD().sync();
      if (!source.delete() && source.exists()) {
        source.deleteOnExit();
      }
    } catch (IOException exception) {
      target.delete();
      throw exception;
    }
  }

  private static void notifyChanged(Context context) {
    context.sendBroadcast(
        new android.content.Intent(State.CHANGED).setPackage(context.getPackageName()));
  }
}
