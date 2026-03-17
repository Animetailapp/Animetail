package tachiyomi.data.handlers.manga

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

/**
 * Returns the transaction dispatcher if we are on a transaction, or the database dispatchers.
 */
internal suspend fun AndroidMangaDatabaseHandler.getCurrentMangaDatabaseContext(): CoroutineContext {
    return coroutineContext[MangaTransactionElement]?.transactionDispatcher ?: queryDispatcher
}

/**
 * Calls the specified suspending [block] in a database transaction. The transaction will be
 * marked as successful unless an exception is thrown in the suspending [block] or the coroutine
 * is cancelled.
 *
 * SQLDelight will only perform at most one transaction at a time, additional transactions are queued
 * and executed on a first come, first serve order.
 *
 * Performing blocking database operations is not permitted in a coroutine scope other than the
 * one received by the suspending block. It is recommended that all [Dao] function invoked within
 * the [block] be suspending functions.
 *
 * The dispatcher used to execute the given [block] will utilize threads from SQLDelight's query executor.
 */
internal suspend fun <T> AndroidMangaDatabaseHandler.withMangaTransaction(block: suspend () -> T): T {
    // Use inherited transaction context if available, this allows nested suspending transactions.
    val transactionContext =
        coroutineContext[MangaTransactionElement]?.transactionDispatcher ?: createTransactionContext()
    return withContext(transactionContext) {
        val transactionElement = coroutineContext[MangaTransactionElement]!!
        transactionElement.acquire()
        try {
            db.transactionWithResult {
                runBlocking(transactionContext) {
                    block()
                }
            }
        } finally {
            transactionElement.release()
        }
    }
}

/**
 * Creates a [CoroutineContext] for performing database operations within a coroutine transaction.
 *
 * The context is a combination of a dispatcher, a [MangaTransactionElement] and a thread local element.
 */
private suspend fun AndroidMangaDatabaseHandler.createTransactionContext(): CoroutineContext {
    val controlJob = Job()
    // make sure to tie the control job to this context to avoid blocking the transaction if
    // context get cancelled before we can even start using this job. Otherwise, the acquired
    // transaction thread will forever wait for the controlJob to be cancelled.
    // see b/148181325
    coroutineContext[Job]?.invokeOnCompletion {
        controlJob.cancel()
    }

    val dispatcher = transactionDispatcher.acquireTransactionThread(controlJob)
    val transactionElement = MangaTransactionElement(controlJob, dispatcher)
    val threadLocalElement =
        suspendingTransactionId.asContextElement(System.identityHashCode(controlJob))
    return dispatcher + transactionElement + threadLocalElement
}

/**
 * Acquires a thread from the executor and returns a [ContinuationInterceptor] to dispatch
 * coroutines to the acquired thread. The [controlJob] is used to control the release of the
 * thread by cancelling the job.
 */
private suspend fun CoroutineDispatcher.acquireTransactionThread(
    controlJob: Job,
): ContinuationInterceptor {
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            // We got cancelled while waiting to acquire a thread, we can't stop our attempt to
            // acquire a thread, but we can cancel the controlling job so once it gets acquired it
            // is quickly released.
            controlJob.cancel()
        }
        try {
            dispatch(EmptyCoroutineContext) {
                runBlocking {
                    // Thread acquired, resume coroutine
                    continuation.resume(coroutineContext[ContinuationInterceptor]!!)
                    controlJob.join()
                }
            }
        } catch (ex: RejectedExecutionException) {
            // Couldn't acquire a thread, cancel coroutine
            continuation.cancel(
                IllegalStateException(
                    "Unable to acquire a thread to perform the database transaction",
                    ex,
                ),
            )
        }
    }
}

/**
 * A [CoroutineContext.Element] that indicates there is an on-going database transaction.
 */
private class MangaTransactionElement(
    private val transactionThreadControlJob: Job,
    val transactionDispatcher: ContinuationInterceptor,
) : CoroutineContext.Element {

    companion object Key : CoroutineContext.Key<MangaTransactionElement>

    override val key: CoroutineContext.Key<MangaTransactionElement>
        get() = MangaTransactionElement

    /**
     * Number of transactions (including nested ones) started with this element.
     * Call [acquire] to increase the count and [release] to decrease it. If the count reaches zero
     * when [release] is invoked then the transaction job is cancelled and the transaction thread
     * is released.
     */
    private val referenceCount = AtomicInteger(0)

    fun acquire() {
        referenceCount.incrementAndGet()
    }

    fun release() {
        val count = referenceCount.decrementAndGet()
        if (count < 0) {
            throw IllegalStateException("Transaction was never started or was already released")
        } else if (count == 0) {
            // Cancel the job that controls the transaction thread, causing it to be released.
            transactionThreadControlJob.cancel()
        }
    }
}
