pluginManagement {
    includeBuild("gradle/build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://www.jitpack.io")
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("aniyomilibs") {
            from(files("gradle/aniyomi.versions.toml"))
        }
        create("mihonx") {
            from(files("gradle/mihon.versions.toml"))
        }
    }
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://www.jitpack.io")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Animetail"
include(":app")
include(":core-metadata")
include(":core:archive")
include(":core:common")
include(":data")
include(":domain")
include(":i18n")
include(":i18n-aniyomi")
// TAIL -->
include(":i18n-tail")
// TAIL <--
include(":macrobenchmark")
include(":presentation-core")
include(":presentation-widget")
include(":source-api")
include(":source-local")
