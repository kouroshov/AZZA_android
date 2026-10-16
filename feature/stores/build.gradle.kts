plugins {
    alias(libs.plugins.azza.android.feature)
}

android {
    namespace = "com.azza.feature.stores"
}

dependencies {
    // Picking a branch's place on the map: the same engine and tiles as the Map tab.
    implementation(libs.maplibre.android)
}
