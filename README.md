# Airreload Go

Airreload Go pairs with the Airreload CLI through a pairing QR code or a pasted
pairing link, then downloads and installs the session's APK. Android remains in control of every
installation and always asks for confirmation before an app is installed.

> **Beta:** The current release is `1.1.0-beta.6` (`versionCode 9`), adding usage analytics with a Settings opt-out.

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

If Android rejects an APK because a newer version or a conflicting signing
certificate is already installed, tap **Uninstall and reinstall**. Android asks
you to confirm removal; uninstalling removes that app's local data. Go then
installs the saved APK with Android's usual installation confirmation, without
another download or build. Cancelling uninstall keeps the installed app in
place. If the saved APK has been deleted, reconnect to download it again.

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

Go keeps waiting while the computer reports that the build is running, including
builds longer than ten minutes. Brief connection failures retry automatically;
a continuous loss of contact for about a minute shows recovery instructions.
Cancel remains available while waiting. Use the updated CLI as well as Go for
safe retries of the initial pairing request. If the CLI session has stopped,
start it again and scan its new code.

The reconnect flow remembers the most recently approved
session. After returning to the same Wi-Fi, tap **Reconnect to computer** on
Home, even if Go was closed or pairing was cancelled. You can also use **Show
reconnect QR** on the computer's pairing page and rescan with the same phone.
Go resumes waiting or downloads the finished APK without triggering another
build. Keep the original CLI process running at the same network address;
restarting it creates a new session. This does not reuse APKs across separate
CLI runs or let a different phone take over the session. The phone stores its
session credentials privately, excluded from Android backup and transfer.

Pairing codes expire with the CLI session and accept one phone. Local HTTP is
used only for this pre-build handshake, so use it only on a trusted development
network. Both the scanner and manual entry accept pairing links only.

## Usage analytics

Usage analytics is **enabled by default**. Turn it off in **Settings → Privacy →
Share usage analytics**. Turning it off deletes the local random installation ID;
turning it back on creates a new ID on the next event. Already received events
remain in PostHog. Preferences and the ID use app-private storage, excluded from
Android backup along with the rest of the app's data.

Go sends explicit `go_` events for app opens, pairing starts/completion/failure/
cancellation, download starts/completion/failure/cancellation, and install
success/failure/cancellation. Pairing completion means the CLI has returned the
ready APK download link. Install failures include Android rejecting or cancelling
an installation; the separate cancellation event covers cancelling in Go.
Repeated progress updates and screen redraws do not produce events.

Each event contains a timestamp, random installation ID, `surface=android`, Go
version, and Android API level. It never includes app names/package names,
installed-app lists, QR contents, pairing URLs/tokens, APK URLs, filenames, or raw
errors/logs. There is no session replay, autocapture, advertising ID, or cross-device
identity linking. Requests disable PostHog person profiles and GeoIP enrichment;
the receiving server can still see the connection IP. Project-level IP discarding
is a separate PostHog setting.

Events go directly over HTTPS to the same US PostHog project as the CLI. Only the
public `phc_` collection token is embedded. Sending uses one background worker,
a 32-event memory queue, one-second connect/read timeouts, and a 200-event cap per
app process. Events waiting more than 15 seconds are dropped. No disk queue,
retries, or redirects are used, and failures never affect pairing/installing.
Opt-out invalidates queued events; an already transmitted event cannot be recalled.
Unit tests inject local stores and transports and send nothing to PostHog.

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

The live analytics smoke test is skipped by default. On a disposable emulator,
explicitly enable it to exercise the Settings opt-out, activity relaunch, and
installation-ID rotation against the configured PostHog project:

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.airreload.airreload.TelemetryLiveTest \
  -Pandroid.testInstrumentationRunnerArguments.livePosthog=true
```

This sends real `go_app_opened` events. Verify delivery in PostHog Activity and
match the IDs printed under the `TelemetryLiveTest` logcat tag. The test leaves
analytics enabled; do not run it on a user's installation.

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
