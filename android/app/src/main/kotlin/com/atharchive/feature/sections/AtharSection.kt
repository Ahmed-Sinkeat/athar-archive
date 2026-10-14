package com.atharchive.feature.sections

import androidx.compose.ui.graphics.vector.ImageVector
import com.atharchive.ui.icons.AtharIcons

/** المقالات and المسائل — opened from the app's ⋮ menu. */
enum class AtharSection(
    val route: String,
    val label: String,
    val description: String,
    val icon: ImageVector,
) {
    Articles("articles", "المقالات", "مقالات وقراءات", AtharIcons.Articles),
    Issues("issues", "المسائل", "مسائل علمية مرتبة حسب الموضوع", AtharIcons.Issues),
}
