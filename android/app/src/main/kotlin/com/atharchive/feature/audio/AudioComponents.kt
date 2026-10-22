package com.atharchive.feature.audio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharCorners
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.atharchive.ui.theme.AtharTheme

/** Muted olive on a whisper of beige — a state, not an award. */
@Composable
internal fun DownloadedPill(modifier: Modifier = Modifier) {
    Text(
        text = "محمّل",
        modifier = modifier
            .clip(AtharCorners.Tag)
            .background(AtharTheme.colors.success.copy(alpha = 0.11f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        color = AtharTheme.colors.success,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
internal fun AudioPlayButton(playing: Boolean, size: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            // primaryAction, not accent: a big filled disc in the bright accent
            // dominates the row in dark mode.
            .background(AtharTheme.colors.primaryAction)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (playing) AtharIcons.Pause else AtharIcons.Play,
            contentDescription = if (playing) "إيقاف مؤقت" else "تشغيل",
            tint = AtharTheme.colors.onPrimaryAction,
            modifier = Modifier.size(size * 0.42f),
        )
    }
}

/**
 * Fills from the start edge, which under RTL is the right — playback begins on the
 * right and travels left. Nothing here mirrors manually; the layout direction does it.
 */
@Composable
fun AudioProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape)
            .background(AtharTheme.colors.divider.copy(alpha = 0.75f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(3.dp)
                .clip(CircleShape)
                .background(AtharTheme.colors.accent),
        )
    }
}
