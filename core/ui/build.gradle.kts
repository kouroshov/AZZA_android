plugins {
    alias(libs.plugins.azza.android.library)
    alias(libs.plugins.azza.android.compose)
}

android {
    namespace = "com.azza.core.ui"
}

dependencies {
    api(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    api(libs.coil.compose)
}
