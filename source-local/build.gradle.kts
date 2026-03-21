import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(mihonx.plugins.kotlin.multiplatform)
    alias(mihonx.plugins.spotless)
}

kotlin {
    android {
        namespace = "tachiyomi.source.local"
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    @Suppress("UnstableApiUsage")
    dependencies {
        implementation(projects.sourceApi)
        api(projects.i18n)
        api(projects.i18nAniyomi)
        // TAIL -->
        api(projects.i18nTail)
        // TAIL <--
        implementation(libs.unifile)
    }

    sourceSets {
        androidMain {
            dependencies {
                implementation(projects.core.archive)
                implementation(projects.core.common)
                implementation(projects.coreMetadata)

                // Move ChapterRecognition to separate module?
                implementation(projects.domain)

                implementation(libs.bundles.serialization)
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    dependencies {
        // FFmpeg-kit
        implementation(aniyomilibs.ffmpeg.kit)
    }
}
