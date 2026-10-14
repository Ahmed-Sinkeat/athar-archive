package com.atharchive.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.MaterialTheme
import com.atharchive.ui.theme.AtharTheme

/**
 * Replaces Android's floating «Copy | Paste» bar everywhere a Compose text is selected
 * (poem reader, search fields, notes) with the app's own card, in the app's own words.
 */
private class AtharTextToolbarState : TextToolbar {
    var rect by mutableStateOf<Rect?>(null)
    var onCopy: (() -> Unit)? = null
    var onPaste: (() -> Unit)? = null
    var onCut: (() -> Unit)? = null
    var onSelectAll: (() -> Unit)? = null

    override val status: TextToolbarStatus
        get() = if (rect != null) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        onCopy = onCopyRequested; onPaste = onPasteRequested; onCut = onCutRequested; onSelectAll = onSelectAllRequested
        this.rect = rect
    }

    override fun hide() { rect = null }
}

@Composable
fun ProvideAtharTextToolbar(content: @Composable () -> Unit) {
    val toolbar = remember { AtharTextToolbarState() }
    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        content()
        val anchor = toolbar.rect
        if (anchor != null) {
            val density = LocalDensity.current
            val gap = with(density) { 10.dp.roundToPx() }
            val colors = atharMenuColors()
            Popup(
                popupPositionProvider = object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize,
                    ): IntOffset {
                        // Centred above the selection; below it when there is no room above.
                        val x = (anchor.center.x.toInt() - popupContentSize.width / 2)
                            .coerceIn(gap, (windowSize.width - popupContentSize.width - gap).coerceAtLeast(gap))
                        val above = anchor.top.toInt() - popupContentSize.height - gap
                        val y = if (above >= gap) above else anchor.bottom.toInt() + gap * 3
                        return IntOffset(x, y)
                    }
                },
                onDismissRequest = { toolbar.hide() },
                properties = PopupProperties(focusable = false, dismissOnClickOutside = true),
            ) {
                val shape = RoundedCornerShape(12.dp)
                Row(
                    modifier = Modifier
                        .clip(shape)
                        .background(colors.card)
                        .border(0.7.dp, AtharTheme.colors.divider, shape)
                        .padding(horizontal = 4.dp)
                        .testTag("athar_text_toolbar"),
                ) {
                    @Composable
                    fun action(label: String, tag: String, run: (() -> Unit)?) {
                        if (run == null) return
                        Text(
                            text = label,
                            modifier = Modifier
                                .clickable(role = Role.Button) { run(); toolbar.hide() }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag(tag),
                            color = colors.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    action("قص", "text_cut", toolbar.onCut)
                    action("نسخ", "text_copy", toolbar.onCopy)
                    action("لصق", "text_paste", toolbar.onPaste)
                    action("تحديد الكل", "text_select_all", toolbar.onSelectAll)
                }
            }
        }
    }
}
