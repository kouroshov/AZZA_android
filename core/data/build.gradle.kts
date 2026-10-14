plugins {
    alias(libs.plugins.azza.android.library)
    alias(libs.plugins.azza.hilt)
}

android {
    namespace = "com.azza.core.data"
}

dependencies {
    api(project(":core:common"))
    api(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(libs.kotlinx.serialization.json)
}
