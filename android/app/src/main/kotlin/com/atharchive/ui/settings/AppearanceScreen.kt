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
 * One screen for how the app looks and how it reads.
 *
 * These were two dead rows in الإعدادات, and they were two rows because the reader kept a
 * palette the app knew nothing about. Now the page follows the app by default, so the two
 * settings are one subject and belong on one screen.
 */
@Composable
fun AppearanceScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    startPlace: com.atharchive.data.StartPlace,
    onStartPlaceChange: (com.atharchive.data.StartPlace) -> Unit,
    reader: ReaderSettings,
    onReaderChange: (ReaderSettings) -> Unit,
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

                SectionRule(pad)
                AtharSectionHeading("صفحة القراءة", pad, top = 18.dp)
                PagePreview(reader, pad)
                Box(segmentPad) {
                    SegmentRow(ReaderPalette.entries.map { it.label }, reader.palette.label, segmentColors, "appearance_palette") { label ->
                        onReaderChange(reader.copy(palette = ReaderPalette.entries.first { it.label == label }))
                    }
                }

                SectionRule(pad)
                AtharSectionHeading("حجم الخط", pad, top = 18.dp)
                FontSizeRow(
                    size = reader.fontSize,
                    horizontalPadding = pad,
                    onChange = { onReaderChange(reader.copy(fontSize = it)) },
                )

                SectionRule(pad)
                AtharSectionHeading("تباعد الأسطر", pad, top = 18.dp)
                Box(segmentPad) {
                    SegmentRow(ReaderSpacing.entries.map { it.label }, reader.spacing.label, segmentColors, "appearance_spacing") { label ->
                        onReaderChange(reader.copy(spacing = ReaderSpacing.entries.first { it.label == label }))
                    }
                }
            }
        }
    }
}

/** A line of the real thing, in the real palette, at the real size. */
@Composable
private fun PagePreview(reader: ReaderSettings, horizontalPadding: Dp) {
    val colors = rememberReaderColors(reader.palette)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 6.dp)
            .clip(AtharCorners.Card)
            .background(colors.page)
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .testTag("appearance_preview"),
    ) {
        Text(
            text = "وَالْإِخْلَاصُ أَصْلُ الْعَمَلِ كُلِّهِ",
            color = colors.text,
            fontFamily = AtharEditorialFontFamily,
            fontSize = reader.fontSize.sp,
            lineHeight = (reader.fontSize * reader.spacing.multiplier).sp,
        )
        Text(
            text = "باب الإخلاص",
            modifier = Modifier.padding(top = 4.dp),
            color = colors.accent,
            fontFamily = AtharEditorialFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (reader.fontSize - 4).sp,
        )
    }
}

@Composable
private fun FontSizeRow(size: Int, horizontalPadding: Dp, onChange: (Int) -> Unit) {
    // Spatial order (small → large), as in the reader's Aa.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StepButton(
            label = "A−",
            enabled = size > ReaderSettings.MIN_SIZE,
            tag = "appearance_font_minus",
            onClick = { onChange(size - 1) },
        )
        Text(
            text = size.toString().map { '\u0660' + (it - '0') }.joinToString(""),
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        StepButton(
            label = "A+",
            enabled = size < ReaderSettings.MAX_SIZE,
            tag = "appearance_font_plus",
            onClick = { onChange(size + 1) },
        )
    } }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(AtharCorners.Card)
            .background(AtharTheme.colors.raisedSurface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                AtharTheme.colors.secondaryText.copy(alpha = 0.4f)
            },
            fontSize = AtharTextSize.Ui,
            fontWeight = FontWeight.SemiBold,
        )
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
