# Настройка окружения (Rust + Android SDK)

Ничего из этого пока нет на машине: `cargo`/`rustc` отсутствуют, Android SDK
не установлен, есть только JDK (`/usr/lib/jvm/java-27-openjdk`) и `adb`.

## 1. Rust + cargo-ndk

```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
source "$HOME/.cargo/env"
rustup target add aarch64-linux-android x86_64-linux-android
cargo install cargo-ndk
```

## 2. Android SDK (cmdline-tools, без Android Studio)

```bash
mkdir -p "$HOME/Android/Sdk/cmdline-tools"
cd "$HOME/Android/Sdk/cmdline-tools"
# актуальную версию commandlinetools-linux можно взять со страницы
# https://developer.android.com/studio#command-line-tools-only
unzip ~/Downloads/commandlinetools-linux-*.zip
mv cmdline-tools latest

yes | "$HOME/Android/Sdk/cmdline-tools/latest/bin/sdkmanager" --licenses
"$HOME/Android/Sdk/cmdline-tools/latest/bin/sdkmanager" \
    "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;27.0.12077973"
```

## 3. Переменные окружения (~/.bashrc или ~/.profile)

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.0.12077973"
export JAVA_HOME="/usr/lib/jvm/java-27-openjdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
```

JDK 27 для AGP 8.7 может оказаться слишком новым (поддерживается до JDK 21/22
в зависимости от версии Gradle). Если Gradle упадёт на версии Java — поставьте
`openjdk-21-jdk` и укажите его в `JAVA_HOME`/`org.gradle.java.home`.

## 4. Gradle wrapper

В `app/` нет gradle wrapper (нужен установленный gradle или сгенерировать
wrapper: `gradle wrapper --gradle-version 8.9` из каталога `app/`).

## 5. Сборка

```bash
cd app
./gradlew :android:assembleDebug     # соберёт Rust через cargo-ndk и APK
adb install android/build/outputs/apk/debug/android-debug.apk
```

Результат анализа и прогресс видны на экране; полный JSON — через «Share JSON».

## Проверка без устройства

```bash
"$ANDROID_HOME/emulator/emulator" -list-avds   # после создания AVD
```

Либо сразу на телефоне с включённой отладкой по USB: `adb devices`.
