package com.atharchive.ui.navigation

import com.atharchive.ui.navigation.AtharDestination.Companion.stored
import com.atharchive.ui.navigation.AtharDestination.Companion.withSlot
import org.junit.Assert.assertEquals
import org.junit.Test

class AtharDestinationTest {
    @Test fun restoredAudioRoutesOpenArticlesAndUnknownRoutesFallBackSafely() {
        assertEquals(AtharDestination.Articles, AtharDestination.fromRoute("audio"))
        assertEquals(AtharDestination.Books, AtharDestination.fromRoute("missing"))
        AtharDestination.entries.forEach { assertEquals(it, AtharDestination.fromRoute(it.route)) }
    }

    @Test fun barKeepsFiveDistinctTabsWithBooksFirst() {
        val default = AtharDestination.DefaultBar
        assertEquals(default, AtharDestination.barOf(null))
        assertEquals(default, AtharDestination.barOf("books,poetry,search"))
        assertEquals(default, AtharDestination.barOf("poetry,books,search,articles,kannashah"))
        assertEquals(default, AtharDestination.barOf("books,poetry,poetry,articles,kannashah"))
        assertEquals(default, AtharDestination.barOf("books,poetry,gone,articles,kannashah"))

        val adhkar = default.withSlot(4, AtharDestination.Adhkar)
        assertEquals(listOf("books", "poetry", "search", "articles", "adhkar"), adhkar.map { it.route })
        assertEquals(adhkar, AtharDestination.barOf(adhkar.stored()))
        // A tab already in the bar swaps places instead of appearing twice.
        assertEquals(listOf("books", "search", "poetry", "articles", "adhkar"), adhkar.withSlot(1, AtharDestination.Search).map { it.route })
        // الكتب never moves, and nothing replaces it.
        assertEquals(adhkar, adhkar.withSlot(0, AtharDestination.Settings))
        assertEquals(adhkar, adhkar.withSlot(2, AtharDestination.Books))
    }
}
