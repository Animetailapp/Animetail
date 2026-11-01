package tachiyomi.data.source.manga

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.source.manga.model.StubMangaSource
import tachiyomi.domain.source.manga.repository.MangaStubSourceRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class MangaStubSourceRepositoryImpl(
    private val database: Database,
) : MangaStubSourceRepository {

    override fun subscribeAllManga(): Flow<List<StubMangaSource>> {
        return database.sourceQueries
            .findAll(::mapStubSource)
            .subscribeToList()
    }

    override suspend fun getStubMangaSource(id: Long): StubMangaSource? {
        return database.sourceQueries
            .findOne(id, ::mapStubSource)
            .awaitAsOneOrNull()
    }

    override suspend fun upsertStubMangaSource(id: Long, lang: String, name: String) {
        database.sourceQueries.upsert(id = id, name = name, language = lang)
    }

    private fun mapStubSource(
        id: Long,
        name: String,
        lang: String,
    ): StubMangaSource = StubMangaSource(id = id, lang = lang, name = name)
}
