plugins {
    alias(libs.plugins.azza.android.library)
    alias(libs.plugins.azza.android.compose)
}

android {
    namespace = "com.azza.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material.icons.extended)
}
