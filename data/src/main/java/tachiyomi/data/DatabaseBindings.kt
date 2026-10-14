package tachiyomi.data

import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.db.SqlDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import com.eygraber.sqldelight.androidx.driver.FileProvider
import data.Chapter
import data.History
import data.Manga
import dataanime.Animehistory
import dataanime.Animes
import dataanime.Episodes
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import tachiyomi.data.handlers.anime.AndroidAnimeDatabaseHandler
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.mi.data.AnimeDatabase

@ContributesTo(AppScope::class)
@BindingContainer
object DatabaseBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun providesSqlDriver(context: Context): SqlDriver {
        return AndroidxSqliteDriver(
            driver = BundledSQLiteDriver(),
            databaseType = AndroidxSqliteDatabaseType.FileProvider(context, "tachiyomi.db"),
            schema = Database.Schema,
            configuration = AndroidxSqliteConfiguration(
                isForeignKeyConstraintsEnabled = true,
            ),
        )
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providesDatabase(driver: SqlDriver): Database {
        return Database(
            driver = driver,
            historyAdapter = History.Adapter(
                read_atAdapter = DateColumnAdapter,
            ),
            mangaAdapter = Manga.Adapter(
                remote_genreAdapter = StringListColumnAdapter,
                remote_update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
                remote_memoAdapter = MemoColumnAdapter,
            ),
            chapterAdapter = Chapter.Adapter(
                remote_memoAdapter = MemoColumnAdapter,
            ),
        )
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providesAnimeDatabaseHandler(context: Context): AnimeDatabaseHandler {
        val driver = AndroidxSqliteDriver(
            driver = BundledSQLiteDriver(),
            databaseType = AndroidxSqliteDatabaseType.FileProvider(context, "tachiyomi.animedb"),
            schema = AnimeDatabase.Schema,
            configuration = AndroidxSqliteConfiguration(
                isForeignKeyConstraintsEnabled = true,
            ),
        )
        val database = AnimeDatabase(
            driver = driver,
            animehistoryAdapter = Animehistory.Adapter(
                last_seenAdapter = DateColumnAdapter,
            ),
            animesAdapter = Animes.Adapter(
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = AnimeUpdateStrategyColumnAdapter,
                fetch_typeAdapter = FetchTypeColumnAdapter,
                castAdapter = CastColumnAdapter,
                memoAdapter = MemoColumnAdapter,
            ),
            episodesAdapter = Episodes.Adapter(
                memoAdapter = MemoColumnAdapter,
            ),
        )
        return AndroidAnimeDatabaseHandler(
            db = database,
            driver = driver,
        )
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providesAnimeDatabase(handler: AnimeDatabaseHandler): AnimeDatabase {
        return (handler as AndroidAnimeDatabaseHandler).db
    }
}
