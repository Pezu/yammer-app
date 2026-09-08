import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Build-time defaults from the (git-ignored) local.properties, so a device can be provisioned
// with the key already filled in without the key ever entering the source tree:
//   bridge.apiKey=<value of the backend's BRIDGE_API_KEY secret>
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val defaultApiKey: String = localProps.getProperty("bridge.apiKey", "")

android {
    namespace = "com.yammer.bridge.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yammer.bridge.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "DEFAULT_API_KEY", "\"$defaultApiKey\"")
    }

    buildFeatures {
        buildConfig = true
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

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // USB host serial drivers (FTDI, CP210x, CH34x, PL2303, CDC-ACM)
    implementation("com.github.mik3y:usb-serial-for-android:3.9.0")
}
