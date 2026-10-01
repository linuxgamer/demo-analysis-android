# AGENTS.md

Цель проекта: портировать **demo-analysis** (Rust-крейт для анализа TF2-демо и
поиска читеров) на Android. Этот файл — для агентов, работающих в каталоге
Android-проекта `app/`.

## Структура репозитория

```
/android-demo-detector
├── README.md                # обзор проекта, сборка, лицензии
├── .github/workflows/android.yml  # CI: сборка APK (артефакт) + release на тегах
├── app/                     # Gradle-проект Android  <-- этот каталог
│   ├── android/             # модуль приложения (Kotlin + JNI-обёртки)
│   │   └── build.gradle.kts # задача buildRust: cargo-ndk → src/main/jniLibs
│   ├── rust/                # cdylib crate demo-analysis-android (JNI-мост)
│   │   ├── src/lib.rs
│   │   └── stubs/{rfd,opener}/  # пустые стабы desktop-only зависимостей
│   └── SETUP.md             # установка окружения (Rust, SDK, NDK, JDK 21)
└── tf2-demo-player-aio-main/ # исходники upstream, взятые как есть
    ├── Cargo.toml           # GUI-приложение tf2-demo-player (relm4/GTK4) — НЕ цель порта
    ├── demo-analysis/       # ЯДРО ПОРТА. Библиотека анализа + CLI + egui-GUI
    └── ...                  # (пути ниже указаны от корня репозитория)
```

`demo-analysis` — vendored-копия https://github.com/Nocrex/demo-analysis
(GPLv3, путь `../tf2-demo-player-aio-main/demo-analysis`). Подключается в
`app/rust` как path-зависимость; **upstream не изменять** — расхождения с
Android решаются в `app/rust` (пример: стабы `rfd`/`opener` через
`[patch.crates-io]`).

## Что делает demo-analysis

Читает файл `.dem` (запись матча TF2), стримит его через `tf-demo-parser`,
строит на каждом тике `CheatAnalyserState` (игроки, постройки, оружие, снаряды,
состояния) и прогоняет набор алгоритмов-детекторов. На выходе — JSON со списком
`Detection { tick, algorithm, player (steamid64), data }`.

Ключевые точки входа (`demo-analysis/src/lib/algorithm.rs`):
- `get_algorithms()` — реестр всех алгоритмов (`lib/algorithm.rs:40`).
- `analyse(&demo, algorithms, progress_cb)` — однопроходный анализ (`lib/algorithm.rs:157`).
- `analyse_multithreaded(bytes, algorithms, threads, cb)` — по потоку на группу
  алгоритмов, каждый перечитывает демо заново (`lib/algorithm.rs:196`).
- Трейт `CheatAlgorithm` — контракт алгоритма (`lib/algorithm.rs:247`).
- `Detection` — сериализуемая структура результата (`lib/algorithm.rs:299`).

Ядро состояния — `base/cheat_analyser_base.rs`:
- `CheatAnalyserState` (`:384`) и `CheatAnalyser` (`:504`) — реализует
  `MessageHandler` из `tf-demo-parser`, наполняется в `handle_message`/`handle_entity`.
- `Player` (`:58`), `Building`/`Sentry`/`Dispenser`/`Teleporter`, `WeaponEntity`, `World`.

`base/demo_handler_base.rs` — `CheatDemoHandler`, обёртка над парсером, гоняет
пакеты в анализатор (важно: в отличие от upstream, поле `analyser` публичное).

## Алгоритмы

Все в `../tf2-demo-player-aio-main/demo-analysis/src/algorithms/`. Именование
по авторам: `nocrex/` и `fidoo/`. Базовый пример с комментариями —
`viewangles_180degrees.rs`. Dev-алгоритмы (пишут файлы, на Android запрещены):
`all_messages.rs`, `write_to_file.rs`, `viewangles_to_csv.rs` — отсекаются
в `app/rust/src/lib.rs` списком `DEV_ALGORITHMS`.

## Android-слой (этот каталог)

- `rust/src/lib.rs` — весь JNI-мост: `version`, `algorithmsJson` (схема
  алгоритмов + параметров для UI), `analyse(fd, algorithms, config, threads)`,
  `progressCurrent/Total/resetProgress` (глобальные атомики ядра).
- Владение fd: Kotlin делает `detachFd()`, Rust принимает `File::from_raw_fd`
  **первым действием** и закрывает сам на любом пути (не закрывать дважды).
- Паники ядра ловятся `catch_unwind` → Java `RuntimeException`.
- JSON результата повторяет формат CLI `print_detection_json` (без `println!`,
  из публичных полей `CheatAnalyser`).
- Kotlin: `DemoAnalysis.kt` (external funs), `MainActivity.kt` (SAF-пикер,
  анализ на `Dispatchers.IO`, поллинг прогресса 4 раза/с, шаринг JSON).

## Сборка Android

Требования: Rust + таргеты `aarch64-linux-android`/`x86_64-linux-android`,
`cargo-ndk`, Android SDK + NDK 27.0.12077973, **JDK 21** (системная Java 27
Gradle 8.x не подходит; на машине JDK в `~/tools/jdk-21.0.12.1+1`, Gradle в
`~/tools/gradle-8.11.1`, SDK в `~/Проекты/android-sdk`).

```bash
export JAVA_HOME="$HOME/tools/jdk-21.0.12.1+1"
export ANDROID_HOME="$HOME/Проекты/android-sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.0.12077973"
cd app && ./gradlew :android:assembleDebug
```

- Задача `:android:buildRust` вызывает `cargo-ndk` (arm64-v8a + x86_64,
  `--platform 26`) и кладёт `.so` в `android/src/main/jniLibs` (в git не входит).
- Быстрая проверка ядра без устройства: `cargo check` в `app/rust` (host),
  JNI-смок-тест — запуск `app/rust/target/debug/libdemo_analysis_android.so`
  под host JVM (см. историю: класс с native-методами `dev.stast.demodetector.DemoAnalysis`).
- CI собирает то же самое на ubuntu-latest: `.github/workflows/android.yml`.

## Платформенные ограничения (важно)

- `tf-demo-parser` держит демо целиком в памяти — демо бывают сотни МБ (риск OOM).
- `analyse_multithreaded`: каждый воркер хранит своё состояние; на мобиле
  `threads` ограничивать (сейчас из Kotlin передаётся 2).
- Dev-алгоритмы пишут в `./output` и паникуют в `init()` при неудаче — на
  Android их нельзя выбирать.

## Сборка desktop (для справки)

- CLI: `cargo run --release -- -i "path/to/demo.dem"` (флаги: `-a`, `-q`, `-Q`, `-c`, `-p`).
- GUI (egui): `cargo build --release --bin gui --features gui`.
- Тесты: `cargo test` (из `../tf2-demo-player-aio-main/demo-analysis/`).

## Лицензия

`demo-analysis` — GPLv3, остальное — MIT. Комбинированная работа: при
распространении APK действует GPLv3.

## Правила работы

- Не менять исходники в `../tf2-demo-player-aio-main/` без явной необходимости.
- Комментарии в стиле репозитория (английский, содержательные).
- После правок Rust: `cargo check` в `app/rust`; после правок Kotlin:
  `./gradlew :android:assembleDebug` должен проходить.
