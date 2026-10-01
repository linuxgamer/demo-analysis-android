# demo-analysis-android

Android-порт [demo-analysis](https://github.com/Nocrex/demo-analysis) — анализатора
демо-записей Team Fortress 2 с поиском читеров. / Android port of
[demo-analysis](https://github.com/Nocrex/demo-analysis) — a TF2 demo file
cheat-detection analyser.

Rust-ядро (парсер демо + 16 алгоритмов-детекторов) собирается в `.so` через
`cargo-ndk`; Kotlin-приложение выбирает `.dem` файл через SAF, прогоняет анализ
и показывает детекции. JSON на выходе бит-в-байт совпадает с десктопным CLI.
/ The Rust core (demo parser + 16 detector algorithms) is compiled into a `.so`
via `cargo-ndk`; the Kotlin app picks a `.dem` file through SAF, runs the
analysis and shows detections. The output JSON is byte-identical to the
desktop CLI.

## Возможности / Features

- 16 боевых алгоритмов (`aimsnap`, `silent_aim`, `backtrack`, `nospread`, `psilent`,
  `bunnyhop`, `auto_backstab`, `180 flips` и др.); dev-алгоритмы (пишут файлы) исключены
  / 16 combat algorithms; dev algorithms (which write files) are excluded
- Анализ в 1–N потоков (каждый воркер перечитывает демо)
  / 1–N threaded analysis (each worker re-reads the demo)
- Прогресс в реальном времени (поллинг атомиков из Rust-ядра, 4 опроса/с)
  / Real-time progress (polling Rust-core atomics 4x/s)
- Результат — JSON формата desktop-CLI (`{server_ip, duration, author, map, detections}`),
  шаринг через Android intent / CLI-shaped result JSON, shared via an Android intent
- Демо передаётся в Rust через file descriptor (одна копия файла в памяти)
  / The demo is handed to Rust as a file descriptor (a single in-memory copy)

## Скачивание / Download

Зайди в [Actions](../../actions/workflows/android.yml), открой последний запуск
`android` и скачай артефакт **demo-analysis-android-apks** — внутри
`android-debug.apk` и подписанный `android-release.apk` (оба ставятся на
устройство). На тегах `v*` те же APK прикладываются к
[Releases](../../releases).
/ Open the latest `android` run in [Actions](../../actions/workflows/android.yml)
and grab the **demo-analysis-android-apks** artifact — it contains
`android-debug.apk` and a signed `android-release.apk` (both installable). On
`v*` tags the same APKs are attached to [Releases](../../releases).

## Структура / Layout

```
/                          # корень = Gradle-проект / repo root = Gradle project
├── android/               # модуль приложения (Kotlin) / app module
├── rust/                  # cdylib crate: JNI-мост над demo-analysis / JNI bridge
│   └── stubs/             # пустые стабы rfd/opener / empty stubs for desktop-only deps
└── SETUP.md               # настройка окружения / environment setup
```

Код анализа не вендорится: `rust/Cargo.toml` подключает `demo-analysis`
git-зависимостью из [tf2-demo-player-aio](https://github.com/eatthefreakingpaper/tf2-demo-player-aio)
с пином коммита.
/ The analysis code is not vendored: `rust/Cargo.toml` pulls `demo-analysis`
as a git dependency from
[tf2-demo-player-aio](https://github.com/eatthefreakingpaper/tf2-demo-player-aio)
with a pinned commit.

## Сборка / Build

```bash
export ANDROID_HOME=~/android-sdk
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.0.12077973"
export JAVA_HOME=~/tools/jdk-21.0.12.1+1   # Gradle 8.x не дружит с Java 27 / rejects Java 27
./gradlew :android:assembleDebug :android:assembleRelease
```

Подробности окружения — [SETUP.md](SETUP.md). CI делает то же самое:
`.github/workflows/android.yml`.
/ Full environment details in [SETUP.md](SETUP.md); CI runs the same steps in
`.github/workflows/android.yml`.

## Как это работает / How it works

1. Kotlin открывает `.dem` через SAF (`ACTION_OPEN_DOCUMENT`), передаёт в Rust
   отсоединённый file descriptor — Rust принимает владение и закрывает его сам.
   / Kotlin opens the `.dem` via SAF (`ACTION_OPEN_DOCUMENT`) and hands a
   detached file descriptor to Rust — Rust takes ownership and closes it.
2. Rust читает демо в память, отсекает dev-алгоритмы, применяет JSON-конфиг
   параметров (`normalize_config` + `apply_config`).
   / Rust reads the demo into memory, drops dev algorithms, applies the JSON
   parameter config (`normalize_config` + `apply_config`).
3. `analyse_multithreaded` гонит анализ; паники ловятся `catch_unwind` и отдаются
   в Kotlin как `RuntimeException`, а не роняют процесс.
   / `analyse_multithreaded` runs the analysis; panics are caught with
   `catch_unwind` and surface as a Java `RuntimeException` instead of killing
   the process.
4. Результат сериализуется в JSON (формат `print_detection_json` CLI) и
   возвращается строкой.
   / The result is serialized to JSON (the CLI `print_detection_json` shape)
   and returned as a string.

## Credits

- [demo-analysis](https://github.com/Nocrex/demo-analysis) — ядро анализа, авторы
  алгоритмов: Nocrex, fidoo, teltta (GPLv3) / the analysis core, algorithm authors
- [tf2-demo-player-aio](https://github.com/eatthefreakingpaper/tf2-demo-player-aio) —
  расширенный форк demo-analysis, используемый как git-зависимость / the extended
  fork used as the git dependency
- [tf-demo-parser](https://github.com/demostf/parser) — парсер демо (MIT) / the demo parser

## Лицензии / Licenses

`demo-analysis` — GPLv3, остальное — MIT. Комбинированная работа: при
распространении APK применяется GPLv3 (код demo-analysis компилируется прямо
в бинарник).
/ `demo-analysis` is GPLv3, the rest is MIT. Combined work: GPLv3 applies to
distributed APKs (demo-analysis is compiled into the binary).
