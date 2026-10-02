# demo-analysis-android

Android-порт [demo-analysis](https://github.com/Nocrex/demo-analysis) —
анализатора демо-записей Team Fortress 2 с поиском читеров. / Android port of
[demo-analysis](https://github.com/Nocrex/demo-analysis) — a TF2 demo file
cheat-detection analyser.

The Rust core (demo parser + 16 detector algorithms) is compiled into a `.so`
via `cargo-ndk`; the Kotlin app picks a `.dem` file through SAF, runs the
analysis and shows detections. The output JSON is byte-identical to the
desktop CLI.

## Features

- 16 combat algorithms (`aimsnap`, `silent_aim`, `backtrack`, `nospread`,
  `psilent`, `bunnyhop`, `auto_backstab`, `180 flips` etc.), all enabled by
  default; dev algorithms (which write files) are excluded
  / 16 боевых алгоритмов, все включены по умолчанию; dev-алгоритмы исключены
- 1–N threaded analysis (each worker re-reads the demo)
  / анализ в 1–N потоков (каждый воркер перечитывает демо)
- Real-time progress (polling Rust-core atomics, monotonic display)
  / прогресс в реальном времени (поллинг атомиков ядра, монотонный показ)
- CLI-shaped result JSON (`{server_ip, duration, author, author_steamid,
  map, players, detections}`) / JSON формата desktop-CLI
- Detection tree: player (nickname + SteamID, hold to copy) → algorithm
  groups → ticks (hold a tick to copy it)
  / дерево детекций: игрок → алгоритмы → тики, копирование по удержанию
- Demo card: author, author's SteamID, creation time, per-algorithm counts
  / карточка демо: автор, SteamID, время создания, счётчики по алгоритмам
- Settings: theme (system/light/dark) + Material You, per-algorithm
  switches, typed parameter dialogs, `params.json` import/export compatible
  with the desktop analyser
  / настройки: тема + Material You, переключатели алгоритмов, диалоги
  параметров, импорт/экспорт `params.json` (совместим с десктопом)
- Universal APK: `armeabi-v7a` + `arm64-v8a` + `x86_64`
  / универсальный APK для трёх архитектур

## Download

Grab **v0.8-beta** from [Releases](../../releases), or open the latest
`android` run in [Actions](../../actions/workflows/android.yml) and download
the **demo-analysis-android-apks** artifact — it contains
`demo-analysis-android-debug.apk` and a signed
`demo-analysis-android-release.apk` (both installable).
/ v0.8-beta лежит в [Releases](../../releases); свежие сборки — артефактом
**demo-analysis-android-apks** из последнего запуска
[Actions](../../actions/workflows/android.yml).

## Layout

```
/                          # repo root = Gradle project
├── android/               # app module (Kotlin)
├── rust/                  # cdylib crate: JNI bridge over demo-analysis
│   └── stubs/             # empty stubs for desktop-only deps
├── signing/               # shared debug keystore (CI + local)
└── SETUP.md               # environment setup
```

The analysis code is not vendored: `rust/Cargo.toml` pulls `demo-analysis` as
a git dependency from
[tf2-demo-player-aio](https://github.com/eatthefreakingpaper/tf2-demo-player-aio)
with a pinned commit.
/ Код анализа не вендорится: git-зависимость на
[tf2-demo-player-aio](https://github.com/eatthefreakingpaper/tf2-demo-player-aio)
с пином коммита.

## Build

```bash
export ANDROID_HOME=~/android-sdk
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.0.12077973"
export JAVA_HOME=~/tools/jdk-21.0.12.1+1   # Gradle 8.x rejects Java 27
./gradlew :android:assembleDebug :android:assembleRelease
```

Full environment details in [SETUP.md](SETUP.md); CI runs the same steps in
`.github/workflows/android.yml`.
/ Подробности окружения — [SETUP.md](SETUP.md); CI делает то же самое.

## How it works

1. Kotlin opens the `.dem` via SAF (`ACTION_OPEN_DOCUMENT`) and hands a
   detached file descriptor to Rust — Rust takes ownership and closes it.
   / Kotlin открывает `.dem` через SAF и передаёт в Rust file descriptor —
   Rust принимает владение и закрывает его сам.
2. Rust reads the demo into memory, drops dev algorithms, applies the JSON
   parameter config (`normalize_config` + `apply_config`).
   / Rust читает демо в память, отсекает dev-алгоритмы, применяет конфиг.
3. `analyse_multithreaded` runs the analysis; panics are caught with
   `catch_unwind` and surface as a Java `RuntimeException` instead of killing
   the process.
   / Паники ловятся `catch_unwind` и отдаются как `RuntimeException`.
4. The result is serialized to JSON (the CLI `print_detection_json` shape,
   plus `players` and `author_steamid`) and returned as a string.
   / Результат сериализуется в JSON (формат CLI + `players` и
   `author_steamid`) и возвращается строкой.

## Credits

- [demo-analysis](https://github.com/Nocrex/demo-analysis) — the analysis
  core, algorithm authors: Nocrex, fidoo, teltta (GPLv3)
- [tf2-demo-player-aio](https://github.com/eatthefreakingpaper/tf2-demo-player-aio) —
  the extended fork used as the git dependency
- [tf-demo-parser](https://github.com/demostf/parser) — the demo parser (MIT)

## Licenses

`demo-analysis` is GPLv3, the rest is MIT. Combined work: GPLv3 applies to
distributed APKs (demo-analysis is compiled into the binary).
/ `demo-analysis` — GPLv3, остальное — MIT. Для распространяемых APK
действует GPLv3.
