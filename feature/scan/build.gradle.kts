plugins {
    alias(libs.plugins.azza.android.feature)
}

android {
    namespace = "com.azza.feature.scan"
}

dependencies {
    // Camera frames for the scanner.
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // EAN-13 decoding in pure Java: no Google Play Services needed, unlike
    // ML Kit — many phones in Iran have none (docs/ROADMAP.md, phase 4b).
    implementation(libs.zxing.core)
}
