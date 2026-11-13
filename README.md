# nft-tag

Tap a reused **Bambu Lab spool NFC tag** (or any NFC sticker) to start white noise
in **[ChromaNoise](https://github.com/moblized/chromanoise)** — meant for the bedside
charger, so you tap as you set the phone down for the night.

Bambu spool tags are MIFARE Classic 1K. This app doesn't touch their locked filament
data or clone anything — it just reads the tag's **UID** (no keys needed) and uses it
as a trigger. So an empty spool's tag becomes a free bedside button.

nft-tag doesn't make any sound itself. On a tap it hands off to ChromaNoise's no-UI
entry point (`app.chromanoise/.playback.PlayTriggerActivity`), so ChromaNoise owns the
actual noise, volume and presets.

## What it does

- **Tap to play** — tapping the tag tells ChromaNoise to start.
- **Pick a sound** — "Last used" (whatever ChromaNoise played last) or a built-in
  preset (White / Pink / Brown / Grey / Blue / Violet), passed through on the tap.
- **Sleep timer** — off, 15/30/45/60/90 min, armed in ChromaNoise.
- **Pair one tag** — lock the trigger to a single tag's UID, or leave it open so any
  tapped tag works.
- **Theme** — system / light / dark / pure-black, with a dark app icon in dark mode.

## Requires

**ChromaNoise** installed on the phone. If it isn't, a tap just shows a short notice.

## The one real limitation

Android only reads NFC while the **screen is on and unlocked**. So this is a deliberate
tap as you dock the phone, not a fully hands-off "set it down and it starts." If it's a
**Qi wireless charger**, keep the tag near the edge of the pad, away from the coil, or
the charging coil will block the read. The Samsung NFC antenna sits upper-center on the
back.

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
