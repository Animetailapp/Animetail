package tachiyomi.source.local.entries.anime

import android.content.Context
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.UnmeteredSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimeRelation
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeEpisodeUpdate
import eu.kanade.tachiyomi.animesource.model.SAnimeSeasonUpdate
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.storage.toFFmpegString
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.storage.nameWithoutExtension
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.core.metadata.tachiyomi.AnimeDetails
import tachiyomi.core.metadata.tachiyomi.EpisodeDetails
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.service.EpisodeRecognition
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.source.local.filter.anime.AnimeOrderBy
import tachiyomi.source.local.image.anime.LocalAnimeBackgroundManager
import tachiyomi.source.local.image.anime.LocalAnimeCoverManager
import tachiyomi.source.local.image.anime.LocalEpisodeThumbnailManager
import tachiyomi.source.local.io.ArchiveAnime
import tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import eu.kanade.tachiyomi.animesource.model.Credit as SourceCredit

@Inject
@SingleIn(AppScope::class)
class LocalAnimeSource(
    private val context: Context,
    private val fileSystem: LocalAnimeSourceFileSystem,
    private val coverManager: LocalAnimeCoverManager,
    private val backgroundManager: LocalAnimeBackgroundManager,
    private val thumbnailManager: LocalEpisodeThumbnailManager,
    private val fetchTypeManager: LocalAnimeFetchTypeManager,
    private val json: Json,
) : AnimeCatalogueSource, UnmeteredSource {

    @Suppress("PrivatePropertyName")
    private val PopularFilters = AnimeFilterList(AnimeOrderBy.Popular(context))

    @Suppress("PrivatePropertyName")
    private val LatestFilters = AnimeFilterList(AnimeOrderBy.Latest(context))

    override val name = context.stringResource(AYMR.strings.local_anime_source)

    override val id: Long = ID

    override val lang = "other"

    override fun toString() = name

    override val supportsLatest = true

    // Browse related
    override suspend fun getPopularAnime(page: Int) = getSearchAnime(page, "", PopularFilters)

    override suspend fun getLatestUpdates(page: Int) = getSearchAnime(page, "", LatestFilters)

    override suspend fun getSearchAnime(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): AnimesPage = withIOContext {
        val lastModifiedLimit = if (filters === LatestFilters) {
            System.currentTimeMillis() - LATEST_THRESHOLD
        } else {
            0L
        }

        var animeDirs = fileSystem.getFilesInBaseDirectory()
            // Filter out files that are hidden and is not a folder
            .filter { it.isDirectory && !it.name.orEmpty().startsWith('.') }
            .distinctBy { it.name }
            .filter {
                if (lastModifiedLimit == 0L && query.isBlank()) {
                    true
                } else if (lastModifiedLimit == 0L) {
                    it.name.orEmpty().contains(query, ignoreCase = true)
                } else {
                    it.lastModified() >= lastModifiedLimit
                }
            }

        filters.forEach { filter ->
            when (filter) {
                is AnimeOrderBy.Popular -> {
                    animeDirs = if (filter.state!!.ascending) {
                        animeDirs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() })
                    } else {
                        animeDirs.sortedWith(
                            compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() },
                        )
                    }
                }

                is AnimeOrderBy.Latest -> {
                    animeDirs = if (filter.state!!.ascending) {
                        animeDirs.sortedBy(UniFile::lastModified)
                    } else {
                        animeDirs.sortedByDescending(UniFile::lastModified)
                    }
                }

                else -> {
                    /* Do nothing */
                }
            }
        }

        // Transform animeDirs to list of SAnime
        val animes = animeDirs
            .map { animeDir ->
                async {
                    getSAnime(animeDir.name)
                }
            }
            .awaitAll()

        AnimesPage(animes.toList(), false)
    }

    private fun getSAnime(animeDir: String?): SAnime {
        val cleanDir = animeDir.orEmpty().replace('\\', '/')
        return SAnime.create().apply {
            title = cleanDir.substringAfterLast('/')
            url = cleanDir
            fetch_type = fetchTypeManager.find(cleanDir)

            // Try to find the cover
            coverManager.find(cleanDir)?.let {
                thumbnail_url = it.uri.toString()
            }

            // Try to find the background
            backgroundManager.find(cleanDir)?.let {
                background_url = it.uri.toString()
            }
        }
    }

    override suspend fun getAnimeSeasonUpdate(
        anime: SAnime,
        seasons: List<SAnime>,
        fetchDetails: Boolean,
        fetchSeasons: Boolean,
    ): SAnimeSeasonUpdate = supervisorScope {
        val asyncAnime = if (fetchDetails) async { getOldAnimeDetails(anime) } else null
        val asyncSeasons = if (fetchSeasons) async { getOldSeasonList(anime) } else null
        SAnimeSeasonUpdate(asyncAnime?.await() ?: anime, asyncSeasons?.await() ?: seasons)
    }

    override val supportsRelatedAnime = false

    override suspend fun getRelatedAnimeList(anime: SAnime): List<AnimeRelation> = emptyList()

    // SY -->
    fun updateAnimeInfo(anime: SAnime) {
        val directory = fileSystem.getFilesInBaseDirectory().map { File(it.filePath, anime.url) }.find {
            it.exists()
        } ?: return
        val existingFileName = directory.listFiles()?.find { it.extension == "json" }?.name
        val file = File(directory, existingFileName ?: "info.json")
        file.outputStream().use {
            json.encodeToStream(anime.toJson(), it)
        }
    }

    override suspend fun getAnimeEpisodeUpdate(
        anime: SAnime,
        episodes: List<SEpisode>,
        fetchDetails: Boolean,
        fetchEpisodes: Boolean,
    ): SAnimeEpisodeUpdate = supervisorScope {
        val asyncAnime = if (fetchDetails) async { getOldAnimeDetails(anime) } else null
        val asyncEpisodes = if (fetchEpisodes) async { getOldEpisodeList(anime) } else null
        SAnimeEpisodeUpdate(asyncAnime?.await() ?: anime, asyncEpisodes?.await() ?: episodes)
    }

    private fun SAnime.toJson(): AnimeDetails {
        // Map SAnime fields to AnimeDetails, including cast if present
        return AnimeDetails(
            title = title,
            author = author,
            artist = artist,
            description = description,
            genre = genre?.split(", "),
            status = status,
            cast = cast?.map {
                SourceCredit(name = it.name, role = it.role, character = it.character, image_url = it.image_url)
            },
        )
    }
    // SY <--

    // Anime details related
    private suspend fun getOldAnimeDetails(anime: SAnime): SAnime = withIOContext {
        coverManager.find(anime.url)?.let {
            anime.thumbnail_url = it.uri.toString()
        }

        backgroundManager.find(anime.url)?.let {
            anime.background_url = it.uri.toString()
        }

        val animeDirFiles = fileSystem.getFilesInAnimeDirectory(anime.url)

        animeDirFiles
            .firstOrNull { it.extension == "json" && it.nameWithoutExtension == "details" }
            ?.let { file ->
                runCatching {
                    json.decodeFromStream<AnimeDetails>(file.openInputStream()).run {
                        title?.let { anime.title = it }
                        author?.let { anime.author = it }
                        artist?.let { anime.artist = it }
                        description?.let { anime.description = it }
                        genre?.let { anime.genre = it.joinToString() }
                        status?.let { anime.status = it }
                        cast?.let { coreCastList ->
                            anime.cast =
                                coreCastList.map { core ->
                                    SourceCredit(
                                        name = core.name,
                                        role = core.role,
                                        character = core.character,
                                        image_url = core.image_url,
                                    )
                                }
                        }
                    }
                }
            }

        return@withIOContext anime
    }

    // Seasons
    private suspend fun getOldSeasonList(anime: SAnime): List<SAnime> = withIOContext {
        val animeDirs = fileSystem.getFilesInAnimeDirectory(anime.url)
            // Filter out files that are hidden and is not a folder
            .filter { it.isDirectory && !it.name.orEmpty().startsWith('.') }
            .distinctBy { it.name }

        animeDirs
            .map { animeDir ->
                async {
                    val seasonName = animeDir.name.orEmpty()
                    val url = if (anime.url.isBlank()) seasonName else "${anime.url}/$seasonName"
                    getSAnime(url)
                }
            }
            .awaitAll()
            .toList()
    }

    override suspend fun getAnimeDetails(anime: SAnime): SAnime = getOldAnimeDetails(anime)

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = getOldEpisodeList(anime)

    override suspend fun getSeasonList(anime: SAnime): List<SAnime> = getOldSeasonList(anime)

    // Episodes
    private suspend fun getOldEpisodeList(anime: SAnime): List<SEpisode> = withIOContext {
        val allFilesInDir = fileSystem.getFilesInAnimeDirectory(anime.url)

        val episodesData = allFilesInDir
            .firstOrNull {
                it.extension == "json" && it.nameWithoutExtension == "episodes"
            }?.let { file ->
                runCatching {
                    json.decodeFromStream<List<EpisodeDetails>>(file.openInputStream())
                }.getOrNull()
            }

        val episodeEntries = mutableListOf<Pair<String, UniFile>>()
        for (file in allFilesInDir) {
            if (file.name.orEmpty().startsWith('.')) continue
            if (ArchiveAnime.isSupported(file)) {
                episodeEntries.add(file.name.orEmpty() to file)
            } else if (file.isDirectory) {
                file.listFiles().orEmpty().forEach { subFile ->
                    if (!subFile.name.orEmpty().startsWith('.') && ArchiveAnime.isSupported(subFile)) {
                        episodeEntries.add("${file.name}/${subFile.name}" to subFile)
                    }
                }
            }
        }

        val episodes = episodeEntries
            .map { (relativePath, episodeFile) ->
                SEpisode.create().apply {
                    url = "${anime.url}/$relativePath"
                    name = episodeFile.nameWithoutExtension.orEmpty()
                    date_upload = episodeFile.lastModified()

                    val episodeNumber = EpisodeRecognition.parseEpisodeNumber(
                        anime.title,
                        this.name,
                        this.episode_number.toDouble(),
                    ).toFloat()
                    episode_number = episodeNumber

                    // Overwrite data from episodes.json file
                    episodesData?.also { dataList ->
                        dataList.firstOrNull { it.episode_number.equalsTo(episodeNumber) }?.also { data ->
                            data.name?.also { name = it }
                            data.date_upload?.also { date_upload = parseDate(it) }
                            scanlator = data.scanlator
                            summary = data.summary
                        }
                    }

                    // Check if thumbnail already exists in directory
                    val existingThumbnail = thumbnailManager.find(anime.url, "${this.name}-$DEFAULT_THUMBNAIL_NAME")
                    if (existingThumbnail != null) {
                        this.preview_url = existingThumbnail.uri.toString()
                    }
                }
            }
            .sortedWith { e1, e2 ->
                val e = e2.episode_number.compareTo(e1.episode_number)
                if (e == 0) e2.name.compareToCaseInsensitiveNaturalOrder(e1.name) else e
            }

        // Generate the cover from the first episode found if not available
        if (anime.thumbnail_url == null || coverManager.find(anime.url) == null) {
            try {
                episodes.lastOrNull()?.let { episode ->
                    val tempFileSuffix = DEFAULT_COVER_NAME
                    val updateCover: (InputStream) -> Unit = { coverManager.update(anime, it) }
                    updateImageFromVideo(episode, anime, tempFileSuffix, updateCover)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Couldn't extract cover from video: $e" }
            }
        }

        // Generate the background from the first episode found if not available
        if (anime.background_url == null || backgroundManager.find(anime.url) == null) {
            try {
                episodes.lastOrNull()?.let { episode ->
                    val tempFileSuffix = DEFAULT_BACKGROUND_NAME
                    val updateBackground: (InputStream) -> Unit = { backgroundManager.update(anime, it) }
                    updateImageFromVideo(episode, anime, tempFileSuffix, updateBackground)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Couldn't extract background from video: $e" }
            }
        }

        episodes
    }

    private fun parseDate(isoDate: String): Long {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(isoDate)?.time ?: 0L
    }

    private fun Float.equalsTo(other: Float): Boolean {
        return abs(this - other) < 0.0001
    }

    // Filters
    override fun getFilterList() = AnimeFilterList(AnimeOrderBy.Popular(context))

    // Unused stuff
    override suspend fun getVideoList(episode: SEpisode): List<Video> = throw UnsupportedOperationException("Unused")

    private fun updateImageFromVideo(
        episode: SEpisode,
        anime: SAnime,
        tempFileSuffix: String,
        updateImage: (InputStream) -> Unit,
    ) {
        try {
            val tempFile = File.createTempFile("tmp_", ".jpg", context.cacheDir)
            val outFile = tempFile.path

            val relativePath = episode.url.removePrefix("${anime.url}/").replace('\\', '/')
            val animeDir = fileSystem.getAnimeDirectory(anime.url) ?: return
            val episodeFile = if (relativePath.contains('/')) {
                val subDir = relativePath.substringBeforeLast('/')
                val filename = relativePath.substringAfterLast('/')
                fileSystem.getAnimeDirectory("${anime.url}/$subDir")?.findFile(filename)
                    ?: animeDir.findFile(relativePath)
            } else {
                animeDir.findFile(relativePath)
            } ?: return
            val episodeFilename = if (episodeFile.uri.scheme == "content") {
                com.arthenica.ffmpegkit.FFmpegKitConfig.getSafParameter(context, episodeFile.uri, "r")
            } else {
                episodeFile.filePath ?: episodeFile.uri.path ?: return
            }

            com.arthenica.ffmpegkit.FFmpegKit.execute(
                "-ss 5 -i \"$episodeFilename\" -frames:v 1 -update true \"$outFile\" -y",
            )

            if (tempFile.exists() && tempFile.length() > 0L) {
                updateImage(tempFile.inputStream())
            }
            tempFile.delete()
        } catch (e: Throwable) {
            android.util.Log.e("LocalAnime", "Failed to extract image from video: $e")
        }
    }

    companion object {
        const val ID = 0L
        const val HELP_URL = "https://aniyomi.org/help/guides/local-anime/"

        private const val DEFAULT_COVER_NAME = "cover.jpg"
        private const val DEFAULT_BACKGROUND_NAME = "background.jpg"
        private const val DEFAULT_THUMBNAIL_NAME = "thumbnail.jpg"
        private val LATEST_THRESHOLD = TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS)
    }
}

fun Anime.isLocal(): Boolean = source == LocalAnimeSource.ID

fun AnimeSource.isLocal(): Boolean = id == LocalAnimeSource.ID
