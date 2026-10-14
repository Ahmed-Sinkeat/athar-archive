package com.atharchive.feature.reader

import com.atharchive.ui.theme.AtharCorners
import com.atharchive.ui.theme.AtharTextSize
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
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
                .heightIn(max = 420.dp)
                .clip(AtharCorners.Surface)
                .background(colors.page)
                .border(1.dp, colors.divider, AtharCorners.Surface)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("reader_settings_popover"),
        ) {
            GroupLabel("حجم الخط", colors)
            // A− on the left, A+ on the right, as in every reader: the order is spatial
            // (small → large), not reading direction.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StepButton("A−", colors, enabled = settings.fontSize > ReaderSettings.MIN_SIZE, tag = "reader_font_minus") {
                    onChange(settings.copy(fontSize = settings.fontSize - 1))
                }
                Text(
                    text = settings.fontSize.toString().map { '\u0660' + (it - '0') }.joinToString(""),
                    color = colors.text,
                    fontSize = AtharTextSize.Ui,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("reader_font_size"),
                )
                StepButton("A+", colors, enabled = settings.fontSize < ReaderSettings.MAX_SIZE, tag = "reader_font_plus") {
                    onChange(settings.copy(fontSize = settings.fontSize + 1))
                }
            } }

            GroupLabel("تباعد الأسطر", colors)
            SegmentRow(
                options = ReaderSpacing.entries.map { it.label },
                selected = settings.spacing.label,
                colors = colors,
                tagPrefix = "reader_spacing",
            ) { label ->
                onChange(settings.copy(spacing = ReaderSpacing.entries.first { it.label == label }))
            }

            GroupLabel("الهوامش", colors)
            SegmentRow(listOf("ضيّقة", "عادية", "واسعة"), when (settings.margin) {
                12 -> "ضيّقة"; 40 -> "واسعة"; else -> "عادية"
            }, colors, "reader_margin") { label ->
                onChange(settings.copy(margin = when (label) { "ضيّقة" -> 12; "واسعة" -> 40; else -> 22 }))
            }
            GroupLabel("التشكيل", colors)
            SegmentRow(listOf("ظاهر", "مخفي"), if (settings.showTashkeel) "ظاهر" else "مخفي", colors, "reader_tashkeel") {
                onChange(settings.copy(showTashkeel = it == "ظاهر"))
            }
            GroupLabel("الحواشي", colors)
            SegmentRow(listOf("ظاهرة", "مخفية"), if (settings.showFootnotes) "ظاهرة" else "مخفية", colors, "reader_footnotes") {
                onChange(settings.copy(showFootnotes = it == "ظاهرة"))
            }
            GroupLabel("أرقام الصفحات", colors)
            SegmentRow(listOf("ظاهرة", "مخفية"), if (settings.showPageNumbers) "ظاهرة" else "مخفية", colors, "reader_page_numbers") {
                onChange(settings.copy(showPageNumbers = it == "ظاهرة"))
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
            .size(48.dp)
            .clip(CircleShape)
            .background(colors.divider.copy(alpha = 0.35f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (label == "A−") "تصغير الخط" else "تكبير الخط" }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) colors.text else colors.secondary.copy(alpha = 0.4f),
            fontSize = AtharTextSize.Ui,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Shared with the Appearance screen, so both set these settings with the same control. */
@Composable
internal fun SegmentRow(
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
                    .clip(AtharCorners.Control)
                    .background(
                        if (isSelected) colors.accent.copy(alpha = 0.13f) else colors.divider.copy(alpha = 0.28f),
                    )
                    .heightIn(min = 48.dp)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(option) }
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
