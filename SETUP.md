# Environment setup

Verified set on this machine (October 2026): rustup (pacman) + stable,
Android SDK in `~/Проекты/android-sdk`, JDK 21 and Gradle in `~/tools/`.
One caveat: non-ASCII paths break Java properties files (`gradle.properties`,
`local.properties`) — keep JDK/Gradle under an ASCII path, don't use
`org.gradle.java.home`, pass the JDK via `JAVA_HOME`.

## 1. Rust + Android targets

```bash
sudo pacman -S rustup          # or the rustup.rs installer
rustup default stable
rustup target add armv7-linux-androideabi aarch64-linux-android i686-linux-android x86_64-linux-android
cargo install cargo-ndk        # lands in ~/.cargo/bin
```

Distro `cargo`/`rustc` can stay — rustup shims in `~/.cargo/bin` shadow them
as long as `~/.cargo/bin` is in PATH.

## 2. Android SDK (cmdline-tools)

```bash
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
unzip ~/Downloads/commandlinetools-linux-*.zip
mv cmdline-tools latest        # must be latest/ or sdkmanager won't find the SDK root

yes | latest/bin/sdkmanager --licenses
latest/bin/sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;27.0.12077973"
```

## 3. JDK and Gradle

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

## 4. Environment variables (~/.bashrc)

```bash
export ANDROID_HOME="$HOME/android-sdk"      # or ~/Проекты/android-sdk
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.0.12077973"
export JAVA_HOME="$HOME/tools/jdk-21.0.12.1+1"
export PATH="$HOME/.cargo/bin:$PATH"
```

## 5. Build

```bash
./gradlew :android:assembleDebug :android:assembleRelease
adb install android/build/outputs/apk/debug/demo-analysis-android-debug.apk
```

## 6. Uninstall everything

If the toolchain is no longer needed, remove the components in reverse order.

### Gradle caches and build artifacts

```bash
rm -rf ~/.gradle                                  # wrapper + dependency caches (~1-2 GB)
```

### JDK and Gradle from ~/tools

```bash
rm -rf ~/tools/jdk-21.0.12.1+1 ~/tools/gradle-8.11.1
rmdir ~/tools 2>/dev/null
```

### Android SDK

```bash
rm -rf ~/android-sdk                              # or wherever ANDROID_HOME points (~3-5 GB with NDK)
```

### Rust (rustup)

```bash
rustup self uninstall                             # asks for confirmation; removes ~/.rustup
# ~/.cargo may still hold cargo-installed tools and the cargo-ndk binary:
cargo uninstall cargo-ndk                         # if you still need it elsewhere, skip this
rm -rf ~/.cargo ~/.rustup
sudo pacman -Rns rustup 2>/dev/null               # the pacman-managed rustup, if installed
```

Note: `rustup self uninstall` leaves `~/.cargo/bin` contents (cargo-ndk and
anything else you `cargo install`ed) behind; only the manual `rm -rf`
removes the whole directory.

### Lines from ~/.bashrc

Remove the block added during setup (the `ANDROID_HOME`, `ANDROID_NDK_HOME`,
`JAVA_HOME` exports and the `~/.cargo/bin` PATH entry):

```bash
sed -i '/# Android SDK + Rust (android-demo-detector)/,+4d' ~/.bashrc
```

### Device

```bash
adb uninstall dev.godetect.tf2demo                # if the APK was installed
```

### Verify everything is gone

```bash
which cargo rustup sdkmanager adb gradle          # should all be silent
ls ~/.cargo ~/.rustup ~/android-sdk ~/tools ~/.gradle 2>&1   # all "No such file"
```

## Quick check without a phone

The JNI layer runs under a host JVM: `cargo build` in `rust/`, then load
`libdemo_analysis_android.so` from a Java class declaring the same native
methods (`dev.godetect.tf2demo.DemoAnalysis`) — see AGENTS.md.
