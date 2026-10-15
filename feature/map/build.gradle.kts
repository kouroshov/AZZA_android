plugins {
    alias(libs.plugins.azza.android.feature)
}

android {
    namespace = "com.azza.feature.map"
}

dependencies {
    // The same map engine and tiles as the web (MapLibre GL + OpenFreeMap's "liberty" style).
    implementation(libs.maplibre.android)
}
