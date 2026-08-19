package com.atharchive.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * A small anchored popover, never a settings page: changes land on the text visible
 * behind it, so the reader judges them in place instead of returning to check.
 */
@Composable
fun ReaderSettingsPopover(
    settings: ReaderSettings,
    colors: ReaderColors,
    onChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        alignment = Alignment.BottomCenter,
        offset = androidx.compose.ui.unit.IntOffset(0, -260),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .width(288.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.page)
                .border(1.dp, colors.divider, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("reader_settings_popover"),
        ) {
            GroupLabel("حجم الخط", colors)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StepButton("A−", colors, enabled = settings.fontSize > ReaderSettings.MIN_SIZE, tag = "reader_font_minus") {
                    onChange(settings.copy(fontSize = settings.fontSize - 1))
                }
                Text(
                    text = "${settings.fontSize}",
                    color = colors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("reader_font_size"),
                )
                StepButton("A+", colors, enabled = settings.fontSize < ReaderSettings.MAX_SIZE, tag = "reader_font_plus") {
                    onChange(settings.copy(fontSize = settings.fontSize + 1))
                }
            }

            GroupLabel("تباعد الأسطر", colors)
            SegmentRow(
                options = ReaderSpacing.entries.map { it.label },
                selected = settings.spacing.label,
                colors = colors,
                tagPrefix = "reader_spacing",
            ) { label ->
                onChange(settings.copy(spacing = ReaderSpacing.entries.first { it.label == label }))
            }

            GroupLabel("مظهر القراءة", colors)
            SegmentRow(
                options = ReaderPalette.entries.map { it.label },
                selected = settings.palette.label,
                colors = colors,
                tagPrefix = "reader_palette",
            ) { label ->
                onChange(settings.copy(palette = ReaderPalette.entries.first { it.label == label }))
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String, colors: ReaderColors) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
        color = colors.secondary,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
private fun StepButton(
    label: String,
    colors: ReaderColors,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(colors.divider.copy(alpha = 0.35f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) colors.text else colors.secondary.copy(alpha = 0.4f),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SegmentRow(
    options: List<String>,
    selected: String,
    colors: ReaderColors,
    tagPrefix: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) colors.accent.copy(alpha = 0.13f) else colors.divider.copy(alpha = 0.28f),
                    )
                    .clickable(role = Role.Button) { onSelect(option) }
                    .padding(vertical = 8.dp)
                    .testTag("${tagPrefix}_$index"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    color = if (isSelected) colors.accent else colors.secondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
