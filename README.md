# android-demo-detector

Порт [demo-analysis](https://github.com/Nocrex/demo-analysis) — анализатора демо-записей
Team Fortress 2 с поиском читеров — на Android.

Rust-ядро (`tf-demo-parser` + 16 алгоритмов-детекторов) собирается в `.so` через
`cargo-ndk`, Android-приложение (Kotlin) выбирает `.dem` файл через SAF, прогоняет
анализ и показывает детекции. JSON на выходе бит-в-байт совпадает с десктопным CLI.

## Возможности

- 16 боевых алгоритмов из upstream (`aimsnap`, `silent_aim`, `backtrack`, `nospread`,
  `psilent`, `bunnyhop`, `auto_backstab`, `180 flips` и др.), dev-алгоритмы исключены
- Анализ в 1–N потоков (`analyse_multithreaded`, каждый воркер перечитывает демо)
- Прогресс в реальном времени (поллинг атомиков из Rust-ядра, 4 опроса/с)
- Результат — JSON формата desktop-CLI (`{server_ip, duration, author, map, detections}`),
  шаринг через Android intent
- Демо передаётся в Rust через file descriptor (одна копия файла в памяти)

## Структура

```
/app                       # Gradle-проект Android
├── android/               # модуль приложения (Kotlin)
├── rust/                  # cdylib crate: JNI-мост над demo-analysis
│   └── stubs/             # пустые стабы rfd/opener (desktop-only зависимости upstream)
└── SETUP.md               # настройка окружения для локальной сборки
/tf2-demo-player-aio-main  # vendored upstream (демо-плеер + demo-analysis, не изменяется)
```

## Сборка

### Артефакты CI

Каждый пуш в `main` собирает debug-APK — качается со страницы Actions запуска
(раздел Artifacts). Тег `v*` дополнительно прикладывает APK к GitHub Release.

### Локально

Требования: Rust + `rustup target add aarch64-linux-android x86_64-linux-android`,
`cargo-ndk`, Android SDK + NDK 27, JDK 21 (Gradle 8.x не дружит с Java 27).

```bash
export ANDROID_HOME=~/android-sdk
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.0.12077973"
cd app
./gradlew :android:assembleDebug
adb install android/build/outputs/apk/debug/android-debug.apk
```

Подробности окружения — [app/SETUP.md](app/SETUP.md).

## Как это работает

1. Kotlin открывает `.dem` через SAF (`ACTION_OPEN_DOCUMENT`), передаёт в Rust
   отсоединённый file descriptor — Rust принимает владение и закрывает его сам.
2. Rust читает демо в память, фильтрует алгоритмы (dev-список отсекается всегда),
   применяет JSON-конфиг параметров (`normalize_config` + `apply_config`).
3. `analyse_multithreaded` гонит анализ; паники ловятся `catch_unwind` и отдаются
   в Kotlin как `RuntimeException`, а не роняют процесс.
4. Результат сериализуется в JSON (тот же формат, что `print_detection_json` CLI)
   и возвращается строкой.

## Лицензии

- `demo-analysis` — GPLv3
- остальное (`tf2-demo-player`) — MIT

Комбинированная работа: при распространении APK применяется GPLv3 (код demo-analysis
компилируется прямо в бинарник).
