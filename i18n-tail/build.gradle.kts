plugins {
    alias(mihonx.plugins.kotlin.multiplatform)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.moko.resources)
}

kotlin {
    android {
        namespace = "tachiyomi.i18n.tail"
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    @Suppress("UnstableApiUsage")
    dependencies {
        api(libs.moko.resources)
    }

    sourceSets {
        commonMain {
            resources.srcDir("src/commonMain/resources")
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

android {
    lint {
        disable += "MissingTranslation"
        disable += "ExtraTranslation"
    }
}

multiplatformResources {
    resourcesClassName.set("TLMR")
    resourcesPackage.set("tachiyomi.i18n.tail")
}
