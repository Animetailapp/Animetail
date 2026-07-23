plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "eu.kanade.tachiyomi.source"

    defaultConfig {
        consumerProguardFiles("consumer-proguard.pro")
    }
}

dependencies {
    api(projects.core.common)

    api(libs.kotlinx.serialization.json)
    api(libs.injekt)
    api(libs.rxJava)
    api(libs.jsoup)
    api(libs.re2j)
    api(aniyomilibs.nanohttpd)
    api(projects.i18nTail)

    // SY -->
    api(libs.kotlin.reflect)
    // SY <--

    api(libs.androidx.preference)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
}

