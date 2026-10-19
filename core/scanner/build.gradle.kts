plugins {
    alias(libs.plugins.azza.android.library)
    alias(libs.plugins.azza.android.compose)
}

android {
    namespace = "com.azza.core.scanner"
}

// The register's scanner (POS-04), shared by the inventory and the sales
// register. There is no universal scan button any more (POS decision).
dependencies {
    implementation(project(":core:common"))
    api(project(":core:designsystem"))
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    // Camera frames for the scanner.
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // Decoding and drawing barcodes in pure Java: no Google Play Services
    // needed, unlike ML Kit — many phones in Iran have none (docs/ROADMAP.md, phase 4b).
    api(libs.zxing.core)
}
