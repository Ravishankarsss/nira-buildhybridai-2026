plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.mos.nira"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.mos.nira"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // BuildConfig.DEBUG guards the debug-only Developer Test card in Settings.
        buildConfig = true
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)

    // OpenCV: geometry and morphology only, always through core/image/ImageOps. Not AI.
    implementation(libs.opencv)
    implementation("com.google.guava:guava:33.4.0-android")
    // CameraX: photographing a garment inside the app.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Firebase BoM, AI Logic and App Check
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.ai)
    // On-device (Gemini Nano) part of hybrid inference. Switched off for now: version
    // 16.0.0-beta08 needs Kotlin 2.4.0 and this project is on Kotlin 2.2.10. Without it,
    // requests still go through the hybrid API and run in the cloud.
    implementation(libs.firebase.ai.ondevice)
    implementation(libs.firebase.appcheck.debug)

    testImplementation(libs.junit)
    // Real JSON parser for JVM unit tests. Without it the Android org.json stubs return
    // default values and a rules-file test would pass without reading anything.
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
