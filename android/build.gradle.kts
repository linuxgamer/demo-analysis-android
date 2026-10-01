plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.stast.demodetector"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.stast.demodetector"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
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
        // Signed with the debug key so CI-produced release APKs are installable;
        // no secrets to distribute, this build is not meant for store publishing.
        release {
            isMinifyEnabled = false
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
val cargoTargets = listOf("arm64-v8a", "x86_64")
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
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Minimal JSON parsing for the JNI bridge; avoids pulling in serde-kotlin mirrors.
    implementation("org.json:json:20240303")
}
