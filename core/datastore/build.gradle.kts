plugins {
    alias(libs.plugins.azza.android.library)
    alias(libs.plugins.azza.hilt)
}

android {
    namespace = "com.azza.core.datastore"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.datastore.preferences)
}
