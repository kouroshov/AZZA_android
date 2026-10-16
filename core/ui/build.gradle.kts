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
    // PagedState: the shared feed list renders a CursorPager's state.
    implementation(project(":core:data"))
    api(libs.coil.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // The app lock (AppUnlock): fingerprint / face, or the phone's screen lock.
    api(libs.androidx.biometric)
}
