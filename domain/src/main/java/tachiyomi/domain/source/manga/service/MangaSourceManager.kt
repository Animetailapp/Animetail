package tachiyomi.domain.source.manga.service

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.manga.model.StubMangaSource

interface MangaSourceManager {

    val catalogueSources: Flow<List<CatalogueSource>>

    suspend fun get(sourceKey: Long): MangaSource?

    suspend fun getOrStub(sourceKey: Long): MangaSource

    suspend fun getOnlineSources(): List<HttpSource>

    suspend fun getCatalogueSources(): List<CatalogueSource>

    suspend fun getStubSources(): List<StubMangaSource>
}
