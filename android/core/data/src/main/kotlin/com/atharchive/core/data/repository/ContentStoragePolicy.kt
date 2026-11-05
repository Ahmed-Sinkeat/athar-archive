package com.atharchive.core.data.repository

import android.os.StatFs
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class InsufficientContentStorageException(
    val availableBytes: Long,
    val requiredBytes: Long,
    val cacheBytesRemoved: Long = 0,
) : Exception("content storage has $availableBytes bytes available; $requiredBytes are required")

data class ContentStorageStatus(
    val lowStorage: Boolean,
    val availableBytes: Long,
    val cacheBytesRemoved: Long,
)

class ContentStoragePolicy(
    private val storageRoot: File,
    private val cacheManager: ContentCacheManager,
    private val availableBytes: () -> Long = { StatFs(storageRoot.absolutePath).availableBytes },
    private val configuredBudgetBytes: () -> Long = { ContentCacheManager.DEFAULT_BUDGET_BYTES },
) {
    companion object {
        const val LOW_STORAGE_BYTES = 500L * 1024 * 1024
    }

    suspend fun prepareForReadThrough(
        activeEntityId: String? = null,
        activeFrameOrdinal: Int? = null,
        activeFrameOrdinals: Set<Int> = activeFrameOrdinal?.let { setOf(it) }.orEmpty(),
    ): ContentStorageStatus {
        val before = availableBytes()
        val low = before < LOW_STORAGE_BYTES
        val removed = cacheManager.evictToBudget(
            budgetBytes = if (low) emergencyBudget() else configuredBudgetBytes(),
            activeEntityId = activeEntityId,
            activeFrameOrdinal = activeFrameOrdinal,
            activeFrameOrdinals = activeFrameOrdinals,
        )
        val after = availableBytes()
        return ContentStorageStatus(lowStorage = low, availableBytes = after, cacheBytesRemoved = removed)
    }

    /**
     * Disk promised to downloads in flight. Up to three run at once and each used to check the
     * free space alone, so all of them passed on the same free bytes and filled the phone.
     * Counted until a download ends, though it fills the disk as it goes: errs on refusing.
     */
    private val reservedBytes = AtomicLong()

    /** [reserve]: hold [diskBytes] against other downloads until [release]. */
    suspend fun prepareForDownload(diskBytes: Long, reserve: Boolean = false): ContentStorageStatus {
        require(diskBytes >= 0)
        val required = LOW_STORAGE_BYTES + diskBytes + reservedBytes.get()
        val before = availableBytes()
        val removed = cacheManager.evictToBudget(
            budgetBytes = if (before < required) emergencyBudget() else configuredBudgetBytes(),
        )
        val after = availableBytes()
        if (after < required) {
            throw InsufficientContentStorageException(after, required, removed)
        }
        if (reserve) reservedBytes.addAndGet(diskBytes)
        return ContentStorageStatus(
            lowStorage = before < LOW_STORAGE_BYTES,
            availableBytes = after,
            cacheBytesRemoved = removed,
        )
    }

    fun release(diskBytes: Long) {
        reservedBytes.addAndGet(-diskBytes)
    }

    private suspend fun emergencyBudget(): Long {
        val configured = configuredBudgetBytes()
        val basis = if (configured == ContentCacheManager.NO_LIMIT_BYTES) {
            cacheManager.usedBytes()
        } else {
            configured
        }
        return basis / 2
    }
}
