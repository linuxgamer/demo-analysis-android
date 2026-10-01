# Environment setup / Настройка окружения

## Part 1 — English

Verified set on this machine (October 2026): rustup (pacman) + stable, Android
SDK in `~/Проекты/android-sdk`, JDK 21 and Gradle in `~/tools/`. One caveat:
non-ASCII paths break Java properties files (`gradle.properties`,
`local.properties`) — keep JDK/Gradle under an ASCII path, don't use
`org.gradle.java.home`, pass the JDK via `JAVA_HOME`.

### 1. Rust + Android targets

```bash
sudo pacman -S rustup          # or the rustup.rs installer
rustup default stable
rustup target add armv7-linux-androideabi aarch64-linux-android x86_64-linux-android
cargo install cargo-ndk        # lands in ~/.cargo/bin
```

Distro `cargo`/`rustc` can stay — rustup shims in `~/.cargo/bin` shadow them
as long as `~/.cargo/bin` is in PATH.

### 2. Android SDK (cmdline-tools)

```bash
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
unzip ~/Downloads/commandlinetools-linux-*.zip
mv cmdline-tools latest        # must be latest/ or sdkmanager won't find the SDK root

yes | latest/bin/sdkmanager --licenses
latest/bin/sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;27.0.12077973"
```

### 3. JDK and Gradle

Gradle 8.x refuses the newest JDKs (27); use 21:

```bash
mkdir -p ~/tools && cd ~/tools
# Temurin 21: https://adoptium.net — download the tar.gz and unpack
curl -sL -o jdk21.tar.gz "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
tar xzf jdk21.tar.gz && rm jdk21.tar.gz

curl -sL -o gradle.zip "https://services.gradle.org/distributions/gradle-8.11.1-bin.zip"
unzip -q gradle.zip && rm gradle.zip

# The wrapper is generated once (already committed; no need to regenerate):
# JAVA_HOME=$HOME/tools/jdk-21.0.12.1+1 ~/tools/gradle-8.11.1/bin/gradle wrapper
```

### 4. Environment variables (~/.bashrc)

```bash
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.0.12077973"
export JAVA_HOME="$HOME/tools/jdk-21.0.12.1+1"
export PATH="$HOME/.cargo/bin:$PATH"
```

### 5. Build

```bash
./gradlew :android:assembleDebug :android:assembleRelease
adb install android/build/outputs/apk/debug/demo-analysis-android-debug.apk
```

### Quick check without a phone

The JNI layer runs under a host JVM: `cargo build` in `rust/`, then load
`libdemo_analysis_android.so` from a Java class declaring the same native
methods (`dev.stast.demodetector.DemoAnalysis`) — see AGENTS.md.

---

## Часть 2 — Русский

Проверенный набор на этой машине (октябрь 2026): rustup (pacman) + stable,
Android SDK в `~/Проекты/android-sdk`, JDK 21 и Gradle в `~/tools/`. Один
нюанс: кириллица в путях ломает Java-properties (`gradle.properties`,
`local.properties`) — JDK/Gradle лежат в `~/tools`, `org.gradle.java.home` не
используется, JDK задаётся через `JAVA_HOME`.

### 1. Rust + Android-таргеты

```bash
sudo pacman -S rustup          # или официальный установщик rustup.rs
rustup default stable
rustup target add armv7-linux-androideabi aarch64-linux-android x86_64-linux-android
cargo install cargo-ndk        # попадёт в ~/.cargo/bin
```

Системный `cargo`/`rustc` из pacman можно оставить — шимы rustup в
`~/.cargo/bin` перекроют их, если `~/.cargo/bin` в PATH.

### 2. Android SDK (cmdline-tools)

```bash
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
unzip ~/Downloads/commandlinetools-linux-*.zip
mv cmdline-tools latest        # обязательно latest/, иначе sdkmanager не найдёт SDK root

yes | latest/bin/sdkmanager --licenses
latest/bin/sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;27.0.12077973"
```

### 3. JDK и Gradle

Gradle 8.x не запускается на свежих JDK (27), нужен 21:

