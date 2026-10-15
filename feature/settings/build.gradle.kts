plugins {
    alias(libs.plugins.azza.android.feature)
}

android {
    namespace = "com.azza.feature.settings"
}

dependencies {
    // Per-app language (AppCompatDelegate.setApplicationLocales), down to API 24.
    implementation(libs.androidx.appcompat)
}
