package mihon.app.di.injekt

import dev.zacsweers.metro.Inject
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton

@Inject
class MetroInteropModule(
    private val json: Json,
    private val protoBuf: ProtoBuf,
    private val xml: XML,

    private val networkHelper: NetworkHelper,
    private val javaScriptEngine: JavaScriptEngine,

    private val preferenceStore: PreferenceStore,
    private val trackPreferences: TrackPreferences,

    private val mangaExtensionManager: MangaExtensionManager,
    private val animeExtensionManager: AnimeExtensionManager,

    private val mangaCoverCache: MangaCoverCache,
    private val animeCoverCache: AnimeCoverCache,
    private val animeBackgroundCache: AnimeBackgroundCache,
    private val chapterCache: ChapterCache,

    private val mangaSourceManager: MangaSourceManager,
    private val animeSourceManager: AnimeSourceManager,

    private val mangaDownloadManager: MangaDownloadManager,
    private val animeDownloadManager: AnimeDownloadManager,

    private val mangaDownloadCache: MangaDownloadCache,
    private val animeDownloadCache: AnimeDownloadCache,
) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingleton(json)
        addSingleton(protoBuf)
        addSingleton(xml)

        addSingleton(networkHelper)
        addSingleton(javaScriptEngine)

        addSingleton(preferenceStore)
        addSingleton(trackPreferences)

        addSingleton(mangaExtensionManager)
        addSingleton(animeExtensionManager)

        addSingleton(mangaCoverCache)
        addSingleton(animeCoverCache)
        addSingleton(animeBackgroundCache)
        addSingleton(chapterCache)

        addSingleton(mangaSourceManager)
        addSingleton(animeSourceManager)

        addSingleton(mangaDownloadManager)
        addSingleton(animeDownloadManager)

        addSingleton(mangaDownloadCache)
        addSingleton(animeDownloadCache)
    }
}
