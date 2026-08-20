package com.atharchive.core.data.repository

import android.os.StatFs
import java.io.File

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
    ): ContentStorageStatus {
        val before = availableBytes()
        val low = before < LOW_STORAGE_BYTES
        val removed = cacheManager.evictToBudget(
            budgetBytes = if (low) emergencyBudget() else configuredBudgetBytes(),
            activeEntityId = activeEntityId,
            activeFrameOrdinal = activeFrameOrdinal,
        )
        val after = availableBytes()
        return ContentStorageStatus(lowStorage = low, availableBytes = after, cacheBytesRemoved = removed)
    }

    suspend fun prepareForDownload(uncompressedBytes: Long): ContentStorageStatus {
        require(uncompressedBytes >= 0)
        val required = LOW_STORAGE_BYTES + uncompressedBytes
        val before = availableBytes()
        val removed = cacheManager.evictToBudget(
            budgetBytes = if (before < required) emergencyBudget() else configuredBudgetBytes(),
        )
        val after = availableBytes()
        if (after < required) {
            throw InsufficientContentStorageException(after, required, removed)
        }
        return ContentStorageStatus(
            lowStorage = before < LOW_STORAGE_BYTES,
            availableBytes = after,
            cacheBytesRemoved = removed,
        )
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
