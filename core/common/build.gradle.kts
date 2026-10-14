plugins {
    alias(libs.plugins.azza.android.library)
    alias(libs.plugins.azza.hilt)
}

android {
    namespace = "com.azza.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
}
