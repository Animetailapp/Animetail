package eu.kanade.tachiyomi.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Dns
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

/**
 * Based on https://github.com/square/okhttp/blob/ef5d0c83f7bbd3a0c0534e7ca23cbc4ee7550f3b/okhttp-dnsoverhttps/src/test/java/okhttp3/dnsoverhttps/DohProviders.java
 */

const val PREF_DOH_CLOUDFLARE = 1
const val PREF_DOH_GOOGLE = 2
const val PREF_DOH_ADGUARD = 3
const val PREF_DOH_QUAD9 = 4
const val PREF_DOH_ALIDNS = 5
const val PREF_DOH_DNSPOD = 6
const val PREF_DOH_360 = 7
const val PREF_DOH_QUAD101 = 8
const val PREF_DOH_MULLVAD = 9
const val PREF_DOH_CONTROLD = 10
const val PREF_DOH_NJALLA = 11
const val PREF_DOH_SHECAN = 12
const val PREF_DOH_LIBREDNS = 13
const val PREF_DOH_CUSTOM = 14

private fun OkHttpClient.cloudflareDoh(): DnsOverHttps = DnsOverHttps.Builder()
    .client(this)
    .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
    .bootstrapDnsHosts(
        InetAddress.getByName("162.159.36.1"),
        InetAddress.getByName("162.159.46.1"),
        InetAddress.getByName("1.1.1.1"),
        InetAddress.getByName("1.0.0.1"),
        InetAddress.getByName("162.159.132.53"),
        InetAddress.getByName("2606:4700:4700::1111"),
        InetAddress.getByName("2606:4700:4700::1001"),
        InetAddress.getByName("2606:4700:4700::0064"),
        InetAddress.getByName("2606:4700:4700::6400"),
    )
    .build()

private fun OkHttpClient.Builder.doh(dns: Dns) = dns(dns.withSystemFallbackForSinkhole())

private fun InetAddress.isSinkholeAddress() = isLoopbackAddress || isAnyLocalAddress

private fun String.isLocalHostname() =
    equals("localhost", ignoreCase = true) ||
        endsWith(".localhost", ignoreCase = true) ||
        endsWith(".local", ignoreCase = true) ||
        contains(':') ||
        matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))

private fun Dns.filterSinkholeOr(fallback: Dns): Dns = Dns { hostname ->
    val addresses = lookup(hostname)
    if (hostname.isLocalHostname()) {
        addresses
    } else {
        addresses.filterNot(InetAddress::isSinkholeAddress)
            .ifEmpty { fallback.lookup(hostname) }
    }
}

private fun Dns.withSystemFallbackForSinkhole() = filterSinkholeOr(Dns.SYSTEM)

/**
 * Some networks and DNS-based blockers resolve blocked domains (e.g. video hosts)
 * to sinkhole addresses like ::1 or 0.0.0.0. Keep using the system DNS, but retry
 * with Cloudflare DoH when only sinkhole addresses are returned.
 */
fun OkHttpClient.Builder.systemDnsWithDohFallback(): OkHttpClient.Builder {
    val fallback = build().cloudflareDoh()
    return dns(Dns.SYSTEM.filterSinkholeOr(fallback))
}

fun OkHttpClient.Builder.dohCloudflare() = doh(build().cloudflareDoh())

fun OkHttpClient.Builder.dohGoogle() = doh(
    DnsOverHttps.Builder().client(build())
        .url("https://dns.google/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("8.8.4.4"),
            InetAddress.getByName("8.8.8.8"),
            InetAddress.getByName("2001:4860:4860::8888"),
            InetAddress.getByName("2001:4860:4860::8844"),
        )
        .build(),
)

// AdGuard "Default" DNS works too but for the sake of making sure no site is blacklisted,
// we use "Unfiltered"
fun OkHttpClient.Builder.dohAdGuard() = doh(
    DnsOverHttps.Builder().client(build())
        .url("https://dns-unfiltered.adguard.com/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("94.140.14.140"),
            InetAddress.getByName("94.140.14.141"),
            InetAddress.getByName("2a10:50c0::1:ff"),
            InetAddress.getByName("2a10:50c0::2:ff"),
        )
        .build(),
)

