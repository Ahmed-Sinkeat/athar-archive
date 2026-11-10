package com.atharchive.core.data.repository

import com.atharchive.core.data.content.AudioReference
import org.junit.Assert.assertEquals
import org.junit.Test

class RetainedMetadataTest {
    @Test fun downloadsAndPlaylistMembershipKeepTheirSourceMetadata() {
        fun source(id: String, audioId: String) =
            id to listOf(AudioReference(audioId, path = "", hash = "", format = "opus", seconds = 1, size = 1))
        val catalog = listOf(source("saved-source", "saved"), source("download-source", "download"), source("disposable", "other"))
        assertEquals(setOf("audio:saved", "text-book", "saved-source", "download-source"),
            retainedMetadataIds(setOf("audio:saved", "text-book"), setOf("download"), catalog))
        assertEquals(emptySet<String>(), retainedMetadataIds(emptySet(), emptySet(), catalog))
    }
}
