# GoDetect

Analyze demos on the go!

A port of [demo-analysis](https://github.com/Nocrex/demo-analysis) to android.

Rust(unmodified) analyzer is compiled into `.so`, and kotlin app(GUI) communicates with it to analyze demos

## Features

- 16 algorithms all enabled by
  default; dev algorithms (which write files) are excluded
- 1–4 threaded analysis and a cancel button
- Detection tree: player (nickname + SteamID, hold to copy) → algorithm
  groups (long-press for a description) → ticks (tap for details, hold to
  copy the tick number)
- Demo card: author, author's SteamID, demo creation time, total detections,
  hold to copy values
- Open suspects on Steam/SteamHistory/Shadefall from the detection tree
- Settings: theme (system/light/dark) + Material you, app language
  (system/English/Russian), website for opening profiles, worker threads, demo size limit
  (1/4 of device RAM), algorithm parameters, desktop
  `params.json` import/export, reset to defaults
- Minimum Android 8.0 (API 26)

## Download

Grab **latest release** from [Releases](../../releases), or open the latest CI
run in [Actions](../../actions/workflows/android.yml) and download the
**demo-analysis-android-apks** artifact — it is a .zip file with apks yk

## Layout

```
/                          # repo root = Gradle project
├── android/               # app module (Kotlin)
├── rust/                  # cdylib crate: JNI bridge over demo-analysis
│   └── stubs/             # empty stubs for desktop-only deps
├── signing/               # shared debug keystore (CI + local)
└── SETUP.md               # environment setup
```

Analysis code is not modified and is taken as a git dependency from
[tf2-demo-player-aio](https://github.com/eatthefreakingpaper/tf2-demo-player-aio)
with a pinned commit.

## Build

```bash
export ANDROID_HOME=~/android-sdk
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.0.12077973"
export JAVA_HOME=~/tools/jdk-21.0.12.1+1   # Gradle 8.x rejects Java 27
./gradlew :android:assembleDebug :android:assembleRelease
```

[SETUP.md](SETUP.md) is much more detailed. CI does same stuff in
`.github/workflows/android.yml`.

## Credits

- [demo-analysis](https://github.com/Nocrex/demo-analysis) — demo analyzer i ported
- [tf2-demo-player-aio](https://github.com/eatthefreakingpaper/tf2-demo-player-aio) —
  the extended fork used for extra algorithms
- [tf-demo-parser](https://github.com/demostf/parser) — the demo parser
