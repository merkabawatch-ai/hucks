plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.shanemarchant.hubtv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.shanemarchant.hubtv"
        minSdk = 22          // Fire OS 5 (oldest Firesticks)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        // TMDB key comes from the TMDB_KEY GitHub secret, never from the code.
        val tmdb = (System.getenv("TMDB_KEY") ?: "").trim()
        buildConfigField("String", "TMDB_KEY", "\"$tmdb\"")
    }

    // Fixed key so each new build installs over the old one on the Firestick.
    signingConfigs {
        create("hubtv") {
            storeFile = file("hubtv.keystore")
            storePassword = "android"
            keyAlias = "hubtv"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("hubtv")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("hubtv")
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
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
