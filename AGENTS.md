# AGENTS.md

Цель проекта: Android-порт **demo-analysis** (Rust-крейт для анализа TF2-демо
и поиска читеров). Корень репозитория = корень Gradle-проекта. Этот файл —
для агентов, работающих здесь.

## Структура репозитория

```
/demo-analysis-android
├── README.md                # обзор проекта, сборка, лицензии
├── SETUP.md                 # установка окружения (Rust, SDK, NDK, JDK 21)
├── .github/workflows/android.yml  # CI: APK debug+release (артефакты) + release на тегах
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── android/                 # модуль приложения (Kotlin)
│   ├── build.gradle.kts     # задача buildRust: cargo-ndk → src/main/jniLibs
│   └── src/main/java/dev/stast/demodetector/
│       ├── DemoAnalysis.kt  # external funs (JNI)
│       └── MainActivity.kt  # SAF-пикер, анализ на Dispatchers.IO, поллинг прогресса
└── rust/                    # cdylib crate demo-analysis-android (JNI-мост)
    ├── Cargo.toml           # git-зависимость demo-analysis (rev-пин!)
    ├── src/lib.rs           # весь JNI-слой
    └── stubs/{rfd,opener}/  # пустые стабы desktop-only зависимостей через [patch.crates-io]
```

## Зависимость demo-analysis

Код анализа **не вендорится**: `rust/Cargo.toml` подключает `demo-analysis`
git-зависимостью из
[eatthefreakingpaper/tf2-demo-player-aio](https://github.com/eatthefreakingpaper/tf2-demo-player-aio)
(пакет лежит в подкаталоге `demo-analysis/`, cargo находит его по имени),
rev-пин обязателен для воспроизводимости. Поднять пин = заменить `rev` в
`rust/Cargo.toml` + обновить `rust/Cargo.lock`.

Это расширенный форк https://github.com/Nocrex/demo-analysis (GPLv3): публичное
поле `CheatAnalyser.analyser`, дополненный `CheatAnalyserState`, наборы
алгоритмов `nocrex/` и `fidoo/`, система параметров.

## Что делает demo-analysis

Читает файл `.dem` (запись матча TF2), стримит его через `tf-demo-parser`,
строит на каждом тике `CheatAnalyserState` (игроки, постройки, оружие, снаряды,
состояния) и прогоняет набор алгоритмов-детекторов. На выходе — JSON со списком
`Detection { tick, algorithm, player (steamid64), data }`.

Ключевые точки входа (`demo-analysis/src/lib/algorithm.rs` в репо-зависимости):
- `get_algorithms()` — реестр всех алгоритмов.
- `analyse(&demo, algorithms, progress_cb)` — однопроходный анализ.
- `analyse_multithreaded(bytes, algorithms, threads, cb)` — по потоку на группу
  алгоритмов, каждый перечитывает демо заново.
- Трейт `CheatAlgorithm` — контракт алгоритма; `Detection` — результат.

Ядро состояния — `demo-analysis/src/base/cheat_analyser_base.rs`:
`CheatAnalyserState`, `CheatAnalyser` (реализует `MessageHandler` из
`tf-demo-parser`), `Player`, `Building`/`Sentry`/`Dispenser`/`Teleporter`,
`WeaponEntity`, `World`. `base/demo_handler_base.rs` — `CheatDemoHandler`,
гоняет пакеты в анализатор (поле `analyser` публичное — локальное отличие форка).

Исходники клона: `~/.cargo/git/checkouts/tf2-demo-player-aio-*/<rev>/demo-analysis/`.

## Алгоритмы

- Боевые (16): `viewangles_180degrees`, `angle_history`, `backtrack`, `double_tap`,
  `triggerbot`, `firewindow`, `recorder_aim_assist`, `nocrex/{aimsnap, angle_repeat,
  oob_pitch}`, `fidoo/{silent_aim, psilent4, nospread, auto_backstab, bunnyhop,
  invalid_equip_region}`. Пример с комментариями — `viewangles_180degrees.rs`.
- Dev (3): `all_messages`, `write_to_file`, `viewangles_to_csv` — пишут в
  `./output` и паникуют в `init()` при неудаче. На Android отсекаются списком
  `DEV_ALGORITHMS` в `rust/src/lib.rs`, в UI не показываются.

## Android-слой

- `rust/src/lib.rs` — JNI-мост: `version`, `algorithmsJson` (схема алгоритмов +
  параметров для UI), `analyse(fd, algorithms, configJson, threads)`,
  `progressCurrent/Total/resetProgress` (глобальные атомики ядра).
- Владение fd: Kotlin делает `detachFd()`, Rust принимает `File::from_raw_fd`
  **первым действием** и закрывает сам на любом пути (не закрывать дважды).
- Паники ядра ловятся `catch_unwind` → Java `RuntimeException`.
- JSON результата повторяет формат CLI `print_detection_json` (без `println!`,
  из публичных полей `CheatAnalyser`).
- Kotlin: `DemoAnalysis.kt` (external funs), `MainActivity.kt` (SAF-пикер,
  `Dispatchers.IO`, поллинг прогресса 4 раза/с, шаринг JSON).
- Release-сборка подписывается debug-ключом (`signingConfig = debug`) — чтобы
  CI-артефакт был ставибельным; это не релиз для стора.

## Сборка

Требования: Rust + таргеты `aarch64-linux-android`/`x86_64-linux-android`,
`cargo-ndk`, Android SDK + NDK 27.0.12077973, **JDK 21** (Java 27 Gradle 8.x
не берёт; на машине JDK в `~/tools/jdk-21.0.12.1+1`, SDK в
`~/Проекты/android-sdk`, экспорты уже в `~/.bashrc`).

```bash
export JAVA_HOME="$HOME/tools/jdk-21.0.12.1+1"
export ANDROID_HOME="$HOME/Проекты/android-sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.0.12077973"
./gradlew :android:assembleDebug :android:assembleRelease
```

- Задача `:android:buildRust` вызывает `cargo-ndk` (arm64-v8a + x86_64,
  `--platform 26`) и кладёт `.so` в `android/src/main/jniLibs` (в git не входит).
- Быстрая проверка ядра без устройства: `cargo check` в `rust/` (нужна сеть —
  git-зависимость). JNI-смок-тест под host JVM — см. историю: класс с
  native-методами `dev.stast.demodetector.DemoAnalysis`,
  `rust/target/debug/libdemo_analysis_android.so`.
- CI делает то же: `.github/workflows/android.yml` (ubuntu-latest, temurin 21,
  ndk 27, cargo-ndk из taiki-e/install-action).

## Платформенные ограничения (важно)

- `tf-demo-parser` держит демо целиком в памяти — демо бывают сотни МБ (риск OOM).
- `analyse_multithreaded`: каждый воркер хранит своё состояние; на мобиле
  `threads` ограничивать (сейчас из Kotlin передаётся 2).
- Git-зависимость требует сети при первой сборке; Cargo.lock пинует всё
  транзитивно, но ревизию форка пинуем вручную через `rev`.

## Лицензия

`demo-analysis` — GPLv3, остальное — MIT. Комбинированная работа: при
распространении APK действует GPLv3.

## Правила работы

- Патчи форка demo-analysis — только через отдельный fork-репозиторий, не локально.
- Комментарии в стиле репозитория (английский, содержательные).
- После правок Rust: `cargo check` в `rust/`; после правок Kotlin:
  `./gradlew :android:assembleDebug` должен проходить.
