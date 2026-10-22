package com.atharchive.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AtharDestinationTest {
    @Test fun restoredAudioRoutesOpenArticlesAndUnknownRoutesFallBackSafely() {
        assertEquals(AtharDestination.Articles, AtharDestination.fromRoute("audio"))
        assertEquals(AtharDestination.Books, AtharDestination.fromRoute("missing"))
        AtharDestination.entries.forEach { assertEquals(it, AtharDestination.fromRoute(it.route)) }
    }
}
