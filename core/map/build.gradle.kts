plugins {
    alias(libs.plugins.azza.android.library)
    alias(libs.plugins.azza.android.compose)
}

android {
    namespace = "com.azza.core.map"
}

// ✅ (2026-10-22) What every map in the app shares: the base style and the
// satellite look, the property-boundary geometry, and the boundary editor
// (used by the listing form; the map screen draws the same shapes).
dependencies {
    api(project(":core:model"))
    api(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    api(libs.maplibre.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
