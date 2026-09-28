# MeiCaller

MeiCaller is an Android phone app prototype written in **Kotlin** and **Jetpack Compose**.
It provides a custom dialer experience, in-call UI, call history access, favorites, configurable UI themes, in-app update support, and Firebase Crashlytics integration.

## Features

- **Tabbed main screen** with Dialer, Favorites, and Call Log.
- **Custom in-call screen** via `InCallService`.
- **Mini dialer entry points** for `tel:` links and dial intents.
- **Missed-call handling** activity integration.
- **Theme customization** (primary/accent colors) persisted with DataStore.
- **Customizable visual assets** such as button/background images.
- **In-app update support** through Google Play.
- **Crash reporting** with Firebase Crashlytics.

## Tech stack

- Kotlin + Jetpack Compose
- Android SDK (compile/target SDK 36)
- Gradle Kotlin DSL
- Ktlint + Detekt for static checks
- Firebase Crashlytics
- Google Play In-App Updates

## Requirements

- Android Studio (latest stable recommended)
- JDK 17
- Android 12 / API 31 or newer on the target device
- Android SDK 36 platform & build tools
- An Android device/emulator (phone-capable device recommended for telephony features)

## Getting started

1. Clone the repository:

   ```bash
   git clone https://github.com/pehab/MeiCaller.git
   cd MeiCaller
   ```

2. Build the debug app:

   ```bash
   bash gradlew :app:assembleDebug
   ```

3. Install on a connected device:

   ```bash
   bash gradlew :app:installDebug
   ```

4. Run the app from launcher as **MeiCaller**, select it as the default phone app when prompted, and grant the requested permissions. Verify real calls on a phone; a successful APK build does not validate device-specific telephony behavior.

## Quality checks

Run all configured checks (CI runs these before building):

```bash
bash gradlew qualityCheck
```

Or run individually:

```bash
bash gradlew :app:ktlintCheck
bash gradlew :app:detekt
bash gradlew :app:lint
```

The app module currently has no unit-test sources; `testDebugUnitTest` reports `NO-SOURCE`. The `buildSrc` module contains 11 JVM regression tests for the platform compatibility patch, run in CI with `bash gradlew -p buildSrc test`. CI also builds the debug APK and release bundle. These tests do not cover telephony behavior. Detekt runs, but `app/config/detekt/detekt.yml` currently sets `maxIssues: 999999`, so its findings are not an effective failure gate. Review the report under `app/build/reports/detekt/`; introduce a reviewed baseline and a strict budget in a separate cleanup.

## Firebase configuration

`app/google-services.json` configures Firebase Crashlytics. For a separate Firebase project, register the application ID `de.haberland.meicaller` and replace that file with the matching configuration. Play in-app updates require a Play-installed build and an available update in the corresponding track.

## Permissions

The app requests permissions related to telephony and call features, including:

- `READ_PHONE_STATE`
- `CALL_PHONE`
- `READ_CONTACTS`
- `READ_CALL_LOG`
- `WRITE_CALL_LOG`
- `VIBRATE`
- `WAKE_LOCK`

## Project structure

- `app/src/main/java/de/haberland/meicaller/` – application source code
- `app/src/main/java/de/haberland/meicaller/ui/` – Compose UI screens
- `app/src/main/java/de/haberland/meicaller/telephony/` – call/in-call integration
- `app/src/main/java/de/haberland/meicaller/data/` – persistent settings stores
- `app/config/detekt/` – detekt configuration

## Privacy

See [PRIVACY_POLICY.md](PRIVACY_POLICY.md).

## Current version

- **Prototype 18** (`versionCode 18`)

## Status

MeiCaller is currently in active prototype testing. Stability fixes and dependency updates are validated through GitHub Actions before new Play test builds are published.

## Platform compatibility fix in Prototype 18

The supplied crashes occur inside AndroidX, before or during Compose initialization:

- `FontWeightAdjustmentHelperApi31` reads a missing `Configuration.fontWeightAdjustment` field.
- `WindowInsetsCompat.TypeImpl34` calls a missing `WindowInsets.Type.systemOverlays()` method.

These API-level-specific paths are reached on runtimes that do not provide the expected platform members. One trace contains instrumentation (`com.mojito.utility`); the traces alone do not establish which device/OS combinations or testing environments are affected.

`buildSrc` uses the Android Gradle Plugin instrumentation API to replace exactly these two member accesses, in the two named AndroidX helpers, with `PlatformApiCompat`. The bridge looks up the actual public members. Available values are preserved, including accessibility font-weight settings. Only missing members fall back to neutral font weight / no additional overlay bits; other errors still propagate. Status/navigation bar bits remain intact. No Android version spoofing or global exception suppression is used.

This is a dependency-bytecode compatibility patch, not an upstream AndroidX fix. It applies automatically to both debug APKs and release bundles, including Android Studio builds. Keep it scoped to the documented helper classes and review it on AndroidX upgrades; a selected class without the expected access fails the build. Remove it once upstream reliably handles these missing-member cases. A renamed upstream class requires explicit review too.

Run its JVM regression tests with:

```bash
bash gradlew -p buildSrc test
```

Tests reproduce the missing-field failure using a synthetic framework class, execute the transformed bytecode, verify overlay bit preservation and check that normal platform values and unrelated failures are preserved. CI also builds the release bundle. Physical-device verification and monitoring of new Prototype 18 events remain necessary; historic Prototype 16/17 crash counts will not disappear after an update.
