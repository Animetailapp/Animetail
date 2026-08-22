package eu.kanade.tachiyomi.network

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.FlareSolverrInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import kotlinx.coroutines.CoroutineScope
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Inject
@SingleIn(AppScope::class)
class NetworkHelper(
    private val context: Context,
    private val preferences: NetworkPreferences,
    scope: CoroutineScope,
) {

    val cookieJar = AndroidCookieJar()

    private val clientBuilder: OkHttpClient.Builder = run {
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(30.seconds)
            .readTimeout(30.seconds)
            .callTimeout(2.minutes)
            .cache(
                Cache(
                    directory = File(context.cacheDir, "network_cache"),
                    maxSize = 5L * 1024 * 1024, // 5 MiB
                ),
            )
            .addInterceptor(BrotliInterceptor)
            .addInterceptor(UncaughtExceptionInterceptor())
            .addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))
            .addInterceptor(FlareSolverrInterceptor(preferences))

        try {
            val trustManager = getTrustManager()
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(trustManager), SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustManager)
        } catch (_: Exception) {
        }

        if (preferences.verboseLogging.get()) {
            val httpLoggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            builder.addNetworkInterceptor(httpLoggingInterceptor)
        }

        when (preferences.dohProvider.get()) {
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
                val custom = preferences.dohCustomUrl.get().trim()
                if (custom.isNotEmpty()) {
                    try {
                        // Validate URL early
                        custom.toHttpUrl()

                        // Parse optional bootstrap hosts from comma-separated preference
                        val bootstrapPref = preferences.dohCustomBootstrap.get().trim()
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
                        // Invalid URL: fall back to system DNS with sinkhole bypass
                        builder.systemDnsWithDohFallback()
                    }
                } else {
                    builder.systemDnsWithDohFallback()
                }
            }

            else -> builder.systemDnsWithDohFallback()
        }
    }

    val client = clientBuilder
        .addInterceptor(
            CloudflareInterceptor(context, cookieJar, preferences, scope) { defaultUserAgentProvider() },
        )
        .build()

    /**
     * @deprecated Since extension-lib 1.5
     */
    @Deprecated("The regular client handles Cloudflare by default")
    @Suppress("UNUSED")
    val cloudflareClient: OkHttpClient = client

    fun defaultUserAgentProvider() = preferences.defaultUserAgent.get().trim()

    private fun getTrustManager(): X509TrustManager {
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(null as KeyStore?)
        val defaultTrustManager = trustManagerFactory.trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()

        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                defaultTrustManager.checkClientTrusted(chain, authType)
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                try {
                    defaultTrustManager.checkServerTrusted(chain, authType)
                } catch (_: Exception) {
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return defaultTrustManager.acceptedIssuers
            }
        }
    }
}
