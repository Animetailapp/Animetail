package eu.kanade.tachiyomi.source.manga.builtin.ehentai

import android.net.Uri
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.NamespaceSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Built-in E-Hentai / ExHentai manga source.
 *
 * Authentication is handled transparently by [EHentaiInterceptor]:
 *  - When the user is logged in via the E-Hentai tracker service, cookies are
 *    injected automatically so ExHentai galleries and member content are visible.
 *  - When not logged in, only publicly accessible E-Hentai galleries are shown.
 *
 * You can switch between E-Hentai and ExHentai in the built-in Settings screen
 * (Settings → Tracking → E-Hentai settings).
 */
class EHentaiSource : HttpSource(), UrlImportableSource, NamespaceSource {

    companion object {
        /**
         * Stable source ID — kept identical to the community E-Hentai extension
         * so that manga entries saved from the extension remain accessible.
         * Computed from MD5("e-hentai/all/1") once and hardcoded for stability.
         */
        const val ID = 2814091215L

        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    }

    override val name = "E-Hentai"
    override val lang = "all"
    override val supportsLatest = true
    override val baseUrl: String
        get() = if (useExHentai) EHentaiConstants.EX_URL else EHentaiConstants.BASE_URL

    // Force stable ID regardless of versionId and name hashing
    override val id = ID

    // Don't use the default MD5-based id generation
    override val versionId = 1

    private val sourcePreferences: SourcePreferences by injectLazy()
    private val useExHentai get() = sourcePreferences.ehUseExHentai().get()
    private val titleDisplayMode get() = sourcePreferences.ehTitleDisplayMode().get()
    private val defaultCategories get() = sourcePreferences.ehDefaultCategories().get()

    // ── UrlImportableSource ────────────────────────────────────────────────────
    // Accept both EH and ExH domains so pasted URLs always resolve
    override val matchingHosts: List<String> = listOf(
        "e-hentai.org",
        "g.e-hentai.org",
        "exhentai.org",
    )

    // ── checkValid: mirrors Komikku — raises an error if ExHentai returns empty due to bad igneous ──
    private fun MangasPage.checkValid(): MangasPage {
        if (useExHentai && mangas.isEmpty()) {
            val igneous = sourcePreferences.ehIgneous().get()
            when {
                igneous.isBlank() ->
                    throw Exception("ExHentai requires a logged-in account with ExHentai access — please log in via E-Hentai settings")
                igneous.equals("mystery", ignoreCase = true) ->
                    throw Exception("Your account does not have ExHentai access (igneous=mystery) — disable ExHentai in E-Hentai settings or use an eligible account")
                else -> { /* valid igneous but empty results — pass through */ }
            }
        }
        return this
    }