fun OkHttpClient.Builder.dohQuad9() = doh(
    DnsOverHttps.Builder().client(build())
        .url("https://dns.quad9.net/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("9.9.9.9"),
            InetAddress.getByName("149.112.112.112"),
            InetAddress.getByName("2620:fe::fe"),
            InetAddress.getByName("2620:fe::9"),
        )
        .build(),
)

fun OkHttpClient.Builder.dohAliDNS() = doh(
    DnsOverHttps.Builder().client(build())
        .url("https://dns.alidns.com/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("223.5.5.5"),
            InetAddress.getByName("223.6.6.6"),
            InetAddress.getByName("2400:3200::1"),
            InetAddress.getByName("2400:3200:baba::1"),
        )
        .build(),
)

fun OkHttpClient.Builder.dohDNSPod() = doh(
    DnsOverHttps.Builder().client(build())
        .url("https://doh.pub/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("1.12.12.12"),
            InetAddress.getByName("120.53.53.53"),
        )
        .build(),
)

fun OkHttpClient.Builder.doh360() = doh(
    DnsOverHttps.Builder().client(build())
        .url("https://doh.360.cn/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("101.226.4.6"),
            InetAddress.getByName("218.30.118.6"),
            InetAddress.getByName("123.125.81.6"),
            InetAddress.getByName("140.207.198.6"),
            InetAddress.getByName("180.163.249.75"),
            InetAddress.getByName("101.199.113.208"),
            InetAddress.getByName("36.99.170.86"),
        )
        .build(),
)

fun OkHttpClient.Builder.dohQuad101() = doh(
    DnsOverHttps.Builder().client(build())
        .url("https://dns.twnic.tw/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("101.101.101.101"),
            InetAddress.getByName("2001:de4::101"),
            InetAddress.getByName("2001:de4::102"),
        )
        .build(),
)

/*
 * Mullvad DoH
 * without ad blocking option
 * Source: https://mullvad.net/en/help/dns-over-https-and-dns-over-tls
 */
fun OkHttpClient.Builder.dohMullvad() = doh(
    DnsOverHttps.Builder().client(build())
        .url(" https://dns.mullvad.net/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("194.242.2.2"),
            InetAddress.getByName("2a07:e340::2"),
        )
        .build(),
)

/*
 * Control D
 * unfiltered option
 * Source: https://controld.com/free-dns/?
 */
fun OkHttpClient.Builder.dohControlD() = doh(
    DnsOverHttps.Builder().client(build())
        .url("https://freedns.controld.com/p0".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("76.76.2.0"),
            InetAddress.getByName("76.76.10.0"),
            InetAddress.getByName("2606:1a40::"),
            InetAddress.getByName("2606:1a40:1::"),
        )
        .build(),
)

/*
 * Njalla
 * Non logging and uncensored
 */
fun OkHttpClient.Builder.dohNajalla() = doh(
    DnsOverHttps.Builder().client(build())
        .url("https://dns.njal.la/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("95.215.19.53"),
            InetAddress.getByName("2001:67c:2354:2::53"),
        )
        .build(),
)

/**
 * Source: https://shecan.ir/
 */
fun OkHttpClient.Builder.dohShecan() = doh(
    DnsOverHttps.Builder().client(build())
        .url("https://free.shecan.ir/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("178.22.122.100"),
            InetAddress.getByName("185.51.200.2"),
        )
        .build(),
)

/**
 * Source: https://libredns.gr/
 */
fun OkHttpClient.Builder.dohLibreDNS() = doh(
    DnsOverHttps.Builder().client(build())
        .url("https://doh.libredns.gr/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("116.202.176.26"),
            InetAddress.getByName("2a01:4f8:1c0c:8274::1"),
        )
        .build(),
)

/**
 * Build a DoH implementation using a custom user-provided URL.
 * The URL must be a valid https URL (e.g. "https://example.com/dns-query").
 */
fun OkHttpClient.Builder.dohCustom(dohUrl: String, bootstrapHosts: List<java.net.InetAddress> = emptyList()) = doh(
    DnsOverHttps.Builder().client(build())
        .url(dohUrl.toHttpUrl())
        .apply {
            if (bootstrapHosts.isNotEmpty()) {
                bootstrapDnsHosts(*bootstrapHosts.toTypedArray())
            }
        }
        .build(),
)
