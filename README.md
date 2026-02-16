# MeiCaller

MeiCaller is an Android phone app prototype written in **Kotlin** and **Jetpack Compose**.
It provides a custom dialer experience, in-call UI, call history access, favorites, and configurable UI themes.

## Features

- **Tabbed main screen** with Dialer, Favorites, and Call Log.
- **Custom in-call screen** via `InCallService`.
- **Mini dialer entry points** for `tel:` links and dial intents.
- **Missed-call handling** activity integration.
- **Theme customization** (primary/accent colors) persisted with DataStore.
- **Customizable visual assets** such as button/background images.

## Tech stack

- Kotlin + Jetpack Compose
- Android SDK (compile/target SDK 36)
- Gradle Kotlin DSL
- Ktlint + Detekt for static checks

## Requirements

- Android Studio (latest stable recommended)
- JDK 17
- Android SDK 36 platform & build tools
- An Android device/emulator (phone-capable device recommended for telephony features)

## Getting started

1. Clone the repository:

   ```bash
   git clone <your-repo-url>
   cd MeiCaller
   ```

2. Build the debug app:

   ```bash
   ./gradlew :app:assembleDebug
   ```

3. Install on a connected device:

   ```bash
   ./gradlew :app:installDebug
   ```

4. Run the app from launcher as **MeiCaller**.

## Quality checks

Run all configured checks:

```bash
./gradlew qualityCheck
```

Or run individually:

```bash
./gradlew :app:ktlintCheck
./gradlew :app:detekt
./gradlew :app:lint
```

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

## Status

This project is currently a **prototype** and may change rapidly.
