plugins {
    alias(libs.plugins.azza.android.feature)
}

android {
    namespace = "com.azza.feature.map"
}

dependencies {
    // ✅ (2026-10-22) the satellite look, the deal colours and the shared map helpers.
    implementation(project(":core:map"))
    // The same map engine and tiles as the web (MapLibre GL + OpenFreeMap's "liberty" style).
    implementation(libs.maplibre.android)
}
