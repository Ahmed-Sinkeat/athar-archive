package com.atharchive.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme

/**
 * Section chrome shared by الكتب / الشعر / المقالات.
 *
 * `tagPrefix` keeps each section's test tags stable and distinct
 * (`books_search`, `poetry_tab_all`, …) without a component per section.
 */

data class AtharTab(val key: String, val label: String)

@Composable
fun AtharSearchField(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp),
        shape = RoundedCornerShape(12.dp),
        // Filled rather than outlined: one soft shape reads quieter than a hard border.
        color = AtharTheme.colors.raisedSurface,
        border = BorderStroke(1.dp, AtharTheme.colors.divider.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = AtharIcons.Search,
                    contentDescription = null,
                    tint = AtharTheme.colors.secondaryText,
                    modifier = Modifier.size(21.dp),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 11.dp)
                    .testTag("${tagPrefix}_search"),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(AtharTheme.colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isBlank()) {
                            Text(
                                text = placeholder,
                                color = AtharTheme.colors.secondaryText,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (query.isNotBlank()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("${tagPrefix}_search_clear"),
                ) {
                    Icon(
                        imageVector = AtharIcons.Close,
                        contentDescription = "مسح البحث",
                        tint = AtharTheme.colors.secondaryText,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun AtharTabRow(
    tabs: List<AtharTab>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier,
) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
    ) {
        tabs.forEach { tab ->
            SectionTab(
                tab = tab,
                selected = tab.key == selectedKey,
                largeText = largeText,
                onClick = { onSelect(tab.key) },
                tagPrefix = tagPrefix,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SectionTab(
    tab: AtharTab,
    selected: Boolean,
    largeText: Boolean,
    onClick: () -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .testTag("${tagPrefix}_tab_${tab.key}")
            .defaultMinSize(minHeight = if (largeText) 60.dp else 42.dp)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = tab.label
            }
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = tab.label,
            color = if (selected) AtharTheme.colors.accent else AtharTheme.colors.secondaryText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = if (largeText) 2 else 1,
            overflow = TextOverflow.Clip,
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .padding(top = 6.dp)
                .width(46.dp)
                .height(1.5.dp)
                .background(if (selected) AtharTheme.colors.accent else Color.Transparent),
        )
    }
}

@Composable
fun AtharEmptyState(
    title: String,
    body: String,
    tag: String,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 38.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            fontFamily = AtharEditorialFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            lineHeight = 26.sp,
        )
        Text(
            text = body,
            modifier = Modifier.padding(top = 2.dp),
            color = AtharTheme.colors.secondaryText,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * A small text control that opens its options as a menu anchored to itself — the list
 * equivalent of a dropdown, not a bottom sheet. Shows the active value when it differs
 * from the default, so you can read the current state without opening it.
 */
@Composable
fun AtharMenuButton(
    label: String,
    options: List<String>,
    selected: String,
    defaultOption: String,
    onSelect: (String) -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val isDefault = selected == defaultOption

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(role = Role.DropdownList) { expanded = true }
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .testTag("${tagPrefix}_menu"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isDefault) label else selected,
                // Weight, not colour, carries "something is applied" — burgundy is
                // reserved for active navigation and progress.
                color = AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isDefault) FontWeight.Normal else FontWeight.SemiBold,
                maxLines = 1,
            )
            Icon(
                imageVector = AtharIcons.ChevronDown,
                contentDescription = null,
                tint = AtharTheme.colors.secondaryText,
                modifier = Modifier
                    .padding(start = 3.dp)
                    .size(12.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = AtharTheme.colors.canvas,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.testTag("${tagPrefix}_menu_open"),
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            color = if (option == selected) {
                                AtharTheme.colors.accent
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (option == selected) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    trailingIcon = if (option == selected) {
                        {
                            Icon(
                                imageVector = AtharIcons.Check,
                                contentDescription = null,
                                tint = AtharTheme.colors.accent,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.testTag("${tagPrefix}_option_$index"),
                )
            }
        }
    }
}

/**
 * A fact about an item — verse count, file size. Very light beige, no border: it
 * should read as a quiet label, never as a button or a badge worth tapping.
 */
@Composable
fun AtharMetaPill(
    text: String,
    modifier: Modifier = Modifier,
    latinDigits: Boolean = false,
) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(AtharTheme.colors.pressedSurface)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        color = AtharTheme.colors.secondaryText,
        style = if (latinDigits) {
            MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr)
        } else {
            MaterialTheme.typography.labelSmall
        },
        maxLines = 1,
    )
}

/** Save / unsave. Burgundy means saved; there is no second bookmark mark on the row. */
@Composable
fun AtharBookmarkButton(
    saved: Boolean,
    onToggle: () -> Unit,
    itemTitle: String,
    tag: String,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier
            .size(36.dp)
            .testTag(tag),
    ) {
        Icon(
            // Filled when saved: the state should be legible without relying on colour.
            imageVector = if (saved) AtharIcons.BookmarkFilled else AtharIcons.Bookmark,
            contentDescription = if (saved) {
                "إزالة $itemTitle من قائمتي"
            } else {
                "حفظ $itemTitle في قائمتي"
            },
            tint = if (saved) {
                AtharTheme.colors.accent
            } else {
                AtharTheme.colors.secondaryText.copy(alpha = 0.75f)
            },
            modifier = Modifier.size(18.dp),
        )
    }
}
