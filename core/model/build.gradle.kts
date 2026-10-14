plugins {
    alias(libs.plugins.azza.android.library)
}

// Plain domain types: no Android, no serialization. What the screens see,
// independent of how apps/api happens to shape its JSON today.
android {
    namespace = "com.azza.core.model"
}
