package com.atharchive.core.data.repository

import com.atharchive.core.data.db.content.BundleRow
import org.junit.Assert.assertEquals
import org.junit.Test

/** Same input and expected names as the site's `bundleParts` (src/lib/app-v2/bundles.ts) gives. */
class BundleNameTest {
    @Test
    fun partsAndNamesMatchTheSite() {
        // bundleRows returns the kind's small packages sorted by id; the site filters and sorts the same way
        val rows = (0 until 40).map { BundleRow("i%02d".format(it), "h$it", 250_000, 10) }
        val parts = bundleParts(rows)
        assertEquals(listOf(33, 7), parts.map { it.size })
        assertEquals("ca56e92302946cdf441fbb65c799868021e9726b04aceb7a0e6c1529776de88d", bundleName(parts[0]))
        assertEquals("44ab5a5095fd6ef892fe5d97830782a40acc6ddb995ce25f685359be0d2f1178", bundleName(parts[1]))
    }
}
