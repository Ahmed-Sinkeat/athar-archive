package com.atharchive.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.atharchive.ui.icons.AtharIcons

enum class AtharDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    /** Shown while the tab is selected. */
    val selectedIcon: ImageVector,
) {
    Books("books", "الكتب", AtharIcons.Books, AtharIcons.BooksFilled),
    Poetry("poetry", "الشعر", AtharIcons.Poetry, AtharIcons.PoetryFilled),
    Search("search", "البحث", AtharIcons.Search, AtharIcons.SearchFilled),
    Articles("articles", "المقالات", AtharIcons.Articles, AtharIcons.ArticlesFilled),
    Kannashah("kannashah", "الكناشة", AtharIcons.Kannashah, AtharIcons.KannashahFilled),
    ;

    companion object {
        fun fromRoute(route: String): AtharDestination = entries.firstOrNull { it.route == if (route == "audio") "articles" else route } ?: Books
    }
}
