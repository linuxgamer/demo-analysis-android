# Настройка окружения / Environment setup

Проверенный набор на этой машине (октябрь 2026): rustup (pacman) + stable,
Android SDK в `~/Проекты/android-sdk`, JDK 21 и Gradle в `~/tools/`.
Один нюанс: кириллица в путях ломает Java-properties (`gradle.properties`,
`local.properties`) — JDK/Gradle лежат в `~/tools`, `org.gradle.java.home`
не используется, JDK задаётся через `JAVA_HOME`.
/ Verified set on this machine (October 2026): rustup (pacman) + stable,
Android SDK in `~/Проекты/android-sdk`, JDK 21 and Gradle in `~/tools/`.
One caveat: non-ASCII paths break Java properties files (`gradle.properties`,
`local.properties`) — keep JDK/Gradle under an ASCII path, don't use
`org.gradle.java.home`, pass the JDK via `JAVA_HOME`.

## 1. Rust + Android-таргеты / Rust + Android targets

```bash
sudo pacman -S rustup          # или официальный установщик rustup.rs / or rustup.rs installer
rustup default stable
rustup target add aarch64-linux-android x86_64-linux-android
cargo install cargo-ndk        # попадёт в ~/.cargo/bin / lands in ~/.cargo/bin
```

Системный `cargo`/`rustc` из pacman можно оставить — шимы rustup в
`~/.cargo/bin` перекроют их, если `~/.cargo/bin` в PATH.
/ Distro `cargo`/`rustc` can stay — rustup shims in `~/.cargo/bin` shadow them
as long as `~/.cargo/bin` is in PATH.

## 2. Android SDK (cmdline-tools)

```bash
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
unzip ~/Downloads/commandlinetools-linux-*.zip
mv cmdline-tools latest        # обязательно latest/, иначе sdkmanager не найдёт SDK root
                               # must be latest/ or sdkmanager won't find the SDK root

yes | latest/bin/sdkmanager --licenses
latest/bin/sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;27.0.12077973"
```

## 3. JDK и Gradle / JDK and Gradle

Gradle 8.x не запускается на свежих JDK (27), нужен 21:
/ Gradle 8.x refuses the newest JDKs (27); use 21:

```bash
mkdir -p ~/tools && cd ~/tools
# Temurin 21: https://adoptium.net (tar.gz) и распаковать / download and unpack
curl -sL -o jdk21.tar.gz "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
tar xzf jdk21.tar.gz && rm jdk21.tar.gz

curl -sL -o gradle.zip "https://services.gradle.org/distributions/gradle-8.11.1-bin.zip"
unzip -q gradle.zip && rm gradle.zip

# wrapper генерируется один раз (уже лежит в корне, пересоздавать не нужно)
# the wrapper is generated once (already committed; no need to regenerate)
# JAVA_HOME=$HOME/tools/jdk-21.0.12.1+1 ~/tools/gradle-8.11.1/bin/gradle wrapper
```

## 4. Переменные окружения / Environment variables (~/.bashrc)

```bash
export ANDROID_HOME="$HOME/android-sdk"      # или / or ~/Проекты/android-sdk
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.0.12077973"
export JAVA_HOME="$HOME/tools/jdk-21.0.12.1+1"
export PATH="$HOME/.cargo/bin:$PATH"
```

## 5. Сборка / Build

```bash
./gradlew :android:assembleDebug :android:assembleRelease
adb install android/build/outputs/apk/debug/android-debug.apk
```

## Быстрая проверка без телефона / Quick check without a phone

JNI-слой можно гонять под host JVM: `cargo build` в `rust/` и вызвать
`libdemo_analysis_android.so` из Java-класса с теми же native-методами
(`dev.stast.demodetector.DemoAnalysis`) — см. AGENTS.md.
/ The JNI layer runs under a host JVM: `cargo build` in `rust/`, then load
`libdemo_analysis_android.so` from a Java class declaring the same native
methods (`dev.stast.demodetector.DemoAnalysis`) — see AGENTS.md.
