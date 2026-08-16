package tachiyomi.data.source.anime

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.repository.AnimeStubSourceRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AnimeStubSourceRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : AnimeStubSourceRepository {

    override fun subscribeAllAnime(): Flow<List<StubAnimeSource>> {
        return handler.subscribeToList { animesourcesQueries.findAll(::mapStubSource) }
    }

    override suspend fun getStubAnimeSource(id: Long): StubAnimeSource? {
        return handler.awaitOneOrNull {
            animesourcesQueries.findOne(
                id,
                ::mapStubSource,
            )
        }
    }

    override suspend fun upsertStubAnimeSource(id: Long, lang: String, name: String) {
        handler.await { animesourcesQueries.upsert(id, lang, name) }
    }

    private fun mapStubSource(
        id: Long,
        lang: String,
        name: String,
    ): StubAnimeSource = StubAnimeSource(id = id, lang = lang, name = name)
}
