package tachiyomi.data

import android.database.SQLException
import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AnimeDatabaseMemoMigrationTest {

    @Test
    fun `ensureAnimeMemoColumnsAndViews awaits all async SQL statements`() = runTest {
        val executedSql = mutableListOf<String>()
        val driver = ColdAsyncSqlDriver { sql ->
            executedSql += sql
        }

        driver.ensureAnimeMemoColumnsAndViews()

        executedSql shouldHaveSize 6
        executedSql[0] shouldBe "ALTER TABLE animes ADD COLUMN memo BLOB NOT NULL DEFAULT x'7b7d'"
        executedSql[1] shouldBe "ALTER TABLE episodes ADD COLUMN memo BLOB NOT NULL DEFAULT x'7b7d'"
        executedSql[2] shouldBe "DROP VIEW IF EXISTS animelibView"
        executedSql[3] shouldContain "CREATE VIEW animelibView AS"
        executedSql[4] shouldBe "DROP VIEW IF EXISTS animeseasonsView"
        executedSql[5] shouldContain "CREATE VIEW animeseasonsView AS"
    }

    @Test
    fun `ensureAnimeMemoColumnsAndViews continues recreating views when memo columns already exist`() = runTest {
        val executedSql = mutableListOf<String>()
        val driver = ColdAsyncSqlDriver { sql ->
            executedSql += sql
            if (sql.startsWith("ALTER TABLE")) {
                throw mockk<SQLException>()
            }
        }

        driver.ensureAnimeMemoColumnsAndViews()

        executedSql shouldHaveSize 6
        executedSql[2] shouldBe "DROP VIEW IF EXISTS animelibView"
        executedSql[3] shouldContain "CREATE VIEW animelibView AS"
        executedSql[4] shouldBe "DROP VIEW IF EXISTS animeseasonsView"
        executedSql[5] shouldContain "CREATE VIEW animeseasonsView AS"
    }

    @Test
    fun `ensureAnimeMemoColumnsAndViews propagates view recreation failures`() = runTest {
        val driver = ColdAsyncSqlDriver { sql ->
            if (sql.contains("CREATE VIEW animelibView AS")) {
                throw mockk<SQLException>()
            }
        }

        shouldThrow<SQLException> {
            driver.ensureAnimeMemoColumnsAndViews()
        }
    }

    /**
     * Simulates [com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver], where [execute]
     * returns a cold [QueryResult.AsyncValue] that only runs when [QueryResult.AsyncValue.await] is invoked.
     */
    private class ColdAsyncSqlDriver(
        private val onAwaitExecute: suspend (String) -> Unit,
    ) : SqlDriver {
        override fun execute(
            identifier: Int?,
            sql: String,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<Long> = QueryResult.AsyncValue {
            onAwaitExecute(sql)
            0L
        }

        override fun <R> executeQuery(
            identifier: Int?,
            sql: String,
            mapper: (SqlCursor) -> QueryResult<R>,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<R> = throw UnsupportedOperationException()

        override fun newTransaction(): QueryResult<Transacter.Transaction> = throw UnsupportedOperationException()

        override fun currentTransaction(): Transacter.Transaction? = null

        override fun addListener(vararg queryKeys: String, listener: Query.Listener) = Unit

        override fun removeListener(vararg queryKeys: String, listener: Query.Listener) = Unit

        override fun notifyListeners(vararg queryKeys: String) = Unit

        override fun close() = Unit
    }
}
