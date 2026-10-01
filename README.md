# demo-analysis-android

Android-порт [demo-analysis](https://github.com/Nocrex/demo-analysis) — анализатора
демо-записей Team Fortress 2 с поиском читеров.

Rust-ядро (парсер демо + 16 алгоритмов-детекторов) собирается в `.so` через
`cargo-ndk`; Kotlin-приложение выбирает `.dem` файл через SAF, прогоняет анализ
и показывает детекции. JSON на выходе бит-в-байт совпадает с десктопным CLI.

## Возможности

- 16 боевых алгоритмов (`aimsnap`, `silent_aim`, `backtrack`, `nospread`, `psilent`,
  `bunnyhop`, `auto_backstab`, `180 flips` и др.); dev-алгоритмы (пишут файлы) исключены
- Анализ в 1–N потоков (каждый воркер перечитывает демо)
- Прогресс в реальном времени (поллинг атомиков из Rust-ядра, 4 опроса/с)
- Результат — JSON формата desktop-CLI (`{server_ip, duration, author, map, detections}`),
  шаринг через Android intent
- Демо передаётся в Rust через file descriptor (одна копия файла в памяти)

## Скачивание

Зайди в [Actions](../../actions/latest), открой последний запуск `android` и скачай
артефакт **demo-analysis-android-apks** — внутри `android-debug.apk` и
подписанный `android-release.apk` (оба ставятся на устройство). На тегах `v*`
те же APK прикладываются к [Releases](../../releases).

## Структура

```
/                          # корень = Gradle-проект
├── android/               # модуль приложения (Kotlin)
├── rust/                  # cdylib crate: JNI-мост над demo-analysis
│   └── stubs/             # пустые стабы rfd/opener (desktop-only зависимости)
└── SETUP.md               # настройка окружения для локальной сборки
```

Код анализа не вендорится: `rust/Cargo.toml` подключает `demo-analysis`
git-зависимостью из [tf2-demo-player-aio](https://github.com/eatthefreakingpaper/tf2-demo-player-aio)
с пином коммита.

## Сборка

```bash
export ANDROID_HOME=~/android-sdk
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.0.12077973"
export JAVA_HOME=~/tools/jdk-21.0.12.1+1   # Gradle 8.x не дружит с Java 27
./gradlew :android:assembleDebug :android:assembleRelease
```

Подробности окружения — [SETUP.md](SETUP.md). CI делает то же самое:
`.github/workflows/android.yml`.

## Как это работает

1. Kotlin открывает `.dem` через SAF (`ACTION_OPEN_DOCUMENT`), передаёт в Rust
   отсоединённый file descriptor — Rust принимает владение и закрывает его сам.
2. Rust читает демо в память, отсекает dev-алгоритмы, применяет JSON-конфиг
   параметров (`normalize_config` + `apply_config`).
3. `analyse_multithreaded` гонит анализ; паники ловятся `catch_unwind` и отдаются
   в Kotlin как `RuntimeException`, а не роняют процесс.
4. Результат сериализуется в JSON (формат `print_detection_json` CLI) и
   возвращается строкой.

## Credits

- [demo-analysis](https://github.com/Nocrex/demo-analysis) — ядро анализа, авторы
  алгоритмов: Nocrex, fidoo, teltta (GPLv3)
- [tf2-demo-player-aio](https://github.com/eatthefreakingpaper/tf2-demo-player-aio) —
  расширенный форк demo-analysis, используемый как git-зависимость
- [tf-demo-parser](https://github.com/demostf/parser) — парсер демо (MIT)

## Лицензии

`demo-analysis` — GPLv3, остальное — MIT. Комбинированная работа: при
распространении APK применяется GPLv3 (код demo-analysis компилируется прямо
в бинарник).
