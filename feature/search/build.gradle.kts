plugins {
    alias(libs.plugins.azza.android.feature)
}

android {
    namespace = "com.azza.feature.search"
}

dependencies {
    // The search box's barcode button.
    implementation(project(":core:scanner"))
}