```bash
mkdir -p ~/tools && cd ~/tools
# Temurin 21: https://adoptium.net — скачать tar.gz и распаковать
curl -sL -o jdk21.tar.gz "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
tar xzf jdk21.tar.gz && rm jdk21.tar.gz

curl -sL -o gradle.zip "https://services.gradle.org/distributions/gradle-8.11.1-bin.zip"
unzip -q gradle.zip && rm gradle.zip

# Wrapper генерируется один раз (уже лежит в корне, пересоздавать не нужно):
# JAVA_HOME=$HOME/tools/jdk-21.0.12.1+1 ~/tools/gradle-8.11.1/bin/gradle wrapper
```

### 4. Переменные окружения (~/.bashrc)

```bash
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.0.12077973"
export JAVA_HOME="$HOME/tools/jdk-21.0.12.1+1"
export PATH="$HOME/.cargo/bin:$PATH"
```

### 5. Сборка

```bash
./gradlew :android:assembleDebug :android:assembleRelease
adb install android/build/outputs/apk/debug/demo-analysis-android-debug.apk
```

### Быстрая проверка без телефона

JNI-слой можно гонять под host JVM: `cargo build` в `rust/` и вызвать
`libdemo_analysis_android.so` из Java-класса с теми же native-методами
(`dev.stast.demodetector.DemoAnalysis`) — см. AGENTS.md.

---

## Часть 3 — Удаление всего установленного / Uninstall

Если окружение больше не нужно, удалите компоненты в обратном порядке.
/ If the toolchain is no longer needed, remove the components in reverse order.

### 1. Gradle кэши и сборочные артефакты / Gradle caches and build artifacts

```bash
rm -rf ~/.gradle                                  # кэши wrapper'ов и зависимостей (~1-2 ГБ)
```

### 2. JDK и Gradle из ~/tools / JDK and Gradle from ~/tools

```bash
rm -rf ~/tools/jdk-21.0.12.1+1 ~/tools/gradle-8.11.1
rmdir ~/tools 2>/dev/null
```

### 3. Android SDK

```bash
rm -rf ~/android-sdk                              # или ~/Проекты/android-sdk (~3-5 ГБ с NDK)
```

### 4. Rust (rustup)

```bash
rustup self uninstall                             # спросит подтверждение; снесёт ~/.rustup и ~/.cargo
# если ставился через pacman и ~/.cargo остался:
rm -rf ~/.cargo ~/.rustup
sudo pacman -Rns rustup cargo-ndk 2>/dev/null     # пакетные версии, если ставились
```

Внимание: `~/.cargo/bin` содержит и другие установленные через
`cargo install` инструменты (например `cargo-ndk`) — `rustup self uninstall`
их не трогает, каталог удаляется целиком только вручную. Если
`cargo-ndk` нужен был для других проектов — сначала `cargo uninstall cargo-ndk`.
/ Note: `~/.cargo/bin` also holds other `cargo install`ed tools (e.g.
`cargo-ndk`). `rustup self uninstall` leaves them; only a manual `rm -rf`
removes the whole directory. If you still need `cargo-ndk` for other
projects, run `cargo uninstall cargo-ndk` first.

### 5. Строки из ~/.bashrc / Remove lines from ~/.bashrc

Удалите блок, добавленный при настройке (экспорты `ANDROID_HOME`,
`ANDROID_NDK_HOME`, `JAVA_HOME`, `PATH` с `.cargo/bin`):
/ Remove the block added during setup (the `ANDROID_HOME`, `ANDROID_NDK_HOME`,
`JAVA_HOME` exports and the `~/.cargo/bin` PATH entry):

```bash
sed -i '/# Android SDK + Rust (android-demo-detector)/,+4d' ~/.bashrc
```

### 6. Устройство / Device

```bash
adb uninstall dev.stast.demodetector              # если APK ставился на телефон/эмулятор
```

### Проверка, что всё чисто / Verify everything is gone

```bash
which cargo rustup sdkmanager adb gradle          # всё должно молчать / should all be silent
ls ~/.cargo ~/.rustup ~/android-sdk ~/tools ~/.gradle 2>&1   # всё «Нет такого файла» / all "No such file"
```
