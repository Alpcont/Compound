plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.alp.compound"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alp.compound"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.1.1"
    }

    // Fixed key so every new APK installs as an update over the previous one.
    // Never change or regenerate this keystore.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

// No extra libraries — Android platform APIs only. Add one only when it saves a lot of code.
