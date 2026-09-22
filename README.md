# Airreload Go

Airreload Go pairs with the Airreload CLI through a pairing QR code or a pasted
pairing link, then downloads and installs the session's APK. Android remains in control of every
installation and always asks for confirmation before an app is installed.

> **Beta:** The current release is `1.1.0-beta.2` (`versionCode 5`).

## Requirements

- Android 8.0 (API 26) or newer
- Airreload CLI running on a computer on the same trusted network
- Permission for Airreload Go to install unknown apps

Airreload Go does not support Android App Bundles (`.aab`), split APK sets,
`.apks`, or `.xapk` files.

## Using the app

1. Scan the pairing QR from `airreload run` or paste its pairing link.
2. Review the computer and tap **Pair and download**. Go waits for the build,
   then downloads the APK automatically.
3. If prompted, allow Airreload Go to install apps from this source.
4. Review Android's installation screen and tap **Install**.

After installation, Airreload Go offers to open the app immediately. Select
**Always open apps after installation** to remember that choice; it can be
changed later under **Settings → After installation**.

Installed apps appear in the **Apps** list. Validated downloads remain in
**History** until you delete them.

### Pairing with Airreload CLI

When a QR comes from `airreload run`, Airreload Go identifies it as a pairing
code rather than an APK link. After you explicitly confirm pairing, Go reports
Android's `Build.SUPPORTED_ABIS` list to the local CLI. The CLI selects the
best Flutter target (`arm64-v8a`, then `armeabi-v7a`, then `x86_64`), builds
only that debug APK, and sends Go an authorized one-time download instruction.
The **Pair and download** confirmation authorizes Go to download this session's
APK automatically when ready. Android still requires its regular installation
approval. Direct APK links, ordinary URLs, and unrelated QR codes are rejected
without starting a download or pairing request.

Pairing codes expire with the CLI session and accept one phone. Local HTTP is
used only for this pre-build handshake, so use it only on a trusted development
network. Both the scanner and manual entry accept pairing links only.

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

## License

Except where otherwise noted, Airreload Go is dual-licensed under the
[Apache License, Version 2.0](LICENSE-APACHE) or the [MIT license](LICENSE-MIT),
at your option (`Apache-2.0 OR MIT`).

See [COPYRIGHT](COPYRIGHT) for the copyright notice. Third-party components
retain their respective licenses and copyright notices.
