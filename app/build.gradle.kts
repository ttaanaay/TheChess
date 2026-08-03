plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.chessbubble"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.chessbubble"
        // minSdk 26 required for reliable overlay windows (TYPE_APPLICATION_OVERLAY)
        // and for MediaProjection foreground service type handling.
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.9.1")

    // Coroutines for async frame processing / engine calls
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // NOTE: do NOT add an org.json dependency here -- Android bundles its own
    // org.json.JSONObject on the device's bootclasspath, and it does not match
    // the standalone org.json library. The app compiles fine against the
    // richer standalone API but crashes at runtime with NoSuchMethodError
    // because the OS's version wins at runtime. Use the platform's org.json
    // (already available, no dependency needed) and stick to its API surface
    // (e.g. put(String, Double), not put(String, Float)).

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
