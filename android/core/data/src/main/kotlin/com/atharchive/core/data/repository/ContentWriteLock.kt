package com.atharchive.core.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one lock over content writes (catalog apply, pins, imports, eviction). A page the reader is
 * waiting for goes ahead of download batches: with downloads running, opening a book queued behind
 * every batch already waiting (each up to eight frames) and took seconds.
 */
class ContentWriteLock {
    @PublishedApi internal val mutex = Mutex()
    @PublishedApi internal val readersWaiting = MutableStateFlow(0)

    suspend inline fun <T> withLock(action: () -> T): T = mutex.withLock { action() }

    /** For the page on screen: download batches that have not taken the lock yet wait for it. */
    suspend inline fun <T> forReader(action: () -> T): T {
        readersWaiting.update { it + 1 }
        try {
            return mutex.withLock { action() }
        } finally {
            readersWaiting.update { it - 1 }
        }
    }

    /**
     * For one download batch: starts only once no reader is waiting. Checked again after taking
     * the lock: batches already queued on the mutex (it is first come, first served) went ahead
     * of a reader that arrived after them, and a page waited for three batches (~430 ms).
     */
    suspend inline fun <T> forBatch(action: () -> T): T {
        while (true) {
            readersWaiting.first { it == 0 }
            mutex.lock()
            if (readersWaiting.value == 0) break
            mutex.unlock()
        }
        try {
            return action()
        } finally {
            mutex.unlock()
        }
    }
}
