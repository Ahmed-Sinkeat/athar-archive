package com.atharchive.core.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One lock per key (a book), dropped once nobody holds or waits for it. A map that kept a Mutex
 * for every book ever opened or downloaded only grew (#19).
 */
class KeyedLock<K : Any> {
    @PublishedApi internal class Entry { val mutex = Mutex(); var users = 0 }
    @PublishedApi internal val entries = HashMap<K, Entry>()

    suspend inline fun <T> withLock(key: K, action: () -> T): T {
        val entry = synchronized(entries) { entries.getOrPut(key) { Entry() }.also { it.users++ } }
        try {
            return entry.mutex.withLock { action() }
        } finally {
            synchronized(entries) { if (--entry.users == 0) entries.remove(key) }
        }
    }

    /** Keys with a holder or a waiter; for the test. */
    val size: Int get() = synchronized(entries) { entries.size }
}
