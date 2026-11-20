
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.motionpilot3d"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.motionpilot3d"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.7"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
