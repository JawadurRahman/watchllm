plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.focussystems.watchllm"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.focussystems.watchllm"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        // OnePlus Watch 2R is 32-bit userspace only.
        ndk { abiFilters += "armeabi-v7a" }
        externalNativeBuild {
            cmake {
                // Always optimised, even for debug builds (a -O0 ggml is unusably slow).
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                arguments += "-DANDROID_ARM_NEON=ON"
                arguments += "-DANDROID_STL=c++_static"
                // Override with -PllamaCppDir=... in gradle.properties / on the command line.
                arguments += "-DLLAMA_CPP_DIR=${providers.gradleProperty("llamaCppDir").getOrElse("C:/llama.cpp")}"
            }
        }
    }
    ndkVersion = "30.0.16248370"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    useLibrary("wear-sdk")
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.navigation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling)
    implementation(libs.core.splashscreen)
    implementation(libs.play.services.wearable)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.wear.tooling.preview)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.test.manifest)
    debugImplementation(libs.ui.tooling)
}