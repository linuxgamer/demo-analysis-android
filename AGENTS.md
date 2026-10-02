# AGENTS.md

Project goal: GoDetect — Android port of **demo-analysis** (Rust crate that
analyzes TF2 demo files for cheaters). The repository root is the Gradle
project root. This file is for agents working in this repo.

## Repository layout

```
/godetect
├── README.md                # project overview, build, licenses
├── SETUP.md                 # environment setup (Rust, SDK, NDK, JDK 21)
├── .github/workflows/android.yml  # CI: unit tests, debug+release APKs, release on tags
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── android/                 # application module (Kotlin)
│   ├── build.gradle.kts     # buildRust task: cargo-ndk → src/main/jniLibs
│   ├── proguard-rules.pro   # keeps JNI entry points under R8
│   └── src/main/java/dev/godetect/tf2demo/
│       ├── DemoAnalysis.kt  # external funs (JNI), algorithmsJson cache
│       ├── AnalysisViewModel.kt  # owns the analysis run (rotation-safe)
│       ├── MainActivity.kt  # toolbar, demo card, detection tree, progress
│       ├── DetectionAdapter.kt   # player → algorithm → tick tree, copy actions
│       ├── AlgorithmInfo.kt # static algorithm descriptions
│       ├── SettingsActivity.kt / SettingsStore.kt / AppearanceStore.kt
│       └── AnalyzerApp.kt   # night mode + Material You
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
  nospread, auto_backstab, bunnyhop, invalid_equip_region}`. All run by
  default — `backtrack`, `double_tap` and `nocrex/aimsnap` are disabled
  upstream but forced on by `SettingsStore.FORCE_DEFAULT_ON`.
- Dev (3): `all_messages`, `write_to_file`, `viewangles_to_csv` — they write
  into `./output` and panic in `init()` on failure. The Rust JNI layer cuts
  them off via the `DEV_ALGORITHMS` list; they never reach the UI.
- Long descriptions for the UI live in `AlgorithmInfo.kt` + string resources
  (`algo_*` keys, English and Russian).

## Android layer

- `rust/src/lib.rs` — the JNI bridge (`Java_dev_godetect_tf2demo_DemoAnalysis_*`):
  `version`, `algorithmsJsonRaw` (algorithm + parameter schema; cached
  Kotlin-side), `analyse(fd, algorithms, configJson, threads)`,
  `progressCurrent/Total`, `resetProgress`, `cancelAnalysis`.
- fd ownership: Kotlin calls `detachFd()`, Rust adopts `File::from_raw_fd` as
  its **very first action** and closes it on every path (never close twice).
- Core panics are caught by `catch_unwind` → Java `RuntimeException`.
- Cancellation: `cancelAnalysis()` sets an atomic; the progress callback
  panics on the next tick and the unwind is reported as
  "analysis cancelled by user" (in multithreaded mode the generic join error
  is replaced by a post-check).
- The result JSON mirrors the CLI `print_detection_json` format plus two
  extras for the UI: `players` (steamid64 → nickname, from
  `CheatAnalyserState.player_names`) and `author_steamid` (filled only when
  the header nick maps to exactly one known player). No `println!`, built
  from public `CheatAnalyser` fields.
- Kotlin layout:
  - `DemoAnalysis.kt` — external funs (JNI), algorithmsJson cached in-process.
  - `AnalysisViewModel.kt` — owns the blocking JNI call and the progress
    poller in `viewModelScope`; activity recreation only re-renders.
  - `MainActivity.kt` — toolbar (title + icon, PICK DEMO / SETTINGS menu),
    demo card (author/SteamID/created hold-to-copy), detection tree,
    progress with Cancel, per-app language is handled by AppCompat.
  - `DetectionAdapter.kt` — player → algorithm → tick tree: hold to copy
    SteamID/tick, tap a tick for its `data` payload, long-press an algorithm
    row for its description, ↗ button opens the player's profile site.
  - `SettingsActivity.kt` + `SettingsStore.kt` — appearance (theme, language,
    profile site), performance (worker threads, demo size limit), per
    algorithm switches, typed parameter dialogs, `params.json` import/export
    (same shape as the desktop file; Rust-side `normalize_config`
    guarantees compatibility).
  - `AppearanceStore.kt` — theme mode + profile site pref +
    `applySystemBarTheme` (status/nav icon colors follow the theme).
  - `AnalyzerApp.kt` — `AppCompatDelegate.setDefaultNightMode` on startup;
    DynamicColors (Material You). An AMOLED mode existed once and was
    removed — it fought the dynamic color overlay. Do not resurrect.
  - `SettingsStore.FORCE_DEFAULT_ON` — algorithms the app enables by
    default although upstream disables them.
- Edge-to-edge: targetSdk 35 forces it; both activities pad their roots by
  system-bar + display-cutout insets. `localeConfig` + AppCompat
  `autoStoreLocales` power the per-app language.
- Signing: both build types use the keystore committed at `signing/debug.keystore`
  (standard Android debug credentials: password "android", alias
  "androiddebugkey"), so CI and local builds share one signature and update
  over each other without uninstalling. Never use this key for store
  publishing.

## Build

Requirements: Rust + `armv7-linux-androideabi`/`aarch64-linux-android`/
`i686-linux-android`/`x86_64-linux-android` targets, `cargo-ndk`, Android
SDK + NDK 27.0.12077973, **JDK 21** (Gradle 8.x rejects Java 27; on this
machine the JDK lives in `~/tools/jdk-21.0.12.1+1`, the SDK in
`~/Проекты/android-sdk`, exports already in `~/.bashrc`).

```bash
export JAVA_HOME="$HOME/tools/jdk-21.0.12.1+1"
export ANDROID_HOME="$HOME/Проекты/android-sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.0.12077973"
./gradlew :android:assembleDebug :android:assembleRelease
```

- The `:android:buildRust` task runs `cargo-ndk` (all four ABIs,
  `--platform 26`) and drops the `.so` files into `android/src/main/jniLibs`
  (not committed). APKs are universal and named
  `demo-analysis-android-<buildType>.apk` via `base.archivesName`.
- The release `.so` gets LTO + strip from the `[profile.release]` section in
  `rust/Cargo.toml` (upstream's own profile section does not apply to
  dependency builds); release Kotlin is minified by R8 (see
  `android/proguard-rules.pro`).
- Quick core check without a device: `cargo check` in `rust/` (needs network —
  git dependency). For a JNI smoke test under a host JVM, see the session
  history: a class with the native methods of
  `dev.godetect.tf2demo.DemoAnalysis` plus
  `rust/target/debug/libdemo_analysis_android.so`.
- CI does the same: `.github/workflows/android.yml` (ubuntu-latest, temurin
  21, ndk 27, cargo-ndk from taiki-e/install-action). It runs the Kotlin
  unit tests, builds debug + release and uploads both APKs as one artifact;
  tags attach them to a release. The release job globs
  `**/demo-analysis-android-*.apk` — the artifact keeps the
  `apk/<variant>/` layout.
- Current version: v1.0 (`versionCode` 9).

## Platform constraints (important)

- `tf-demo-parser` keeps the whole demo in memory — the app enforces a
  configurable size limit (auto = 1/4 of total RAM) before starting.
- `analyse_multithreaded`: every worker keeps its own state; worker count is
  a setting (1/2/4, default 2).
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
  `./gradlew :android:assembleDebug` must pass. Unit tests:
  `./gradlew :android:testDebugUnitTest`.
