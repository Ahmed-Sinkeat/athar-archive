package com.atharchive.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.atharchive.ui.theme.AtharTheme
import kotlinx.coroutines.delay

/**
 * The app's one "something is loading" mark: a ring and a line of words — or, when the work
 * has a known end ([progress] 0..1), a filling bar with its percent. After a few seconds it
 * says so out loud, so a slow first open never looks like a frozen page.
 */
@Composable
fun AtharLoading(message: String, modifier: Modifier = Modifier, tag: String = "athar_loading", progress: Float? = null) {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(6_000); slow = true }
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (progress == null) CircularProgressIndicator(
            modifier = Modifier.size(34.dp),
            color = AtharTheme.colors.accent,
            strokeWidth = 3.dp,
        ) else {
            val shown by animateFloatAsState(progress.coerceIn(0f, 1f), label = "loading")
            LinearProgressIndicator(
                progress = { shown },
                modifier = Modifier.width(180.dp).testTag("${tag}_progress"),
                color = AtharTheme.colors.accent,
                trackColor = AtharTheme.colors.accent.copy(alpha = 0.18f),
            )
            Text(
                text = "${arabicDigits((shown * 100).toInt())}٪",
                color = AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Text(
            text = message,
            color = AtharTheme.colors.secondaryText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (slow) Text(
            text = "يستغرق هذا وقتًا أطول من المعتاد — تأكّد من الاتصال",
            color = AtharTheme.colors.secondaryText,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}
