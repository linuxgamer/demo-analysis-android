# AGENTS.md

Project goal: Android port of **demo-analysis** (Rust crate that analyzes TF2
demo files for cheaters). The repository root is the Gradle project root. This
file is for agents working in this repo.

## Repository layout

```
/demo-analysis-android
├── README.md                # project overview, build, licenses
├── SETUP.md                 # environment setup (Rust, SDK, NDK, JDK 21)
├── .github/workflows/android.yml  # CI: debug+release APKs (artifacts) + release on tags
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── android/                 # application module (Kotlin)
│   ├── build.gradle.kts     # buildRust task: cargo-ndk → src/main/jniLibs
│   └── src/main/java/com/tf2demo/analyzer/
│       ├── DemoAnalysis.kt  # external funs (JNI)
│       └── MainActivity.kt  # SAF picker, analysis on Dispatchers.IO, progress polling
└── rust/                    # cdylib crate demo-analysis-android (JNI bridge)
    ├── Cargo.toml           # git dependency on demo-analysis (rev-pinned!)
    ├── src/lib.rs           # the whole JNI layer
    └── stubs/{rfd,opener}/  # empty stubs for desktop-only deps via [patch.crates-io]
```

## The demo-analysis dependency

The analysis code is **not vendored**: `rust/Cargo.toml` pulls in
`demo-analysis` as a git dependency from
[eatthefreakingpaper/tf2-demo-player-aio](https://github.com/eatthefreakingpaper/tf2-demo-player-aio)
(the package lives in the `demo-analysis/` subdirectory; cargo finds it by
name). The `rev` pin is mandatory for reproducibility. Bumping the pin =
replacing `rev` in `rust/Cargo.toml` + updating `rust/Cargo.lock`.

That repo is an extended fork of https://github.com/Nocrex/demo-analysis
(GPLv3): public `CheatAnalyser.analyser` field, extended `CheatAnalyserState`,
the `nocrex/` and `fidoo/` algorithm sets, and a parameter system.

## What demo-analysis does

Reads a `.dem` file (a TF2 match recording), streams it through
`tf-demo-parser`, builds a `CheatAnalyserState` (players, buildings, weapons,
projectiles, states) on every tick and runs a set of detector algorithms.
Output: JSON with a list of `Detection { tick, algorithm, player (steamid64),
data }`.

Key entry points (`demo-analysis/src/lib/algorithm.rs` in the dependency):
- `get_algorithms()` — registry of all algorithms.
- `analyse(&demo, algorithms, progress_cb)` — single-pass analysis.
- `analyse_multithreaded(bytes, algorithms, threads, cb)` — one thread per
  algorithm group, each re-reads the demo stream.
- `CheatAlgorithm` trait — the algorithm contract; `Detection` — the result.

The state core is `demo-analysis/src/base/cheat_analyser_base.rs`:
`CheatAnalyserState`, `CheatAnalyser` (implements `MessageHandler` from
`tf-demo-parser`), `Player`, `Building`/`Sentry`/`Dispenser`/`Teleporter`,
`WeaponEntity`, `World`. `base/demo_handler_base.rs` — `CheatDemoHandler`,
pumps packets into the analyser (the `analyser` field is public — a local
difference of this fork).

Clone sources land in:
`~/.cargo/git/checkouts/tf2-demo-player-aio-*/<rev>/demo-analysis/`.

## Algorithms

- Combat (16): `viewangles_180degrees`, `angle_history`, `backtrack`,
  `double_tap`, `triggerbot`, `firewindow`, `recorder_aim_assist`,
  `nocrex/{aimsnap, angle_repeat, oob_pitch}`, `fidoo/{silent_aim, psilent4,
  nospread, auto_backstab, bunnyhop, invalid_equip_region}`. The commented
  example is `viewangles_180degrees.rs`.
- Dev (3): `all_messages`, `write_to_file`, `viewangles_to_csv` — they write
  into `./output` and panic in `init()` on failure. On Android they are cut
  off by the `DEV_ALGORITHMS` list in `rust/src/lib.rs` and never shown in UI.

## Android layer

- `rust/src/lib.rs` — the JNI bridge: `version`, `algorithmsJson` (algorithm +
  parameter schema for the UI), `analyse(fd, algorithms, configJson, threads)`,
  `progressCurrent/Total/resetProgress` (core global atomics).
- fd ownership: Kotlin calls `detachFd()`, Rust adopts `File::from_raw_fd` as
  its **very first action** and closes it on every path (never close twice).
- Core panics are caught by `catch_unwind` → Java `RuntimeException`.
- The result JSON mirrors the CLI `print_detection_json` format (no
  `println!`, built from public `CheatAnalyser` fields).
- Kotlin: `DemoAnalysis.kt` (external funs), `MainActivity.kt` (SAF picker,
  `Dispatchers.IO`, progress polling 4x/s, JSON sharing).
- Signing: both build types use the keystore committed at `signing/debug.keystore`
  (standard Android debug credentials: password "android", alias "androiddebugkey"),
  so CI and local builds share one signature and update over each other without
  uninstalling. Never use this key for store publishing.

## Build

Requirements: Rust + `armv7-linux-androideabi`/`aarch64-linux-android`/`x86_64-linux-android` targets,
`cargo-ndk`, Android SDK + NDK 27.0.12077973, **JDK 21** (Gradle 8.x rejects
Java 27; on this machine the JDK lives in `~/tools/jdk-21.0.12.1+1`, the SDK
in `~/Проекты/android-sdk`, exports already in `~/.bashrc`).

```bash
export JAVA_HOME="$HOME/tools/jdk-21.0.12.1+1"
export ANDROID_HOME="$HOME/Проекты/android-sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.0.12077973"
./gradlew :android:assembleDebug :android:assembleRelease
```

- The `:android:buildRust` task runs `cargo-ndk` (arm64-v8a + x86_64,
  `--platform 26`) and drops the `.so` files into `android/src/main/jniLibs`
  (not committed).
- Quick core check without a device: `cargo check` in `rust/` (needs network —
  git dependency). For a JNI smoke test under a host JVM, see the session
  history: a class with the native methods of `com.tf2demo.analyzer.DemoAnalysis`
  plus `rust/target/debug/libdemo_analysis_android.so`.
- CI does the same: `.github/workflows/android.yml` (ubuntu-latest, temurin 21,
  ndk 27, cargo-ndk from taiki-e/install-action).

## Platform constraints (important)

- `tf-demo-parser` keeps the whole demo in memory — demos reach hundreds of MB
  (OOM risk).
- `analyse_multithreaded`: every worker keeps its own state; cap `threads` on
  mobile (currently 2, passed from Kotlin).
- The git dependency needs network on first build; Cargo.lock pins everything
  transitively, but the fork revision is pinned manually via `rev`.

## License

`demo-analysis` is GPLv3, the rest is MIT. Combined work: GPLv3 applies to
distributed APKs.

## Working rules

- Patches to the demo-analysis fork go through a separate fork repository,
  never locally.
- Comments follow repository style (English, meaningful).
- After Rust changes: `cargo check` in `rust/`; after Kotlin changes:
  `./gradlew :android:assembleDebug` must pass.
