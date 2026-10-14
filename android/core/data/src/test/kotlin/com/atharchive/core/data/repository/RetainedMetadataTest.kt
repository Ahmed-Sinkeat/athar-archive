package com.atharchive.core.data.repository

import com.atharchive.core.data.content.AudioReference
import com.atharchive.core.data.content.CatalogEntry
import com.atharchive.core.data.content.PackageReference
import org.junit.Assert.assertEquals
import org.junit.Test

class RetainedMetadataTest {
    @Test fun downloadsAndPlaylistMembershipKeepTheirSourceMetadata() {
        fun source(id: String, audioId: String) = CatalogEntry(id, "book", 1, "", id,
            pkg = PackageReference("", "", 0, "", "", 0, 0, 0, 0),
            audio = listOf(AudioReference(audioId, path = "", hash = "", format = "opus", seconds = 1, size = 1)))
        val catalog = listOf(source("saved-source", "saved"), source("download-source", "download"), source("disposable", "other"))
        assertEquals(setOf("audio:saved", "text-book", "saved-source", "download-source"),
            retainedMetadataIds(setOf("audio:saved", "text-book"), setOf("download"), catalog))
        assertEquals(emptySet<String>(), retainedMetadataIds(emptySet(), emptySet(), catalog))
    }
}
