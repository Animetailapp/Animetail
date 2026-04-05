package eu.kanade.tachiyomi.data.download.anime

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.util.lang.Hash.md5
import eu.kanade.tachiyomi.util.size
import eu.kanade.tachiyomi.util.storage.DiskUtil
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.displayablePath
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import tachiyomi.source.local.entries.anime.isLocal
import tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * This class is used to provide the directories where the downloads should be saved.
 * It uses the following path scheme: /<root downloads dir>/<source name>/<anime>/<episode>
 *
 * @param context the application context.
 */
class AnimeDownloadProvider(
    private val context: Context,
    private val storageManager: StorageManager = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    // AM (FILE_SIZE) -->
    private val localFileSystem: LocalAnimeSourceFileSystem = Injekt.get(),
    // <-- AM (FILE_SIZE)
) {

    private val downloadsDir: UniFile?
        get() = storageManager.getDownloadsDirectory()

    /**
     * Returns the download directory for an anime. For internal use only.
     *
     * @param animeTitle the title of the anime to query.
     * @param source the source of the anime.
     */
    internal fun getAnimeDir(animeTitle: String, source: AnimeSource): UniFile {
        try {
            return downloadsDir!!
                .createDirectory(getSourceDirName(source))!!
                .createDirectory(getAnimeDirName(animeTitle))!!
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Invalid download directory" }
            throw Exception(
                context.stringResource(
                    MR.strings.invalid_location,
                    downloadsDir?.displayablePath ?: "",
                ),
            )
        }
    }

    /**
     * Returns the download directory for a source if it exists.
     *
     * @param source the source to query.
     */
    fun findSourceDir(source: AnimeSource): UniFile? {
        return downloadsDir?.findFile(getSourceDirName(source))
    }

    /**
     * Returns the download directory for an anime if it exists.
     *
     * @param animeTitle the title of the anime to query.
     * @param source the source of the anime.
     */
    fun findAnimeDir(animeTitle: String, source: AnimeSource): UniFile? {
        val sourceDir = findSourceDir(source)
        return sourceDir?.findFile(getAnimeDirName(animeTitle))
    }

    /**
     * Returns the download directory for an episode if it exists.
     *
     * @param episodeName the name of the episode to query.
     * @param episodeScanlator scanlator of the episode to query
     * @param episodeUrl url of the episode to query.
     * @param animeTitle the title of the anime to query.
     * @param source the source of the episode.
     */
    fun findEpisodeDir(
        episodeName: String,
        episodeScanlator: String?,
        episodeUrl: String,
        animeTitle: String,
        source: AnimeSource,
    ): UniFile? {
        val animeDir = findAnimeDir(animeTitle, source)
        return getValidEpisodeDirNames(episodeName, episodeScanlator, episodeUrl).asSequence()
            .mapNotNull { animeDir?.findFile(it) }
            .firstOrNull()
    }

    /**
     * Returns a list of downloaded directories for the episodes that exist.
     *
     * @param episodes the episodes to query.
     * @param anime the anime of the episode.
     * @param source the source of the episode.
     */
    fun findEpisodeDirs(episodes: List<Episode>, anime: Anime, source: AnimeSource): Pair<UniFile?, List<UniFile>> {
        val animeDir = findAnimeDir(anime.title, source) ?: return null to emptyList()
        return animeDir to episodes.mapNotNull { episode ->
            getValidEpisodeDirNames(episode.name, episode.scanlator, episode.url).asSequence()
                .mapNotNull { animeDir.findFile(it) }
                .firstOrNull()
        }
    }

    /**
     * Returns the download directory name for a source.
     *
     * @param source the source to query.
     */
    fun getSourceDirName(source: AnimeSource): String {
        return DiskUtil.buildValidFilename(
            source.toString(),
            disallowNonAscii = libraryPreferences.disallowNonAsciiFilenames.get(),
        )
    }

    /**
     * Returns the download directory name for an anime.
     *
     * @param animeTitle the title of the anime to query.
     */
    fun getAnimeDirName(animeTitle: String): String {
        return DiskUtil.buildValidFilename(
            animeTitle,
            disallowNonAscii = libraryPreferences.disallowNonAsciiFilenames.get(),
        )
    }

    /**
     * Returns the episode directory name for an episode.
     *
     * @param episodeName the name of the episode to query.
     * @param episodeScanlator scanlator of the episode to query.
     * @param episodeUrl url of the episode to query.
     */
    fun getEpisodeDirName(
        episodeName: String,
        episodeScanlator: String?,
        episodeUrl: String,
        disallowNonAsciiFilenames: Boolean = libraryPreferences.disallowNonAsciiFilenames.get(),
    ): String {
        var dirName = sanitizeEpisodeName(episodeName)
        if (!episodeScanlator.isNullOrBlank()) {
            dirName = episodeScanlator + "_" + dirName
        }
        // Subtract 7 bytes for hash and underscore, 4 bytes for .mkv/.mp4
        dirName = DiskUtil.buildValidFilename(dirName, DiskUtil.MAX_FILE_NAME_BYTES - 11, disallowNonAsciiFilenames)
        dirName += "_" + md5(episodeUrl).take(6)
        return dirName
    }

    /**
     * Returns list of names that might have been previously used as
     * the directory name for an episode.
     * Add to this list if naming pattern ever changes.
     *
     * @param episodeName the name of the episode to query.
     * @param episodeScanlator scanlator of the episode to query.
     * @param episodeUrl url of the episode to query.
     */
    private fun getLegacyEpisodeDirNames(
        episodeName: String,
        episodeScanlator: String?,
        episodeUrl: String,
    ): List<String> {
        val sanitizedEpisodeName = sanitizeEpisodeName(episodeName)
        val episodeNameV1 = DiskUtil.buildValidFilename(
            when {
                !episodeScanlator.isNullOrBlank() -> "${episodeScanlator}_$sanitizedEpisodeName"
                else -> sanitizedEpisodeName
            },
        )
        val oldEpisodeName = getOldEpisodeDirName(episodeName, episodeScanlator)

        // Get the filename that would be generated if the user were
        // using the other value for the disallow non-ASCII
        // filenames setting.
        val otherEpisodeDirName =
            getEpisodeDirName(
                episodeName,
                episodeScanlator,
                episodeUrl,
                !libraryPreferences.disallowNonAsciiFilenames.get(),
            )

        return buildList(3) {
            // Episode name without hash (unable to handle duplicate episode names)
            add(episodeNameV1)
            // Old format without scanlator prefix sanitization
            add(oldEpisodeName)
            add(otherEpisodeDirName)
        }
    }

    /**
     * Return the new name for the episode (in case it's empty or blank)
     *
     * @param episodeName the name of the episode
     */
    private fun sanitizeEpisodeName(episodeName: String): String {
        return episodeName.ifBlank {
            "Episode"
        }
    }

    /**
     * Returns the episode directory name for an episode.
     *
     * @param episodeName the name of the episode to query.
     * @param episodeScanlator scanlator of the episode to query
     */
    fun getOldEpisodeDirName(episodeName: String, episodeScanlator: String?): String {
        return DiskUtil.buildValidFilename(
            when {
                episodeScanlator != null -> "${episodeScanlator}_$episodeName"
                else -> episodeName
            },
        )
    }

    fun isEpisodeDirNameChanged(oldEpisode: Episode, newEpisode: Episode): Boolean {
        return getEpisodeDirName(oldEpisode.name, oldEpisode.scanlator, oldEpisode.url) !=
            getEpisodeDirName(newEpisode.name, newEpisode.scanlator, newEpisode.url)
    }

    /**
     * Returns valid downloaded episode directory names.
     *
     * @param episodeName the name of the episode to query.
     * @param episodeScanlator scanlator of the episode to query.
     * @param episodeUrl url of the episode to query.
     */
    fun getValidEpisodeDirNames(episodeName: String, episodeScanlator: String?, episodeUrl: String): List<String> {
        val episodeDirName = getEpisodeDirName(episodeName, episodeScanlator, episodeUrl)
        val legacyEpisodeDirNames = getLegacyEpisodeDirNames(episodeName, episodeScanlator, episodeUrl)

        return buildList {
            // Folder of video files
            add(episodeDirName)
            legacyEpisodeDirNames.forEach { add(it) }
        }
    }

    // AM (FILE_SIZE) -->

    /**
     * Returns an episode file size in bytes.
     * Returns null if the episode is not found in expected location
     *
     * @param episodeName the name of the episode to query.
     * @param episodeScanlator scanlator of the episode to query
     * @param animeTitle the title of the anime
     * @param animeSource the source of the anime
     */
    fun getEpisodeFileSize(
        episodeName: String,
        episodeUrl: String?,
        episodeScanlator: String?,
        animeTitle: String,
        animeSource: AnimeSource?,
    ): Long? {
        if (animeSource == null) return null
        return if (animeSource.isLocal()) {
            val (animeDirName, episodeDirName) = episodeUrl?.split('/', limit = 2) ?: return null
            localFileSystem.getBaseDirectory()?.findFile(animeDirName)?.findFile(episodeDirName)?.size()
        } else {
            findEpisodeDir(episodeName, episodeScanlator, episodeUrl.orEmpty(), animeTitle, animeSource)?.size()
        }
    }
    // <-- AM (FILE_SIZE)
}