    override val client: OkHttpClient by lazy {
        network.client.newBuilder()
            .addNetworkInterceptor(EHentaiInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val json: Json by lazy { Json { ignoreUnknownKeys = true } }

    // ── Browse: popular (= "newest" — EH has no universal popularity endpoint) ─

    override fun popularMangaRequest(page: Int): Request =
        buildBrowseRequest(page = page, sortParam = "")

    override fun popularMangaParse(response: Response): MangasPage =
        parseGalleryListPage(Jsoup.parse(response.body.string())).checkValid()

    // ── Browse: latest updates ─────────────────────────────────────────────────

    override fun latestUpdatesRequest(page: Int): Request =
        buildBrowseRequest(page = page, sortParam = "")

    override fun latestUpdatesParse(response: Response): MangasPage =
        parseGalleryListPage(Jsoup.parse(response.body.string())).checkValid()

    // ── Search ─────────────────────────────────────────────────────────────────

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = StringBuilder("$baseUrl/?")
        val queryParts = mutableListOf<String>()

        // Category filter (excluded bitmask)
        val catGroup = filters.filterIsInstance<CategoryGroup>().firstOrNull()
        val excludedMask = if (catGroup != null) {
            val enabledMask = catGroup.state
                .filterIsInstance<CategoryFilter>()
                .filter { it.state }
                .fold(0) { acc, f -> acc or f.bitmask }
            EHentaiConstants.CAT_FULL_MASK and enabledMask.inv()
        } else {
            defaultCategories
        }
        if (excludedMask > 0) urlBuilder.append("f_cats=$excludedMask&")

        // Sort order
        val sortFilter = filters.filterIsInstance<SortOrderFilter>().firstOrNull()
        val sortParam = EHentaiConstants.SORT_PARAMS[sortFilter?.state ?: 0] ?: ""
        if (sortParam.isNotEmpty()) urlBuilder.append("f_stor=$sortParam&")

        // Minimum rating
        val ratingFilter = filters.filterIsInstance<RatingFilter>().firstOrNull()
        val ratingValue = EHentaiConstants.RATING_FILTER_VALUES.getOrNull(ratingFilter?.state ?: 0) ?: ""
        if (ratingValue.isNotEmpty()) {
            urlBuilder.append("f_srdd=$ratingValue&")
            urlBuilder.append("f_srt=1&")
        }

        // Uploader
        val uploader = filters.filterIsInstance<UploaderFilter>().firstOrNull()?.state?.trim() ?: ""
        if (uploader.isNotEmpty()) queryParts.add("uploader:$uploader")

        // Language checkboxes → language:XX tags
        val langGroup = filters.filterIsInstance<LanguageGroup>().firstOrNull()
        langGroup?.state?.filterIsInstance<LanguageCheckBox>()
            ?.filter { it.state }
            ?.forEach { queryParts.add("language:${it.langTag}") }

        // Tag search field
        val tagSearch = filters.filterIsInstance<TagSearchFilter>().firstOrNull()?.state?.trim() ?: ""
        if (tagSearch.isNotEmpty()) queryParts.add(tagSearch)

        // Main query text
        if (query.isNotBlank()) queryParts.add(query.trim())

        // Page count
        val minPages = filters.filterIsInstance<MinPagesFilter>().firstOrNull()?.state?.trim() ?: ""
        val maxPages = filters.filterIsInstance<MaxPagesFilter>().firstOrNull()?.state?.trim() ?: ""
        if (minPages.isNotEmpty()) urlBuilder.append("f_spf=$minPages&")
        if (maxPages.isNotEmpty()) urlBuilder.append("f_spt=$maxPages&")

        val combinedQuery = queryParts.joinToString(" ")
        if (combinedQuery.isNotEmpty()) {
            urlBuilder.append("f_search=${combinedQuery.encodeUrl()}&")
            urlBuilder.append("f_apply=Apply+Filter&")
        }

        // Page number
        if (page > 1) urlBuilder.append("page=${page - 1}&")

        return GET(urlBuilder.toString().trimEnd('&', '?'), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage =
        parseGalleryListPage(Jsoup.parse(response.body.string())).checkValid()

    /**
     * URL import: if the search query is a pasted EH/ExH URL, resolve it directly
     * to a single manga — mirrors Komikku's urlImportFetchSearchMangaSuspend.
     */
    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val trimmedQuery = query.trim()
        if (trimmedQuery.startsWith("http")) {
            val uri = runCatching { android.net.Uri.parse(trimmedQuery) }.getOrNull()
            if (uri != null && matchesUri(uri)) {
                val mangaUrl = mapUrlToMangaUrl(uri)
                if (mangaUrl != null) {
                    val cleanUrl = cleanMangaUrl(mangaUrl)
                    val manga = getMangaDetails(SManga.create().apply { url = cleanUrl })
                    manga.url = cleanUrl
                    return MangasPage(listOf(manga), false)
                }
            }
        }
        return super.getSearchManga(page, query, filters)
    }

    // ── Manga details ──────────────────────────────────────────────────────────

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = Jsoup.parse(response.body.string())
        val manga = SManga.create()

        // Extract GID and Token from the URL
        val urlMatch = Regex("/g/(\\d+)/([a-f0-9]+)/?").find(response.request.url.toString())
        val gId = urlMatch?.groupValues?.get(1) ?: ""
        val gToken = urlMatch?.groupValues?.get(2) ?: ""

        // Title (prefer Japanese if setting is "japanese")
        val englishTitle = doc.selectFirst("#gn")?.text() ?: ""
        val japaneseTitle = doc.selectFirst("#gj")?.text() ?: ""
        manga.title = when {
            titleDisplayMode == "japanese" && japaneseTitle.isNotBlank() -> japaneseTitle
            else -> englishTitle
        }

        // Thumbnail — try cover div background-image first, then img tag
        manga.thumbnail_url = doc.selectFirst("#gd1 div")?.attr("style")?.let { style ->
            Regex("""url\((.+?)\)""").find(style)?.groupValues?.get(1)
        } ?: doc.selectFirst("#gd1 img, #cover")?.attr("src")

        // Uploader
        val uploader = doc.selectFirst("#gdn a")?.text()

        // Rating
        val ratingLabel = doc.selectFirst("#rating_label")?.text() ?: ""
        val ratingValue = Regex("""Average:\s*([\d.]+)""").find(ratingLabel)
            ?.groupValues?.get(1)?.toDoubleOrNull()
        val ratingCount = doc.selectFirst("#rating_count")?.text()?.toIntOrNull()

        // Category
        val category = doc.selectFirst("#gdc div")?.text()

        // Author / Artist from tag table
        val tagTable = doc.selectFirst("#taglist")
        val tags = mutableListOf<String>()
        var artist = ""
        var author = ""
        tagTable?.select("tr")?.forEach { row ->
            val namespace = row.selectFirst("td.tc")?.text()?.trimEnd(':') ?: ""
            row.select("td:not(.tc) a").forEach { anchor ->
                val tagName = anchor.attr("id")
                    .removePrefix("ta_")
                    .ifEmpty { anchor.text() }
                tags.add("$namespace:$tagName")
                when (namespace) {
                    "artist" -> if (artist.isEmpty()) artist = tagName
                    "group" -> if (author.isEmpty()) author = tagName
                }
            }
        }
        manga.artist = artist.ifEmpty { null }
        manga.author = author.ifEmpty { artist.ifEmpty { null } }

        // Parse metadata info table (#gdd)
        val infoMap = mutableMapOf<String, String>()
        doc.selectFirst("#gdd table")?.select("tr")?.forEach { row ->
            val label = row.selectFirst(".gdt1")?.text()?.trimEnd(':')?.trim() ?: ""
            val value = row.selectFirst(".gdt2")?.text()?.trim() ?: ""
            if (label.isNotBlank() && value.isNotBlank()) infoMap[label.lowercase()] = value
        }

        val datePosted = infoMap["posted"]
        val visible = infoMap["visible"]
        val language = infoMap["language"]
        val translated = language?.contains("TR", ignoreCase = true) == true
        val langName = language?.replace(Regex("""\s*TR$""", RegexOption.IGNORE_CASE), "")?.trim()
        val fileSize = infoMap["file size"]
        val length = infoMap["length"]?.replace(Regex("""\D+"""), "")?.toIntOrNull()
        val favorites = infoMap["favorited"]?.replace(Regex("""\D+"""), "")?.toIntOrNull()
        val parent = doc.selectFirst("#gdd a[href*='/g/']")?.attr("href")

        // Build metadata object
        val metadata = EHentaiGalleryMetadata(
            gId = gId,
            gToken = gToken,
            exh = useExHentai,
            thumbnailUrl = manga.thumbnail_url,
            title = englishTitle.ifBlank { null },
            altTitle = japaneseTitle.ifBlank { null },
            genre = category,
            datePosted = datePosted,
            visible = visible,
            language = langName,
            translated = translated,
            fileSize = fileSize,
            length = length,
            favorites = favorites,
            rating = ratingValue,
            ratingCount = ratingCount,
            uploader = uploader,
            parent = parent,
            lastUpdateCheck = System.currentTimeMillis(),
        )

        // Build human-readable description
        val humanDescription = buildString {
            if (uploader != null) appendLine("Uploader: $uploader")
            if (ratingValue != null) {
                append("Rating: ★ $ratingValue")
                if (ratingCount != null) append(" ($ratingCount)")
                appendLine()
            }
            if (datePosted != null) appendLine("Posted: $datePosted")
            if (langName != null) {
                append("Language: $langName")
                if (translated) append(" (Translated)")
                appendLine()
            }
            if (fileSize != null) appendLine("File Size: $fileSize")
            if (length != null) appendLine("Pages: $length")
            if (favorites != null) appendLine("Favorited: $favorites times")
            if (visible != null) appendLine("Visible: $visible")
        }.trim()

        manga.description = EHentaiGalleryMetadata.encode(metadata, humanDescription)

        // Genre = category + all namespace:tag pairs
        manga.genre = if (category != null) {
            (listOf(category) + tags).joinToString(", ")
        } else {
            tags.joinToString(", ")
        }

        manga.status = SManga.COMPLETED
        manga.initialized = true
        return manga
    }

    // ── Chapter list (one chapter per gallery) ─────────────────────────────────

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = Jsoup.parse(response.body.string())
        val chapter = SChapter.create()

        chapter.name = "Gallery"
        chapter.url = response.request.url.encodedPath + "?nw=session"
        chapter.chapter_number = 1f

        // Use posted date
        val posted = doc.selectFirst("#gdd tr:has(.gdt1:containsOwn(Posted:)) .gdt2")?.text()
        if (posted != null) {
            try {
                chapter.date_upload = DATE_FORMAT.parse(posted)?.time ?: 0L
            } catch (_: Exception) {
                chapter.date_upload = 0L
            }
        }

        return listOf(chapter)
    }

