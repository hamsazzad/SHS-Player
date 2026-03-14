plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.shsai.antigravity"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shsai.antigravity"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += listOf("armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            isMinifyEnabled = false
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
