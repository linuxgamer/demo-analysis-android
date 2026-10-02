# GoDetect

TF2 demo cheat-detection analyser on Android — a port of
[demo-analysis](https://github.com/Nocrex/demo-analysis).

The Rust core (demo parser + 16 detector algorithms) is cross-compiled into a
`.so` with `cargo-ndk`; the Kotlin app picks a `.dem` file through SAF, runs
the analysis and renders the detections. The output JSON matches the desktop
CLI byte-for-byte.

## Features

- 16 combat algorithms (`aimsnap`, `silent_aim`, `backtrack`, `nospread`,
  `psilent`, `bunnyhop`, `auto_backstab`, `180 flips` etc.), all enabled by
  default; dev algorithms (which write files) are excluded
- 1–4 threaded analysis (each worker re-reads the demo), rotation-safe via a
  ViewModel, cancel button
- Monotonic real-time progress (polled from Rust-core atomics)
- Detection tree: player (nickname + SteamID, hold to copy) → algorithm
  groups (long-press for a description) → ticks (tap for details, hold to
  copy the tick number)
- Demo card: author, author's SteamID, creation time, per-algorithm counts,
  hold-to-copy values
- Open suspects on Steam / SteamHistory / Shadefall from the detection tree
- Settings: theme (system/light/dark) + Material You, per-app language
  (system/English/Russian), profile site, worker threads, demo size limit
  (auto-scaled to device RAM), per-algorithm parameter dialogs, desktop
  `params.json` import/export, reset to defaults, About/Licenses
- Universal APK: `armeabi-v7a` + `arm64-v8a` + `x86` + `x86_64`
- Minimum Android 8.0 (API 26)

## Download

Grab **v1.0** from [Releases](../../releases), or open the latest `android`
run in [Actions](../../actions/workflows/android.yml) and download the
**demo-analysis-android-apks** artifact — it contains
`demo-analysis-android-debug.apk` and a signed
`demo-analysis-android-release.apk` (both installable).

## Layout

```
/                          # repo root = Gradle project
├── android/               # app module (Kotlin)
├── rust/                  # cdylib crate: JNI bridge over demo-analysis
│   └── stubs/             # empty stubs for desktop-only deps
├── signing/               # shared debug keystore (CI + local)
└── SETUP.md               # environment setup
```

The analysis code is not vendored: `rust/Cargo.toml` pulls `demo-analysis`
as a git dependency from
[tf2-demo-player-aio](https://github.com/eatthefreakingpaper/tf2-demo-player-aio)
with a pinned commit.

## Build

```bash
export ANDROID_HOME=~/android-sdk
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.0.12077973"
export JAVA_HOME=~/tools/jdk-21.0.12.1+1   # Gradle 8.x rejects Java 27
./gradlew :android:assembleDebug :android:assembleRelease
```

Full environment details in [SETUP.md](SETUP.md); CI runs the same steps in
`.github/workflows/android.yml`.

## How it works

1. Kotlin opens the `.dem` via SAF (`ACTION_OPEN_DOCUMENT`) and hands a
   detached file descriptor to Rust — Rust takes ownership and closes it.
2. Rust reads the demo into memory, drops dev algorithms, applies the JSON
   parameter config (`normalize_config` + `apply_config`).
3. `analyse_multithreaded` runs the analysis; panics are caught with
   `catch_unwind` and surface as a Java `RuntimeException` instead of
   killing the process. Cancellation works the same way: a flag set from
   JNI makes the progress callback panic, which unwinds the parse loop.
4. The result is serialized to JSON (the CLI `print_detection_json` shape,
   plus `players` and `author_steamid`) and returned as a string.

## Credits

- [demo-analysis](https://github.com/Nocrex/demo-analysis) — the analysis
  core; algorithm authors: Nocrex, fidoo, teltta (GPLv3)
- [tf2-demo-player-aio](https://github.com/eatthefreakingpaper/tf2-demo-player-aio) —
  the extended fork used as the git dependency
- [tf-demo-parser](https://github.com/demostf/parser) — the demo parser (MIT)

## Licenses

`demo-analysis` is GPLv3, the rest is MIT. Combined work: GPLv3 applies to
distributed APKs (demo-analysis is compiled into the binary). This app is
not affiliated with Valve.
