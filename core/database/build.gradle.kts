plugins {
    alias(libs.plugins.azza.android.library)
    alias(libs.plugins.azza.hilt)
}

android {
    namespace = "com.azza.core.database"
}

dependencies {
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
}
