plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "ph.appbuilders.saklolo"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "ph.appbuilders.saklolo"
        minSdk = 26
        targetSdk = 35
        versionCode = 24
        versionName = "0.12.6"
        ndk {
            // Phones at the demo are arm64. x86_64 covers an emulator.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                )
                cppFlags += "-O3"
            }
        }
    }

    signingConfigs {
        create("demo") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            externalNativeBuild {
                cmake {
                    arguments += "-DCMAKE_BUILD_TYPE=Release"
                }
            }
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("demo")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    androidResources {
        noCompress += "bin"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            pickFirsts += "**/libc++_shared.so"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

val whisperModel = layout.projectDirectory.file("src/main/assets/models/ggml-base-q5_1.bin").asFile
val whisperModelBytes = 59_707_625L

tasks.register("fetchWhisperModel") {
    outputs.file(whisperModel)
    onlyIf { !whisperModel.exists() || whisperModel.length() != whisperModelBytes }
    doLast {
        whisperModel.parentFile.mkdirs()
        exec {
            commandLine(
                "curl",
                "-fL",
                "--retry",
                "3",
                "-o",
                whisperModel.absolutePath,
                "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
            )
        }
        check(whisperModel.length() == whisperModelBytes) {
            "Whisper model download was ${whisperModel.length()} bytes, expected $whisperModelBytes"
        }
    }
}

tasks.configureEach {
    if (
        name == "preBuild" ||
        name.startsWith("configureCMake") ||
        name.startsWith("buildCMake") ||
        name.startsWith("externalNativeBuild")
    ) {
        dependsOn(rootProject.tasks.named("fetchWhisperCpp"))
    }
    if (name == "preBuild" || name == "mergeDebugAssets" || name == "mergeReleaseAssets") {
        dependsOn("fetchWhisperModel")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.gms:play-services-nearby:19.3.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("com.google.mediapipe:tasks-genai:0.10.27")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
