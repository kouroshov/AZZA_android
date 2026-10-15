plugins {
    alias(libs.plugins.azza.android.feature)
}

android {
    namespace = "com.azza.feature.create"
}

dependencies {
    // The system photo picker (PickMultipleVisualMedia): no storage permission needed.
    implementation(libs.androidx.activity.compose)
}
