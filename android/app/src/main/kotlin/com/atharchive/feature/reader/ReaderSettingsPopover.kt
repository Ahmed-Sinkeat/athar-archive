package com.atharchive.feature.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import com.atharchive.ui.components.SoapLayer
import com.atharchive.ui.components.rememberSoapTargets
import com.atharchive.ui.components.soapTarget
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
import com.atharchive.ui.components.AtharMenu
import com.atharchive.ui.components.AtharMenuColors

/**
 * A small popover over the Aa button, never a settings page: changes land on the text visible
 * behind it, so the reader judges them in place instead of returning to check. It is the app's
 * one menu (AtharMenu) in the page's colours — put it in the same Box as the Aa button.
 */
@Composable
fun ReaderSettingsPopover(
    expanded: Boolean,
    settings: ReaderSettings,
    colors: ReaderColors,
    onChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
    forPoem: Boolean = false,
) {
    AtharMenu(
        expanded = expanded,
        onDismiss = onDismiss,
        width = 300.dp,
        colors = AtharMenuColors(card = colors.page, ink = colors.text, secondary = colors.secondary, accent = colors.accent),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag("reader_settings_popover")) {
            LabeledRow("حجم الخط", colors) {
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
            }

            LabeledRow("تباعد الأسطر", colors) { SegmentRow(
                options = ReaderSpacing.entries.map { it.label },
                selected = settings.spacing.label,
                colors = colors,
                tagPrefix = "reader_spacing",
            ) { label ->
                onChange(settings.copy(spacing = ReaderSpacing.entries.first { it.label == label }))
            } }

            LabeledRow("الهوامش", colors) { SegmentRow(listOf("ضيّقة", "عادية", "واسعة"), when (settings.margin) {
                12 -> "ضيّقة"; 40 -> "واسعة"; else -> "عادية"
            }, colors, "reader_margin") { label ->
                onChange(settings.copy(margin = when (label) { "ضيّقة" -> 12; "واسعة" -> 40; else -> 22 }))
            } }
            LabeledRow("التشكيل", colors) { SegmentRow(listOf("ظاهر", "مخفي"), if (settings.showTashkeel) "ظاهر" else "مخفي", colors, "reader_tashkeel") {
                onChange(settings.copy(showTashkeel = it == "ظاهر"))
            } }
            if (forPoem) {
                LabeledRow("أرقام الأبيات", colors) { SegmentRow(listOf("ظاهرة", "مخفية"), if (settings.verseNumbers) "ظاهرة" else "مخفية", colors, "poem_numbers") {
                    onChange(settings.copy(verseNumbers = it == "ظاهرة"))
                } }
                LabeledRow("العرض", colors) { SegmentRow(listOf("أبيات", "متصل"), if (settings.poemProse) "متصل" else "أبيات", colors, "poem_layout") {
                    onChange(settings.copy(poemProse = it == "متصل"))
                } }
            } else LabeledRow("العرض", colors) { SegmentRow(listOf("صفحات", "متصل"), if (settings.continuous) "متصل" else "صفحات", colors, "reader_layout") {
                onChange(settings.copy(continuous = it == "متصل"))
            } }
            // متصل has no page foot, so notes open from their number only.
            if (!forPoem && !settings.continuous) LabeledRow("الحواشي", colors) { SegmentRow(FootnoteMode.entries.map { it.label }, settings.footnoteMode.label, colors, "reader_footnotes") { label ->
                onChange(settings.copy(footnoteMode = FootnoteMode.entries.first { it.label == label }))
            } }

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

/** Label at the start (about 30%), the control filling the rest — one line per setting. */
@Composable
private fun LabeledRow(label: String, colors: ReaderColors, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.3f).padding(end = 6.dp),
            color = colors.secondary,
            style = MaterialTheme.typography.labelSmall,
        )
        Box(Modifier.weight(0.7f)) { content() }
    }
}

@Composable
private fun GroupLabel(text: String, colors: ReaderColors) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
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
            .size(36.dp)
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
    val soap = rememberSoapTargets()
    Box(Modifier.fillMaxWidth()) {
        // The selected fill is the soap pill; the others keep their light track tint.
        SoapLayer(soap, options.indexOf(selected), colors.accentFill, Modifier.matchParentSize())
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = option == selected
                val textColor by animateColorAsState(if (isSelected) colors.onAccent else colors.secondary, tween(160), label = "segment_text")
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .soapTarget(soap, index)
                        .clip(AtharCorners.Control)
                        .background(
                            if (isSelected) Color.Transparent else colors.divider.copy(alpha = 0.28f),
                        )
                        .heightIn(min = 34.dp)
                        .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(option) }
                        .padding(vertical = 6.dp)
                        .testTag("${tagPrefix}_$index"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option,
                        color = textColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
