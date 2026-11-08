package tachiyomi.data

import android.app.ActivityManager
import android.content.Context
import android.database.SQLException
import androidx.core.content.getSystemService
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteAfterVersion
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConcurrencyModel.MultipleReadersSingleWriter
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConnectionFactory
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
import mihon.data.database.DatabaseRepositoryImpl
import tachiyomi.data.handlers.anime.AndroidAnimeDatabaseHandler
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.mi.data.AnimeDatabase

@ContributesTo(AppScope::class)
@BindingContainer
object DatabaseBindings {

    /**
     * WAL on every device, so a write never blocks reads. Low-RAM devices get one reader instead of four, since each
     * connection keeps its own page cache.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun providesSqlDriver(context: Context, databaseRepository: DatabaseRepositoryImpl): SqlDriver {
        val isLowRam = context.getSystemService<ActivityManager>()?.isLowRamDevice == true
        return AndroidxSqliteDriver(
            connectionFactory = object : AndroidxSqliteConnectionFactory {
                override val driver: SQLiteDriver = BundledSQLiteDriver()

                override fun createConnection(name: String): SQLiteConnection {
                    return driver.open(name).apply {
                        execSQL("PRAGMA busy_timeout = 3000")
                    }
                }
            },
            databaseType = AndroidxSqliteDatabaseType.FileProvider(context, "tachiyomi.db"),
            schema = Database.Schema,
            configuration = AndroidxSqliteConfiguration(
                isForeignKeyConstraintsEnabled = true,
                concurrencyModel = MultipleReadersSingleWriter(isWal = true, walCount = if (isLowRam) 1 else 4),
            ),
            // Runs before the driver compares the schema version, and onOpen once the schema is ready
            onConfigure = {
                val version = executePragmaQuery("user_version", { QueryResult.Value(it.apply { next() }.getLong(0)) })
                databaseRepository.isMigrating.value = version in 1..<Database.Schema.version
            },
            onOpen = {
                databaseRepository.isMigrating.value = false
            },
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
        val isLowRam = context.getSystemService<ActivityManager>()?.isLowRamDevice == true
        val driver = AndroidxSqliteDriver(
            connectionFactory = object : AndroidxSqliteConnectionFactory {
                override val driver: SQLiteDriver = BundledSQLiteDriver()

                override fun createConnection(name: String): SQLiteConnection {
                    return driver.open(name).apply {
                        execSQL("PRAGMA busy_timeout = 3000")
                    }
                }
            },
            databaseType = AndroidxSqliteDatabaseType.FileProvider(context, "tachiyomi.animedb"),
            schema = AnimeDatabase.Schema,
            configuration = AndroidxSqliteConfiguration(
                isForeignKeyConstraintsEnabled = true,
                concurrencyModel = MultipleReadersSingleWriter(isWal = true, walCount = if (isLowRam) 1 else 4),
            ),
            onOpen = {
                ensureAnimeMemoColumnsAndViews()
            },
            migrationCallbacks = arrayOf(
                AndroidxSqliteAfterVersion(143) {
                    it.ensureAnimeMemoColumnsAndViews()
                },
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

internal suspend fun SqlDriver.ensureAnimeMemoColumnsAndViews() {
    try {
        execute(null, "ALTER TABLE animes ADD COLUMN memo BLOB NOT NULL DEFAULT x'7b7d'", 0).await()
    } catch (_: SQLException) {
    }
    try {
        execute(null, "ALTER TABLE episodes ADD COLUMN memo BLOB NOT NULL DEFAULT x'7b7d'", 0).await()
    } catch (_: SQLException) {
    }
    execute(null, "DROP VIEW IF EXISTS animelibView", 0).await()
    execute(
        null,
        """
        CREATE VIEW animelibView AS
        SELECT
            M.*,
            CASE M.fetch_type
                WHEN 1 THEN coalesce(ES.total, 0)
                WHEN 0 THEN coalesce(ASS.child_count, 0)
            END AS totalCount,
            CASE M.fetch_type
                WHEN 1 THEN coalesce(ES.seenCount, 0)
                WHEN 0 THEN coalesce(ASS.fully_seen_seasons, 0)
            END AS seenCount,
            CASE M.fetch_type
                WHEN 1 THEN coalesce(ES.latestUpload, 0)
                WHEN 0 THEN coalesce(ASS.max_latest_upload, 0)
            END AS latestUpload,
            CASE M.fetch_type
                WHEN 1 THEN coalesce(ES.fetchedAt, 0)
                WHEN 0 THEN coalesce(ASS.max_fetched_at, 0)
            END AS episodeFetchedAt,
            CASE M.fetch_type
                WHEN 1 THEN coalesce(AHS.lastSeen, 0)
                WHEN 0 THEN coalesce(ASS.max_last_seen, 0)
            END AS lastSeen,
            CASE M.fetch_type
                WHEN 1 THEN coalesce(ES.bookmarkCount, 0)
                WHEN 0 THEN coalesce(ASS.total_bookmarks, 0)
            END AS bookmarkCount,
            CASE M.fetch_type
                WHEN 1 THEN coalesce(ES.fillermarkCount, 0)
                WHEN 0 THEN coalesce(ASS.total_fillermarks, 0)
            END AS fillermarkCount,
            coalesce(MC.category_id, 0) AS category
        FROM animes M
        LEFT JOIN episodestatsView AS ES ON M._id = ES.anime_id
        LEFT JOIN animehistorystatsView AS AHS ON M._id = AHS.anime_id
        LEFT JOIN animes_categories AS MC ON MC.anime_id = M._id
        LEFT JOIN animeseasonstatsView AS ASS ON M._id = ASS.parent_id
        WHERE M.favorite = 1
        """.trimIndent(),
        0,
    ).await()
    execute(null, "DROP VIEW IF EXISTS animeseasonsView", 0).await()
    execute(
        null,
        """
        CREATE VIEW animeseasonsView AS
        SELECT
            M.*,
            CASE M.fetch_type
                WHEN 1 THEN coalesce(ES.total, 0)
                WHEN 0 THEN coalesce(ASS.child_count, 0)
            END AS totalCount,
            CASE M.fetch_type
                WHEN 1 THEN coalesce(ES.seenCount, 0)
                WHEN 0 THEN coalesce(ASS.fully_seen_seasons, 0)
            END AS seenCount,
            CASE M.fetch_type
                WHEN 1 THEN coalesce(ES.latestUpload, 0)
                WHEN 0 THEN coalesce(ASS.max_latest_upload, 0)
            END AS latestUpload,
            CASE M.fetch_type
                WHEN 1 THEN coalesce(ES.fetchedAt, 0)
                WHEN 0 THEN coalesce(ASS.max_fetched_at, 0)
            END AS episodeFetchedAt,
            CASE M.fetch_type
                WHEN 1 THEN coalesce(AHS.lastSeen, 0)
                WHEN 0 THEN coalesce(ASS.max_last_seen, 0)
            END AS lastSeen,
            CASE M.fetch_type
                WHEN 1 THEN coalesce(ES.bookmarkCount, 0)
                WHEN 0 THEN coalesce(ASS.total_bookmarks, 0)
            END AS bookmarkCount,
            CASE M.fetch_type
                WHEN 1 THEN coalesce(ES.fillermarkCount, 0)
                WHEN 0 THEN coalesce(ASS.total_fillermarks, 0)
            END AS fillermarkCount
        FROM animes AS M
        LEFT JOIN episodestatsView AS ES ON M._id = ES.anime_id
        LEFT JOIN animehistorystatsView AS AHS ON M._id = AHS.anime_id
        LEFT JOIN animeseasonstatsView AS ASS ON M._id = ASS.parent_id
        WHERE M.parent_id IS NOT NULL
        """.trimIndent(),
        0,
    ).await()
}
