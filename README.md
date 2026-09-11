# Airreload Go

Airreload Go is an Android companion app for downloading and installing signed,
standalone APKs from a QR code or URL. Android remains in control of every
installation and always asks for confirmation before an app is installed.

> **Beta:** The current release is `1.0.1-beta.1` (`versionCode 3`).

## Requirements

- Android 8.0 (API 26) or newer
- A signed, standalone APK
- Permission for Airreload Go to install unknown apps

Airreload Go does not support Android App Bundles (`.aab`), split APK sets,
`.apks`, or `.xapk` files.

## Using the app

1. Scan an APK QR code or paste its HTTP or HTTPS URL.
2. Review the source and tap **Download and review**.
3. If prompted, allow Airreload Go to install apps from this source.
4. Review Android's installation screen and tap **Install**.

Installed apps appear in the **Apps** list. Validated downloads remain in
**History** until you delete them.

## Build from source

Install JDK 17 and Android SDK 35, then run:

```sh
./gradlew :app:assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Tests

Run the shared and Android unit tests with:

```sh
./gradlew :shared:allTests :app:testDebugUnitTest
```

With an emulator or Android device connected, run the UI tests with:

```sh
./gradlew :app:connectedDebugAndroidTest
```

Only install APKs from developers and sources you trust.

## Project structure

- `app/` contains the Android application and platform-specific behavior.
- `shared/` contains Kotlin Multiplatform models and history logic shared with
  future platform clients.

The Android application ID is `com.airreload.app`.
