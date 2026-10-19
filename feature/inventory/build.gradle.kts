plugins {
    alias(libs.plugins.azza.android.feature)
}

android {
    namespace = "com.azza.feature.inventory"
}

// The store's private inventory (POS-04).
dependencies {
    implementation(project(":core:scanner"))
    implementation(libs.androidx.activity.compose)
    // Printing a code label.
    implementation(libs.androidx.print)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Newer than the one compose-ui-test pulls in: that one calls an InputManager
    // method Android 16+ no longer has.
    androidTestImplementation(libs.androidx.test.espresso)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
