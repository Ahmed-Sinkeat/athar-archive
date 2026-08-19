package com.atharchive.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atharchive.ui.theme.AtharTheme

/**
 * Athar's restrained glass treatment.
 *
 * It is deliberately near-opaque: the effect comes from tonal separation, a
 * warm hairline and shallow elevation. This keeps Arabic text crisp and gives
 * API 26 devices the same hierarchy without requiring a costly backdrop blur.
 */
@Composable
fun AtharGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    shadowElevation: Dp = 3.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = AtharTheme.colors.glassSurface,
        border = BorderStroke(1.dp, AtharTheme.colors.glassBorder),
        shadowElevation = shadowElevation,
    ) {
        Box(content = content)
    }
}