    override fun chapterPageParse(response: Response): SChapter {
        // Not used — chapters come from chapterListParse
        throw UnsupportedOperationException()
    }

    // ── Page list ──────────────────────────────────────────────────────────────

    /**
     * Overrides getPageList to recursively handle multi-page gallery listings
     * (EH splits galleries > 40 thumbnails across multiple listing pages).
     * 1:1 with Komikku's fetchChapterPage logic.
     */
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val startUrl = baseUrl + chapter.url
        return fetchAllGalleryPages(startUrl)
            .mapIndexed { index, pageUrl -> Page(index, pageUrl) }
    }

    /** Recursively collects all page viewer URLs from a gallery listing. */
    private suspend fun fetchAllGalleryPages(
        listingUrl: String,
        accumulated: List<String> = emptyList(),
    ): List<String> {
        val response = client.newCall(GET(listingUrl, headers)).awaitSuccess()
        val doc = Jsoup.parse(response.body.string())

        // Parse page thumbnail links — sort by the page number encoded in alt/title attributes
        val pageUrls = doc.select(".gdtm a").map {
            Pair(it.child(0).attr("alt").toIntOrNull() ?: 0, it.attr("href"))
        }.plus(
            doc.select("#gdt a").map {
                Pair(
                    it.child(0).attr("title").removePrefix("Page ").substringBefore(":").toIntOrNull() ?: 0,
                    it.attr("href"),
                )
            },
        ).sortedBy { it.first }.map { it.second }

        val allUrls = accumulated + pageUrls

        // Navigate to next listing page if present (">") — 1:1 with Komikku nextPageUrl
        val nextUrl = doc.select("a[onclick=return false]").lastOrNull()
            ?.takeIf { it.text() == ">" }
            ?.attr("href")

        return if (nextUrl != null) fetchAllGalleryPages(nextUrl, allUrls) else allUrls
    }

    /** Not used — getPageList handles all fetching. */
    override fun pageListParse(response: Response): List<Page> =
        throw UnsupportedOperationException("Unused — see getPageList")

    // ── Image URL ──────────────────────────────────────────────────────────────

    /**
     * Fetches the full-resolution image URL for a page viewer URL.
     * 1:1 with Komikku realImageUrlParse:
     *  - Detects page-quota exceeded (509 gif) and throws
     *  - Extracts nl param from #loadfail for retry-on-other-server logic
     */
    override suspend fun getImageUrl(page: Page): String {
        val response = client.newCall(imageUrlRequest(page)).awaitSuccess()
        val doc = Jsoup.parse(response.body.string())

        val imageUrl = doc.getElementById("img")?.attr("src")
            ?: throw Exception("Could not extract image URL from page")

        // EH/ExH quota exceeded — shown as a 509 gif placeholder
        if (imageUrl == "https://ehgt.org/g/509.gif") {
            throw Exception("Exceeded page quota — try again later")
        }

        // Extract nl (next-server) param from the retry button so the caller can
        // retry on a different image server if needed
        doc.selectFirst("#loadfail")?.attr("onclick")
            ?.takeIf { it.isNotBlank() }
            ?.let { onclick ->
                val nl = onclick.substring(onclick.indexOf('\'') + 1 until onclick.lastIndexOf('\''))
                page.url = addQueryParam(page.url, "nl", nl)
            }

        return imageUrl
    }

    /** Not used — getImageUrl handles image URL resolution. */
    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("Unused — see getImageUrl")

    // ── Filters ───────────────────────────────────────────────────────────────

    override fun getFilterList(): FilterList = getEHentaiFilters()

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun buildBrowseRequest(page: Int, sortParam: String): Request {
        val sb = StringBuilder("$baseUrl/?")
        val excluded = defaultCategories
        if (excluded > 0) sb.append("f_cats=$excluded&")
        if (sortParam.isNotEmpty()) sb.append("f_stor=$sortParam&")
        if (page > 1) sb.append("page=${page - 1}&")
        return GET(sb.toString().trimEnd('&', '?'), headers)
    }

    private fun parseGalleryListPage(doc: Document): MangasPage {
        val mangas = mutableListOf<SManga>()

        // Extended (table) layout — selector covers both table rows
        val galleryItems = doc.select(
            // Extended list view rows
            "table.itg tr.gl1e, " +
                // Thumbnail grid items (dm_t view)
                "div.gl1t, " +
                // Compact list view rows
                "table.itg tr:not(.glh)",
        )

        for (el in galleryItems) {
            val manga = parseGalleryElement(el) ?: continue
            mangas.add(manga)
        }

        val hasNextPage = doc.selectFirst("td#unext a") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun parseGalleryElement(el: Element): SManga? {
        // Locate the gallery link — works across display modes
        val link = el.selectFirst("a[href*='/g/']") ?: return null
        val url = link.attr("href").toRelativeUrl() ?: return null

        val manga = SManga.create()
        manga.url = url

        // Title
        manga.title = el.selectFirst(".glink, .gl3e .glink, a > div.glink")?.text()
            ?: link.attr("title").ifEmpty { link.text() }

        // Thumbnail
        manga.thumbnail_url = el.selectFirst("img[src]")?.attr("src")
            ?: el.selectFirst("img[data-src]")?.attr("data-src")

        // Category as genre (when available without an extra request)
        val category = el.selectFirst(".gl5e .cl, .cs, [class^='cs']")?.text()
            ?: el.selectFirst("div[class*='ctd']")?.text()

        // Extended layout metadata (sl=dm_2)
        val uploader = el.selectFirst("td.gl5e div:has(a[href*='uploader']) a, div.gl3e a[href*='uploader']")?.text()
            ?: el.selectFirst("td.gl5e .glhide div:nth-child(4), div.gl5e div:nth-child(4)")?.text()
        val ratingStyle = el.selectFirst(".ir")?.attr("style") ?: ""
        val ratingVal = parseStarRating(ratingStyle)
        val pages = el.selectFirst("td.gl5e div:last-child, div.gl3e div:last-child")?.text()
            ?.let { Regex("""(\d+)\s*pages?""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
        val posted = el.selectFirst(".gl5e .glhide div:first-child, div[id^='posted_']")?.text()
        // Language row in extended mode: "Language: Japanese"
        val langText = el.select("td.gl5e div").map { it.text() }
            .firstOrNull { it.lowercase().startsWith("language") }
            ?.substringAfter(":")?.trim()
            ?.takeIf { it.isNotBlank() && it.lowercase() != "n/a" }

        // EH_LIST: pipe-separated metadata used by EHentaiBrowseListItem composable
        // Format: category|uploader|rating|pages|posted|language
        manga.description = "EH_LIST:${category.orEmpty()}|${uploader.orEmpty()}|${ratingVal ?: ""}|${pages.orEmpty()}|${posted.orEmpty()}|${langText.orEmpty()}"

        // Genre: category for search/filter compatibility
        manga.genre = category?.ifBlank { null }

        return manga
    }

    /**
     * Parses E-Hentai's CSS-based star rating.
     * The `.ir` element uses background-position to encode the rating:
     *   - x-offset: each full star shifts -16px; half star adds -8px
     *   - y-offset: -1px means positive (brighter), -21px means dimmer
     * Returns a rounded rating value like "4.5" or null if not parseable.
     */
    private fun parseStarRating(style: String): String? {
        val xMatch = Regex("""background-position:\s*(-?\d+)px\s+(-?\d+)px""").find(style) ?: return null
        val x = xMatch.groupValues[1].toIntOrNull() ?: return null
        val y = xMatch.groupValues[2].toIntOrNull() ?: return null
        val baseStars = 5.0 + (x.toDouble() / 16.0)
        val halfAdjust = if (y == -21) 0.0 else 0.0 // y offset doesn't affect value, only color
        val rating = (baseStars + halfAdjust).coerceIn(0.0, 5.0)
        return if (rating == rating.toLong().toDouble()) {
            rating.toLong().toString()
        } else {
            "%.1f".format(rating)
        }
    }

    /** Extracts relative path `/g/GID/TOKEN/` from a full URL. */
    private fun String.toRelativeUrl(): String? {
        val match = Regex("/g/(\\d+)/([a-f0-9]+)/?").find(this) ?: return null
        return match.value
    }

    private fun String.encodeUrl(): String =
        java.net.URLEncoder.encode(this, "UTF-8")

    /** Appends or replaces a query parameter in a URL string. */
    private fun addQueryParam(url: String, key: String, value: String): String {
        val idx = url.indexOf('?')
        val base = if (idx >= 0) url.substring(0, idx) else url
        val existing = if (idx >= 0) url.substring(idx + 1) else ""
        val params = existing.split("&")
            .filter { it.isNotBlank() && !it.startsWith("$key=") }
            .toMutableList()
        params.add("$key=$value")
        return "$base?${params.joinToString("&")}"
    }

    // ── UrlImportableSource implementation ─────────────────────────────────────

    /**
     * Maps a user-pasted EH/ExH URL to a manga URL.
     * - /g/ paths are already gallery URLs — return as-is.
     * - /s/ paths are individual page URLs — use gtoken API to resolve to gallery.
     */
    override suspend fun mapUrlToMangaUrl(uri: Uri): String? {
        return when (uri.pathSegments.firstOrNull()) {
            "g" -> uri.toString()           // Already a gallery URL
            "s" -> getGalleryUrlFromPage(uri) // Single-page URL → resolve gallery
            else -> null
        }
    }

    /**
     * Strips host/scheme and normalises to `/g/GID/TOKEN/`.
     * Mirrors EHentaiSearchMetadata.normalizeUrl in Komikku.
     */
    override fun cleanMangaUrl(url: String): String {
        return Regex("/g/(\\d+)/([a-f0-9]+)/?").find(url)?.value
            ?: super.cleanMangaUrl(url)
    }

    /**
     * Resolves a single-page EH URL (/s/...) to its parent gallery URL.
     * Uses the EH gtoken API — 1:1 with Komikku getGalleryUrlFromPage.
     */
    private suspend fun getGalleryUrlFromPage(uri: Uri): String {
        val lastSplit = uri.pathSegments.last().split("-")
        val pageNum = lastSplit.last()
        val gallery = lastSplit.first()
        val pageToken = uri.pathSegments.elementAt(1)

        val body = buildJsonObject {
            put("method", "gtoken")
            put(
                "pagelist",
                buildJsonArray {
                    add(
                        buildJsonArray {
                            add(gallery.toInt())
                            add(pageToken)
                            add(pageNum.toInt())
                        },
                    )
                },
            )
        }.toString().toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(EHentaiConstants.API_URL)
            .post(body)
            .build()

        val response = client.newCall(request).awaitSuccess()
        val root = json.parseToJsonElement(response.body.string()).jsonObject
        val obj = root["tokenlist"]!!.jsonArray.first().jsonObject

        return "${uri.scheme}://${uri.host}/g/${obj["gid"]!!.jsonPrimitive.int}/${obj["token"]!!.jsonPrimitive.content}/"
    }

    // ── E-Hentai gdata API (used for bulk metadata fetching) ──────────────────

    /**
     * Fetches full metadata for [gidList] (list of gid/token pairs) using the
     * E-Hentai metadata API.  Returns a map of gid → [JsonObject].
     */
    @Suppress("UNUSED")
    internal suspend fun fetchGalleryMetadata(gidList: List<Pair<Long, String>>): Map<Long, JsonObject> {
        val body = buildJsonObject {
            put("method", "gdata")
            putJsonArray("gidlist") {
                gidList.forEach { (gid, token) ->
                    add(
                        buildJsonObject {
                            put("gid", gid)
                            put("token", token)
                        },
                    )
                }
            }
            put("namespace", 1)
        }.toString().toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(EHentaiConstants.API_URL)
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        val jsonStr = response.body.string()
        val root = json.parseToJsonElement(jsonStr).jsonObject
        val metadata = root["gmetadata"]?.jsonArray ?: return emptyMap()

        return metadata.associate { element ->
            val obj = element.jsonObject
            val gid = obj["gid"]!!.jsonPrimitive.content.toLong()
            gid to obj
        }
    }
}
