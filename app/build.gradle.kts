plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.thramart.setup2"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.thramart.setup2"
        minSdk = 28
        targetSdk = 36
        versionCode = 4
        versionName = "2.2.0-v4-launch-fix"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
