plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
    id("com.github.ben-manes.versions")
}

android {
    namespace = "eu.kanade.tachiyomi.core.common"
    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

dependencies {
    implementation(projects.i18n)
    // TAIL -->
    implementation(projects.i18nTail)
    // TAIL <--
    api(libs.logcat)

    api(libs.rxJava)

    api(libs.okhttp.core)
    api(libs.okhttp.logging)
    api(libs.okhttp.brotli)
    api(libs.okhttp.dnsOverHttps)
    api(libs.okio)

    implementation(libs.image.decoder)

    implementation(libs.unifile)
    implementation(libs.archive)

    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.serialization.jsonOkio)

    api(libs.androidx.preference)

    implementation(libs.jsoup)
    implementation(libs.re2j)

    // Sort
    implementation(libs.natural.comparator)

    // JavaScript engine
    implementation(libs.quickJs)

    // FFmpeg-kit
    implementation(aniyomilibs.ffmpeg.kit)

    // SY -->
    implementation(libs.zip4j)
    // TLMR -->
    implementation(libs.injekt)
    // TLMR <--
    implementation(libs.exifinterface)
    // SY <--

    // TAIL
    implementation(libs.torrentserver)

    // Tests
    testImplementation(libs.bundles.test)
}
