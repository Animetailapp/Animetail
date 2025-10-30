package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.FlareSolverrInterceptor
import eu.kanade.tachiyomi.network.interceptor.IgnoreGzipInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit

class NetworkHelper(
    private val context: Context,
    private val preferences: NetworkPreferences,
) {

    val cookieJar = AndroidCookieJar()

    private val clientBuilder: OkHttpClient.Builder = run {
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.MINUTES)
            .cache(
                Cache(
                    directory = File(context.cacheDir, "network_cache"),
                    maxSize = 5L * 1024 * 1024, // 5 MiB
                ),
            )
            .addInterceptor(UncaughtExceptionInterceptor())
            .addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))
            .addNetworkInterceptor(IgnoreGzipInterceptor())
            .addNetworkInterceptor(BrotliInterceptor)
            // TLMR -->
            .addInterceptor(FlareSolverrInterceptor(preferences))
        // <-- TLMR

        if (preferences.verboseLogging().get()) {
            val httpLoggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            builder.addNetworkInterceptor(httpLoggingInterceptor)
        }

        builder.addInterceptor(
            // TLMR -->
            CloudflareInterceptor(context, cookieJar, preferences) { defaultUserAgentProvider() },
            // <-- TLMR
        )

        when (preferences.dohProvider().get()) {
            PREF_DOH_CLOUDFLARE -> builder.dohCloudflare()
            PREF_DOH_GOOGLE -> builder.dohGoogle()
            PREF_DOH_ADGUARD -> builder.dohAdGuard()
            PREF_DOH_QUAD9 -> builder.dohQuad9()
            PREF_DOH_ALIDNS -> builder.dohAliDNS()
            PREF_DOH_DNSPOD -> builder.dohDNSPod()
            PREF_DOH_360 -> builder.doh360()
            PREF_DOH_QUAD101 -> builder.dohQuad101()
            PREF_DOH_MULLVAD -> builder.dohMullvad()
            PREF_DOH_CONTROLD -> builder.dohControlD()
            PREF_DOH_NJALLA -> builder.dohNajalla()
            PREF_DOH_SHECAN -> builder.dohShecan()
            PREF_DOH_LIBREDNS -> builder.dohLibreDNS()
            PREF_DOH_CUSTOM -> {
                val custom = preferences.dohCustomUrl().get().trim()
                if (custom.isNotEmpty()) {
                    try {
                        // Validate URL early
                        custom.toHttpUrl()

                        // Parse optional bootstrap hosts from comma-separated preference
                        val bootstrapPref = preferences.dohCustomBootstrap().get().trim()
                        val bootstrapHosts = if (bootstrapPref.isNotEmpty()) {
                            bootstrapPref.split(',')
                                .mapNotNull { it.trim().takeIf { t -> t.isNotEmpty() } }
                                .mapNotNull { host ->
                                    try {
                                        java.net.InetAddress.getByName(host)
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                        } else {
                            emptyList()
                        }

                        builder.dohCustom(custom, bootstrapHosts)
                    } catch (e: Exception) {
                        // Invalid URL: fall back to no DoH
                        builder
                    }
                } else {
                    builder
                }
            }
            else -> builder
        }
    }

    val nonCloudflareClient = clientBuilder.build()

    val client = clientBuilder
        .addInterceptor(
            CloudflareInterceptor(context, cookieJar, preferences) { defaultUserAgentProvider() },
        )
        .build()

    /**
     * @deprecated Since extension-lib 1.5
     */
    @Deprecated("The regular client handles Cloudflare by default")
    @Suppress("UNUSED")
    val cloudflareClient: OkHttpClient = client

    fun defaultUserAgentProvider() = preferences.defaultUserAgent().get().trim()
}
