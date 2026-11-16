plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "dev.haashemi.virshdroid"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.haashemi.virshdroid"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        resources.excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    // 1.5 is the first line with the Material 3 Expressive components as public API.
    implementation("androidx.compose.material3:material3:1.5.0-rc01")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.navigation3:navigation3-ui:1.2.1")
    implementation("androidx.datastore:datastore-preferences:1.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
    implementation("com.github.mwiede:jsch:2.28.8")
    // JSch uses Bouncy Castle for Ed25519 keys on Android.
    implementation("org.bouncycastle:bcprov-jdk18on:1.87")
}
