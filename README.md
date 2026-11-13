# nft-tag

Tap a reused **Bambu Lab spool NFC tag** (or any NFC sticker) to start white noise
on your Samsung — meant for the bedside charger, so you tap as you set the phone
down for the night.

Bambu spool tags are MIFARE Classic 1K. This app doesn't touch their locked
filament data or clone anything — it just reads the tag's **UID** (no keys needed)
and uses it as a trigger. So an empty spool's tag becomes a free bedside button.

## What it does

- **Tap to play** — tapping the tag launches the app and starts noise.
- **White / pink / brown** noise, generated on the fly (no audio files).
- **Keeps playing with the screen off** via a foreground service.
- **Sleep timer** — off, 15/30/45/60/90 min.
- **Pair one tag** — lock the trigger to a single tag's UID, or leave it open so
  any tapped tag works.
- **Theme** — system / light / dark / pure-black, with a dark app icon in dark mode.

## The one real limitation

Android only reads NFC while the **screen is on and unlocked**. So this is a
deliberate tap as you dock the phone, not a fully hands-off "set it down and it
starts." If it's a **Qi wireless charger**, keep the tag near the edge of the pad,
away from the coil, or the charging coil will block the read. The Samsung NFC
antenna sits upper-center on the back.

## Build

APKs are built by GitHub Actions (`.github/workflows/build.yml`):

- Every push to `main` and manual runs produce a debug APK as a build artifact.
- Pushing a tag like `v0.1.0` publishes a **GitHub Release** with the APK attached.

Locally, open in Android Studio, or with Gradle 8.9 on your PATH run:

```bash
gradle assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Stack

Kotlin · Jetpack Compose · Material 3 · `android.nfc` · minSdk 26 · targetSdk 34
