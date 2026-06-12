plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.radafiq"

    // ✅ Updated (important)
    compileSdk = 35

    defaultConfig {
        applicationId = "com.radafiq"
        minSdk = 24
        targetSdk = 35
        // FIX-23: versionCode must be incremented with every release build.
        // Increment this before every Play Store submission.
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            // FIX-21: Never hardcode absolute paths or usernames in build files.
            // Set KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD as
            // environment variables in CI or in your local ~/.gradle/gradle.properties.
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: "radafiq-key"
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
        create("shared") {
            // Reuse the same keystore across all machines so the same SHA-1
            // works everywhere. Set KEYSTORE_PATH (and friends) on each
            // developer machine to point to the shared keystore.
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "radafiq-debug.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "radafiq123"
            keyAlias = System.getenv("KEY_ALIAS") ?: "radafiq-key"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "radafiq123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {

    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Compose
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    // FIX-22: Updated Compose BOM to latest stable
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // FIX-22: Updated Material3 to latest stable
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.6")

    // 🚀 IMPORTANT FIX (Navigation)
    implementation("androidx.navigation:navigation-compose:2.8.5")
    // FIX-22: Updated biometric
    implementation("androidx.biometric:biometric:1.1.0")
    // FIX-22: Updated security-crypto (1.1.0-alpha06 has key rotation fixes)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.google.android.gms:play-services-auth-base:18.0.10")

    // Profile photo loading — FIX-22: Updated Coil
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
}
