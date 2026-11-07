package com.atharchive.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.atharchive.ui.icons.AtharIcons

enum class AtharDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    /** Shown while the tab is selected. */
    val selectedIcon: ImageVector = icon,
) {
    Books("books", "الكتب", AtharIcons.Books, AtharIcons.BooksFilled),
    Poetry("poetry", "الشعر", AtharIcons.Poetry, AtharIcons.PoetryFilled),
    Search("search", "البحث", AtharIcons.Search, AtharIcons.SearchFilled),
    Articles("articles", "المقالات", AtharIcons.Articles, AtharIcons.ArticlesFilled),
    Kannashah("kannashah", "الكناشة", AtharIcons.Kannashah, AtharIcons.KannashahFilled),
    // The rest have their own screens; any of them can take a tab instead of living in ⋮.
    Mutoon("mutoon", "المتون", AtharIcons.Mutoon),
    Adhkar("adhkar", "الأذكار", AtharIcons.Adhkar),
    Persons("persons", "الأعلام", AtharIcons.People),
    Issues("issues", "المسائل", AtharIcons.Issues),
    Settings("settings", "الإعدادات", AtharIcons.Settings),
    ;

    companion object {
        fun fromRoute(route: String): AtharDestination = entries.firstOrNull { it.route == if (route == "audio") "articles" else route } ?: Books

        const val BarSize = 5
        val DefaultBar = listOf(Books, Poetry, Search, Articles, Kannashah)

        /** The stored bar, or the default when it is not five distinct tabs starting with الكتب. */
        fun barOf(stored: String?): List<AtharDestination> {
            val bar = stored.orEmpty().split(',').mapNotNull { r -> entries.firstOrNull { it.route == r } }
            return bar.takeIf { it.size == BarSize && it.distinct().size == BarSize && it.first() == Books } ?: DefaultBar
        }

        fun List<AtharDestination>.stored(): String = joinToString(",") { it.route }

        /** Puts [choice] in [slot]; if it already has a tab, the two swap. الكتب stays first: back leads to it. */
        fun List<AtharDestination>.withSlot(slot: Int, choice: AtharDestination): List<AtharDestination> {
            if (slot !in 1 until size || choice == Books) return this
            val bar = toMutableList()
            val from = bar.indexOf(choice)
            if (from >= 0) bar[from] = bar[slot]
            bar[slot] = choice
            return bar
        }
    }
}
