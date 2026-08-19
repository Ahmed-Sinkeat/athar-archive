package com.atharchive.ui.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.atharchive.R
import com.atharchive.ui.theme.AtharTheme

/**
 * The Athar mark, drawn as two tinted layers.
 *
 * The artwork is a flat two-colour PNG, so it was split into an ink layer and an accent
 * layer that each carry only alpha; the colour comes from the tint at draw time. That is
 * what makes it theme-aware — a single-image mark cannot recolour, and a charcoal mark on
 * the `#151311` dark canvas is a contrast ratio of about 1.05:1, i.e. invisible.
 *
 * Replace with a real [androidx.compose.ui.graphics.vector.ImageVector] if a vector of this
 * mark ever exists: same call site, sharper at large sizes, one file instead of two.
 */
@Composable
fun AtharMark(
    modifier: Modifier = Modifier,
    ink: Color = MaterialTheme.colorScheme.onBackground,
    accent: Color = AtharTheme.colors.accent,
    contentDescription: String? = null,
) {
    Box(modifier = modifier) {
        Image(
            painter = painterResource(R.drawable.ic_athar_mark_ink),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            colorFilter = ColorFilter.tint(ink),
        )
        Image(
            painter = painterResource(R.drawable.ic_athar_mark_accent),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            colorFilter = ColorFilter.tint(accent),
        )
    }
}
