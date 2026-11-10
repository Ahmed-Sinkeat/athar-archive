package com.atharchive.core.data.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyedLockTest {
    @Test
    fun oneHolderPerKeyAndNothingKeptAfter() = runBlocking {
        val lock = KeyedLock<String>()
        var inside = 0
        var most = 0
        (1..50).map { i ->
            async {
                lock.withLock(if (i % 2 == 0) "a" else "b") {
                    inside++; most = maxOf(most, inside); delay(1); inside--
                }
            }
        }.awaitAll()
        assertEquals(2, most) // one per key, two keys
        assertEquals(0, lock.size)
        // an early return out of the block releases too
        fun early(): Int = 0
        lock.withLock("c") { return@withLock early() }
        assertEquals(0, lock.size)
    }
}
