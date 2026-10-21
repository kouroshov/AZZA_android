pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AZZA"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

include(":core:common")
include(":core:model")
include(":core:network")
include(":core:datastore")
include(":core:database")
include(":core:data")
include(":core:designsystem")
include(":core:ui")

include(":feature:auth")
include(":feature:feed")
include(":feature:product")
include(":feature:profile")
include(":feature:search")
include(":feature:basket")
include(":feature:notifications")
include(":feature:create")
include(":feature:map")
include(":feature:settings")
include(":core:scanner")
include(":feature:stores")
