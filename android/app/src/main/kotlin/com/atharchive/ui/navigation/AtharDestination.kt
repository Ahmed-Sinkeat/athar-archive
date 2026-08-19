package com.atharchive.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.atharchive.ui.icons.AtharIcons

enum class AtharDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Books("books", "الكتب", AtharIcons.Books),
    Poetry("poetry", "الشعر", AtharIcons.Poetry),
    Search("search", "البحث", AtharIcons.Search),
    Audio("audio", "الصوتيات", AtharIcons.Audio),
    Kannashah("kannashah", "الكناشة", AtharIcons.Kannashah),
    ;

    companion object {
        fun fromRoute(route: String): AtharDestination = entries.first { it.route == route }
    }
}
