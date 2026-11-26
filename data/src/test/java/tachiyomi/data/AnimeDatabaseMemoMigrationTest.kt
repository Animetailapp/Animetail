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

        executedSql shouldHaveSize 8
        executedSql[0] shouldBe "ALTER TABLE animes ADD COLUMN memo BLOB NOT NULL DEFAULT x'7b7d'"
        executedSql[1] shouldBe "ALTER TABLE episodes ADD COLUMN memo BLOB NOT NULL DEFAULT x'7b7d'"
        executedSql[2] shouldBe BEGIN
        executedSql[3] shouldBe "DROP VIEW IF EXISTS animelibView"
        executedSql[4] shouldContain "CREATE VIEW animelibView AS"
        executedSql[5] shouldBe "DROP VIEW IF EXISTS animeseasonsView"
        executedSql[6] shouldContain "CREATE VIEW animeseasonsView AS"
        executedSql[7] shouldBe COMMIT
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

        executedSql shouldHaveSize 8
        executedSql[2] shouldBe BEGIN
        executedSql[3] shouldBe "DROP VIEW IF EXISTS animelibView"
        executedSql[4] shouldContain "CREATE VIEW animelibView AS"
        executedSql[5] shouldBe "DROP VIEW IF EXISTS animeseasonsView"
        executedSql[6] shouldContain "CREATE VIEW animeseasonsView AS"
        executedSql[7] shouldBe COMMIT
    }

    @Test
    fun `ensureAnimeMemoColumnsAndViews propagates view recreation failures`() = runTest {
        val executedSql = mutableListOf<String>()
        val driver = ColdAsyncSqlDriver { sql ->
            executedSql += sql
            if (sql.contains("CREATE VIEW animelibView AS")) {
                throw mockk<SQLException>()
            }
        }

        shouldThrow<SQLException> {
            driver.ensureAnimeMemoColumnsAndViews()
        }
        // The dropped view is restored by the rollback instead of being left missing.
        executedSql.last() shouldBe ROLLBACK
    }

    /**
     * Simulates [com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver], where [execute]
     * returns a cold [QueryResult.AsyncValue] that only runs when [QueryResult.AsyncValue.await] is invoked.
     */
    private class ColdAsyncSqlDriver(
        private val onAwaitExecute: suspend (String) -> Unit,
    ) : SqlDriver {
        private var transaction: Transacter.Transaction? = null

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

        override fun newTransaction(): QueryResult<Transacter.Transaction> = QueryResult.AsyncValue {
            onAwaitExecute(BEGIN)
            val enclosing = transaction
            object : Transacter.Transaction() {
                override val enclosingTransaction = enclosing

                override fun endTransaction(successful: Boolean): QueryResult<Unit> = QueryResult.AsyncValue {
                    transaction = enclosing
                    onAwaitExecute(if (successful) COMMIT else ROLLBACK)
                }
            }.also { transaction = it }
        }

        override fun currentTransaction(): Transacter.Transaction? = transaction

        override fun addListener(vararg queryKeys: String, listener: Query.Listener) = Unit

        override fun removeListener(vararg queryKeys: String, listener: Query.Listener) = Unit

        override fun notifyListeners(vararg queryKeys: String) = Unit

        override fun close() = Unit
    }

    private companion object {
        const val BEGIN = "BEGIN IMMEDIATE"
        const val COMMIT = "COMMIT"
        const val ROLLBACK = "ROLLBACK"
    }
}
