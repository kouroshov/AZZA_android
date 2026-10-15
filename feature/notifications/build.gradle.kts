plugins {
    alias(libs.plugins.azza.android.feature)
}

android {
    namespace = "com.azza.feature.notifications"
}

dependencies {
    // The background poll (NotificationPolling) and its Hilt-injected worker.
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    // The chosen app language, for notifications posted from the background.
    implementation(libs.androidx.appcompat)
}
