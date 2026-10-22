plugins {
    alias(libs.plugins.azza.android.feature)
}

android {
    namespace = "com.azza.feature.create"
}

dependencies {
    // ✅ (2026-10-22) the real-estate location / boundary editor.
    implementation(project(":core:map"))
    // The system photo picker (PickMultipleVisualMedia): no storage permission needed.
    implementation(libs.androidx.activity.compose)
}
