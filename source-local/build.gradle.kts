plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)
}

android {
    namespace = "tachiyomi.source.local"

    defaultConfig {
        consumerProguardFiles("consumer-proguard.pro")
    }
}

kotlin {
    compilerOptions {
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.i18n)
    implementation(projects.i18nAniyomi)
    implementation(projects.i18nTail)

    implementation(projects.core.archive)
    implementation(projects.core.common)
    implementation(projects.coreMetadata)
    implementation(projects.domain)

    implementation(libs.unifile)
    implementation(aniyomilibs.ffmpeg.kit)
    implementation(libs.bundles.serialization)

    implementation(libs.injekt)
    implementation(libs.jsoup)
}
