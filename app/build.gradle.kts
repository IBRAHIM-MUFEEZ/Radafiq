plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

import java.io.File

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

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

val pythonPath = "C:\\Python314\\python.exe"

tasks.whenTaskAdded {
    if (name.endsWith("NativeLibs")) {
        doLast {
            val variantName = name.removePrefix("merge").removeSuffix("NativeLibs")
                .replaceFirstChar { it.lowercaseChar() }
            val buildDir = layout.buildDirectory.get().asFile
            val mergedDir = File(buildDir, "intermediates/merged_native_libs/$variantName/$name/out/lib")
            if (mergedDir.exists()) {
                logger.lifecycle("Patching ELF alignment for 16 KB pages in $mergedDir")
                val cmd = listOf(pythonPath, "scripts/patch_elf_16kb.py", mergedDir.absolutePath)
                val proc = ProcessBuilder(cmd)
                    .directory(rootProject.projectDir)
                    .redirectErrorStream(true)
                    .start()
                val output = proc.inputStream.reader().readText()
                val exitCode = proc.waitFor()
                logger.lifecycle(output.trim())
                if (exitCode != 0) {
                    throw RuntimeException("ELF patching failed with exit code $exitCode")
                }
            }
        }
    }
}

tasks.whenTaskAdded {
    if (name.startsWith("strip") && name.endsWith("DebugSymbols")) {
        doLast {
            val variantName = name.removePrefix("strip").removeSuffix("DebugSymbols")
                .replaceFirstChar { it.lowercaseChar() }
            val buildDir = layout.buildDirectory.get().asFile
            val strippedDir = File(buildDir, "intermediates/stripped_native_libs/$variantName/$name/out/lib")
            if (strippedDir.exists()) {
                logger.lifecycle("Patching ELF alignment for 16 KB pages in stripped libs: $strippedDir")
                val cmd = listOf(pythonPath, "scripts/patch_elf_16kb.py", strippedDir.absolutePath)
                val proc = ProcessBuilder(cmd)
                    .directory(rootProject.projectDir)
                    .redirectErrorStream(true)
                    .start()
                val output = proc.inputStream.reader().readText()
                val exitCode = proc.waitFor()
                logger.lifecycle(output.trim())
                if (exitCode != 0) {
                    throw RuntimeException("ELF patching failed with exit code $exitCode")
                }
            }
        }
    }
}

dependencies {

    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    // Profile Installer — installs baseline profile on API 24+ for AOT compilation of startup code
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
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

    // Google Sign-In (Credential Manager — replaces old GoogleSignIn APIs)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    // Drive token acquisition (kept for Drive backup/restore; @Suppress("DEPRECATION") applied)
    implementation("com.google.android.gms:play-services-auth-base:18.0.10")

    // Profile photo loading — FIX-22: Updated Coil
    implementation("io.coil-kt:coil-compose:2.7.0")

    // dotLottie animation
    implementation("com.github.LottieFiles:dotlottie-android:0.5.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
}
