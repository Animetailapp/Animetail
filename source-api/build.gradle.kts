import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(mihonx.plugins.kotlin.multiplatform)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
    id("com.github.ben-manes.versions")
}

kotlin {
    android {
        namespace = "eu.kanade.tachiyomi.source"

        defaultConfig {
            consumerProguardFile("consumer-proguard.pro")
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    @Suppress("UnstableApiUsage")
    dependencies {
        api(libs.kotlinx.serialization.json)
        api(libs.injekt)
        api(libs.rxJava)
        api(libs.jsoup)
        api(libs.re2j)
        // TAIL
        api(projects.i18nTail)
        // TAIL

        // SY -->
        api(libs.kotlin.reflect)
        // SY <--

        implementation(platform(libs.androidx.compose.bom))
        implementation(libs.androidx.compose.runtime)
    }

    sourceSets {
        androidMain {
            dependencies {
                implementation(projects.core.common)
                api(libs.androidx.preference)
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
