# Настройка окружения (Rust + Android SDK)

Проверенный набор на этой машине (октябрь 2026): rustup (pacman) + stable,
Android SDK в `~/Проекты/android-sdk`, JDK 21 и Gradle в `~/tools/`.
Один нюанс: кириллица в путях ломает Java-properties (`gradle.properties`,
`local.properties`) — JDK/Gradle лежат в `~/tools`, `org.gradle.java.home`
не используется, JDK задаётся через `JAVA_HOME`.

## 1. Rust + Android-таргеты

```bash
sudo pacman -S rustup          # или официальный установщик rustup.rs
rustup default stable
rustup target add aarch64-linux-android x86_64-linux-android
cargo install cargo-ndk        # попадёт в ~/.cargo/bin
```

Системный `cargo`/`rustc` из pacman можно оставить — шимы rustup в
`~/.cargo/bin` перекроют их, если `~/.cargo/bin` в PATH.

## 2. Android SDK (cmdline-tools)

```bash
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
unzip ~/Downloads/commandlinetools-linux-*.zip
mv cmdline-tools latest        # обязательно latest/, иначе sdkmanager не найдёт SDK root

yes | latest/bin/sdkmanager --licenses
latest/bin/sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;27.0.12077973"
```

## 3. JDK и Gradle

Gradle 8.x не запускается на свежих JDK (27), нужен 21:

```bash
mkdir -p ~/tools && cd ~/tools
# Temurin 21: https://adoptium.net (tar.gz) и распаковать
curl -sL -o jdk21.tar.gz "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
tar xzf jdk21.tar.gz && rm jdk21.tar.gz

curl -sL -o gradle.zip "https://services.gradle.org/distributions/gradle-8.11.1-bin.zip"
unzip -q gradle.zip && rm gradle.zip

# wrapper генерируется один раз (уже лежит в app/, пересоздавать не нужно)
# JAVA_HOME=$HOME/tools/jdk-21.0.12.1+1 ~/tools/gradle-8.11.1/bin/gradle wrapper
```

## 4. Переменные окружения (~/.bashrc)

```bash
export ANDROID_HOME="$HOME/android-sdk"      # или ~/Проекты/android-sdk
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.0.12077973"
export JAVA_HOME="$HOME/tools/jdk-21.0.12.1+1"
export PATH="$HOME/.cargo/bin:$PATH"
```

## 5. Сборка

```bash
cd app
./gradlew :android:assembleDebug
adb install android/build/outputs/apk/debug/android-debug.apk
```

## Быстрая проверка без телефона

JNI-слой можно гонять под host JVM: `cargo build` в `app/rust` и вызвать
`libdemo_analysis_android.so` из Java-класса с теми же native-методами
(`dev.stast.demodetector.DemoAnalysis`) — см. AGENTS.md.
