import mihon.gradle.tasks.GenerateLocalesConfigTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(mihonx.plugins.kotlin.multiplatform)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.moko.resources)
}

kotlin {
    android {
        namespace = "tachiyomi.i18n.aniyomi"
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    @Suppress("UnstableApiUsage")
    dependencies {
        api(libs.moko.resources)
    }

    sourceSets {
        commonMain {
            resources.srcDir("src/commonMain/resources")
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

multiplatformResources {
    resourcesClassName.set("AYMR")
    resourcesPackage.set("tachiyomi.i18n.aniyomi")
}

tasks {
    val generatedAndroidResourceDir = layout.buildDirectory.dir("generated/android/res")
    val localesConfigTask = register<GenerateLocalesConfigTask>("generateLocalesConfig") {
        outputDir.set(generatedAndroidResourceDir)
    }
}

androidComponents {
    onVariants { variant ->
        val resSource = variant.sources.res ?: return@onVariants
        resSource.addGeneratedSourceDirectory(
            tasks.named("generateLocalesConfig", GenerateLocalesConfigTask::class.java),
        ) {
            it.outputDir
        }
    }
}
