package com.atharchive.ui.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                ThemeMode.entries.forEach { mode ->
                    ChoiceRow(
                        label = mode.label,
                        selected = mode == themeMode,
                        horizontalPadding = pad,
                        tag = "appearance_theme_${mode.name}",
                        onClick = { onThemeModeChange(mode) },
                    )
                }

                SectionRule(pad)
                AtharSectionHeading("صفحة القراءة", pad, top = 18.dp)
                PagePreview(reader, pad)
                ReaderPalette.entries.forEach { palette ->
                    ChoiceRow(
                        label = palette.label,
                        // The one option that needs saying out loud: it is why the default
                        // no longer drops a cream page on a reader at night.
                        note = if (palette == ReaderPalette.Follow) "يتبع سمة التطبيق" else null,
                        selected = palette == reader.palette,
                        horizontalPadding = pad,
                        tag = "appearance_palette_${palette.name}",
                        onClick = { onReaderChange(reader.copy(palette = palette)) },
                    )
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
                ReaderSpacing.entries.forEach { spacing ->
                    ChoiceRow(
                        label = spacing.label,
                        selected = spacing == reader.spacing,
                        horizontalPadding = pad,
                        tag = "appearance_spacing_${spacing.name}",
                        onClick = { onReaderChange(reader.copy(spacing = spacing)) },
                    )
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
            .clip(RoundedCornerShape(12.dp))
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
    Row(
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
            text = "$size",
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        StepButton(
            label = "A+",
            enabled = size < ReaderSettings.MAX_SIZE,
            tag = "appearance_font_plus",
            onClick = { onChange(size + 1) },
        )
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
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
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    horizontalPadding: Dp,
    tag: String,
    onClick: () -> Unit,
    note: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = horizontalPadding, vertical = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (selected) {
                    AtharTheme.colors.accent
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (note != null) {
                Text(
                    text = note,
                    color = AtharTheme.colors.secondaryText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        // A check, not a radio ring: the app marks state with a check everywhere else.
        if (selected) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(AtharTheme.colors.accentSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AtharIcons.Check,
                    contentDescription = null,
                    tint = AtharTheme.colors.accent,
                    modifier = Modifier.size(14.dp),
                )
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
