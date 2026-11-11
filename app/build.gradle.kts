plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") // Kotlin 2.x
}
android {
    namespace = "com.example.docscanner"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.docscanner"
        minSdk = 26; targetSdk = 35
        versionCode = 1; versionName = "1.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") } // keeps APK small
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    val camerax = "1.3.4"
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("org.opencv:opencv:4.9.0") // OpenCV on Maven Central
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")
}
