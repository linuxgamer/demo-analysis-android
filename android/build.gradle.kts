plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.godetect.tf2demo"
    compileSdk = 35

    // APK names: demo-analysis-android-<buildType>.apk instead of android-<buildType>.apk.
    base {
        archivesName.set("demo-analysis-android")
    }

    signingConfigs {
        // The keystore is committed (standard Android debug credentials:
        // password "android", alias "androiddebugkey") so that CI builds and
        // local builds share one signature and update over each other without
        // uninstalling. Never use this key for store publishing.
        getByName("debug") {
            storeFile = rootProject.file("signing/debug.keystore")
        }
    }

    defaultConfig {
        applicationId = "dev.godetect.tf2demo"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "1.0"
        ndk {
            // Universal APK: both ARM flavors, 32-bit and 64-bit x86.
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildTypes {
        release {
            // R8 shrinks/dexes the Kotlin side; the proguard rules keep the
            // JNI entry points and the org.json reflection paths alive.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Signed with the debug key so CI-produced release APKs are
            // installable; no secrets to distribute, this build is not meant
            // for store publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

// Cross-compiles the Rust JNI library into src/main/jniLibs; AGP picks the
// .so files up from there automatically. Requires cargo-ndk on PATH and
// ANDROID_NDK_HOME (or ANDROID_HOME/ndk/<version>) pointing at the NDK.
val cargoTargets = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
val cargoArgs = mutableListOf("cargo", "ndk")
cargoTargets.forEach { cargoArgs += listOf("-t", it) }
cargoArgs += listOf("--platform", "26", "-o", layout.projectDirectory.dir("src/main/jniLibs").asFile.absolutePath)
cargoArgs += listOf("build", "--release")

tasks.register<Exec>("buildRust") {
    group = "build"
    description = "Builds the demo-analysis JNI library with cargo-ndk."
    workingDir = rootDir.resolve("rust")
    commandLine = cargoArgs
    environment("ANDROID_HOME", System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: "")
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn("buildRust")
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Minimal JSON parsing for the JNI bridge; avoids pulling in serde-kotlin mirrors.
    implementation("org.json:json:20240303")

    testImplementation("junit:junit:4.13.2")
}
