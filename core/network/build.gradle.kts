plugins {
    alias(libs.plugins.azza.android.library)
    alias(libs.plugins.azza.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.azza.core.network"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:datastore"))

    api(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.okhttp.mockwebserver)
}
