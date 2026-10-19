package com.atharchive.ui.settings

import com.atharchive.ui.theme.AtharCorners
import com.atharchive.ui.theme.AtharTextSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import com.atharchive.feature.reader.SegmentRow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.feature.reader.ReaderPalette
import com.atharchive.feature.reader.ReaderSettings
import com.atharchive.feature.reader.ReaderSpacing
import com.atharchive.feature.reader.rememberReaderColors
import com.atharchive.ui.components.AtharSectionHeading
import com.atharchive.ui.components.AtharTopBar
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme
import com.atharchive.ui.theme.ThemeMode

/**
 * How the app looks. Font size, line spacing and the reading page live in the reader's «Aa»
 * panel only — one place per setting.
 */
@Composable
fun AppearanceScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    startPlace: com.atharchive.data.StartPlace,
    onStartPlaceChange: (com.atharchive.data.StartPlace) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("section_appearance")
            .background(AtharTheme.colors.canvas),
    ) {
        val pad = if (maxWidth >= 720.dp) 32.dp else 20.dp
        val segmentColors = rememberReaderColors(ReaderPalette.Follow)
        val segmentPad = Modifier.padding(horizontal = pad, vertical = 6.dp)

        Column(modifier = Modifier.fillMaxSize()) {
            AtharTopBar(
                title = "المظهر والقراءة",
                onSettings = {},
                showAppIcon = true,
                onBack = onBack,
                horizontalPadding = pad,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 28.dp),
            ) {
                AtharSectionHeading("سمة التطبيق", pad, top = 10.dp)
                Box(segmentPad) {
                    SegmentRow(ThemeMode.entries.map { it.label }, themeMode.label, segmentColors, "appearance_theme") { label ->
                        onThemeModeChange(ThemeMode.entries.first { it.label == label })
                    }
                }

                SectionRule(pad)
                AtharSectionHeading("يفتح التطبيق على", pad, top = 18.dp)
                val places = com.atharchive.data.StartPlace.entries
                places.chunked(3).forEachIndexed { row, group ->
                    Box(segmentPad) {
                        SegmentRow(group.map { it.label }, startPlace.label, segmentColors, "appearance_start_$row") { label ->
                            onStartPlaceChange(places.first { it.label == label })
                        }
                    }
                }

            }
        }
    }
}

@Composable
private fun SectionRule(horizontalPadding: Dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = horizontalPadding, end = horizontalPadding, top = 12.dp),
        thickness = 0.7.dp,
        color = AtharTheme.colors.divider.copy(alpha = 0.42f),
    )
}
