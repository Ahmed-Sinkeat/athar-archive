package com.atharchive.ui.components

import com.atharchive.ui.theme.AtharCorners
import com.atharchive.ui.theme.AtharTextSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
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
import com.atharchive.feature.downloads.TransferUi
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
        shape = AtharCorners.Card,
        // Nearly flush with the page: a search field should read as part of the
        // surface it sits on, not as a card floating above it.
        color = AtharTheme.colors.inputSurface,
        border = BorderStroke(1.dp, AtharTheme.colors.inputBorder),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
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
                                color = AtharTheme.colors.placeholderText,
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
            SectionTabCell(
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
private fun SectionTabCell(
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
        // Fixed gap, not weight(1f): weight swallows all the height once the row is in a bounded column.
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .padding(top = 6.dp)
                .width(46.dp)
                .height(1.5.dp)
                .background(if (selected) AtharTheme.colors.accent else Color.Transparent),
        )
    }
}

/** The one tab set every content section shows. */
enum class SectionTab(val key: String, val label: String) {
    All("all", "الكل"),
    Recent("recent", "الأخيرة"),
    Downloaded("downloaded", "المحمّلة"),
    Favourites("favourites", "المفضّلة"),
    ;

    fun matches(recent: Boolean, downloaded: Boolean, saved: Boolean): Boolean = when (this) {
        All -> true
        Recent -> recent
        Downloaded -> downloaded
        Favourites -> saved
    }

    companion object {
        val tabs: List<AtharTab> = entries.map { AtharTab(it.key, it.label) }
        fun fromKey(key: String): SectionTab = entries.first { it.key == key }
    }
}

/** Empty-state copy for the tab-only case; [noun] is the plural, e.g. «كتب»; [downloadHint] is the المحمّلة body. */
fun SectionTab.emptyCopy(noun: String, downloadHint: String): Pair<String, String> = when (this) {
    SectionTab.Recent -> "لا توجد $noun أخيرة" to "ستظهر هنا ما فتحته مؤخرًا."
    SectionTab.Downloaded -> "لا توجد $noun محمّلة" to downloadHint
    SectionTab.Favourites -> "لا توجد عناصر مفضّلة" to "اضغط النجمة على أي عنصر ليظهر هنا."
    SectionTab.All -> "لا توجد $noun" to "لم تتوفر $noun في هذا القسم بعد."
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
            fontSize = AtharTextSize.Title,
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
    onSelectIndex: ((Int) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val isDefault = selected == defaultOption

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(AtharCorners.Control)
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
        AtharMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            modifier = Modifier.testTag("${tagPrefix}_menu_open"),
        ) {
            options.forEachIndexed { index, option ->
                AtharMenuItem(
                    label = option,
                    checked = option == selected,
                    onClick = {
                        if (onSelectIndex != null) onSelectIndex(index) else onSelect(option)
                        expanded = false
                    },
                    modifier = Modifier.testTag("${tagPrefix}_option_$index"),
                )
            }
        }
    }
}

class AtharFilterGroup(
    val label: String,
    /** The first option is the "no filter" default, e.g. «كل المؤلفين». */
    val options: List<String>,
    val selected: String,
    val onSelect: (String) -> Unit,
) {
    val default get() = options.first()
    val active get() = selected != default
}

/**
 * One «تصفية» control for several filters: the menu lists the groups with their current
 * value; tapping one drills into its options (iOS-style) and the top row comes back.
 */
@Composable
fun AtharFilterMenu(groups: List<AtharFilterGroup>, tagPrefix: String, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Int?>(null) }
    val activeCount = groups.count { it.active }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(AtharCorners.Control)
                .clickable(role = Role.DropdownList) { open = null; expanded = true }
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .testTag("${tagPrefix}_menu"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (activeCount == 0) "تصفية" else "تصفية · ${arabicDigits(activeCount)}",
                color = AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (activeCount == 0) FontWeight.Normal else FontWeight.SemiBold,
                maxLines = 1,
            )
            Icon(
                imageVector = AtharIcons.ChevronDown,
                contentDescription = null,
                tint = AtharTheme.colors.secondaryText,
                modifier = Modifier.padding(start = 3.dp).size(12.dp),
            )
        }
        AtharMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            modifier = Modifier.testTag("${tagPrefix}_menu_open"),
        ) {
            val group = open?.let(groups::getOrNull)
            if (group == null) {
                groups.forEachIndexed { index, g ->
                    AtharMenuItem(
                        label = g.label,
                        subtitle = g.selected,
                        icon = AtharIcons.Forward,
                        onClick = { open = index },
                        modifier = Modifier.testTag("${tagPrefix}_group_$index"),
                    )
                }
                if (activeCount > 0) {
                    AtharMenuDivider()
                    AtharMenuItem(
                        label = "إزالة التصفية",
                        destructive = true,
                        onClick = {
                            groups.forEach { if (it.active) it.onSelect(it.default) }
                            expanded = false
                        },
                        modifier = Modifier.testTag("${tagPrefix}_clear"),
                    )
                }
            } else {
                AtharMenuItem(
                    label = group.label,
                    icon = AtharIcons.Back,
                    onClick = { open = null },
                    modifier = Modifier.testTag("${tagPrefix}_back"),
                )
                AtharMenuDivider()
                group.options.forEachIndexed { index, option ->
                    AtharMenuItem(
                        label = option,
                        checked = option == group.selected,
                        onClick = {
                            group.onSelect(option)
                            expanded = false
                        },
                        modifier = Modifier.testTag("${tagPrefix}_option_$index"),
                    )
                }
            }
        }
    }
}

/** Western digits to Arabic-Indic; anything else is kept as is. */
fun arabicDigits(text: String): String = text.map { if (it in '0'..'9') '\u0660' + (it - '0') else it }.joinToString("")
fun arabicDigits(value: Int): String = arabicDigits(value.toString())

/**
 * Tab and filter rows that get out of the way: hidden while the list scrolls down, back on
 * the first scroll up. The page title and the search field stay outside it, pinned.
 *
 * Driven by nested-scroll deltas (the user's gesture), not by the list position, so the
 * list re-clamping when the header collapses can never flip it back and forth.
 */
@androidx.compose.runtime.Stable
class AtharCollapsingHeader {
    var visible by mutableStateOf(true)
        private set
    private var travelled = 0f

    /** A tap, for chrome that also toggles by touch (the reader). */
    fun toggle() {
        visible = !visible
        travelled = 0f
    }

    val connection = object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
        override fun onPreScroll(
            available: androidx.compose.ui.geometry.Offset,
            source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
        ): androidx.compose.ui.geometry.Offset {
            val dy = available.y
            // A reversal restarts the count, so a small wobble never toggles the header.
            if (dy != 0f && (dy > 0f) != (travelled > 0f)) travelled = 0f
            travelled += dy
            if (travelled < -Threshold) visible = false else if (travelled > Threshold) visible = true
            return androidx.compose.ui.geometry.Offset.Zero
        }
    }

    private companion object { const val Threshold = 36f }
}

@Composable
fun rememberAtharCollapsingHeader(): AtharCollapsingHeader = remember { AtharCollapsingHeader() }

@Composable
fun AtharCollapsible(header: AtharCollapsingHeader, content: @Composable () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(
        visible = header.visible,
        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
    ) { content() }
}

/**
 * The one download control for every row (books, poems, articles, questions): the state
 * and the size are always readable, the same way everywhere. [onClick] does not remove a
 * finished download by itself — the caller asks first.
 */
@Composable
fun AtharDownloadButton(
    title: String,
    sizeLabel: String,
    downloaded: Boolean,
    transfer: TransferUi?,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val colors = AtharTheme.colors
    val progress = when (transfer) {
        is TransferUi.Running -> transfer.progress
        is TransferUi.Paused -> transfer.progress
        is TransferUi.Failed -> transfer.progress
        null -> 0f
    }
    // All bytes are here but verification and import still run: say so, not a stuck 100%.
    val finishing = transfer is TransferUi.Running && progress >= 1f
    val label = when {
        transfer is TransferUi.Failed -> "إعادة"
        transfer is TransferUi.Paused -> "متوقف"
        finishing -> "إنهاء"
        transfer is TransferUi.Running -> "${arabicDigits((progress * 100).toInt())}٪"
        else -> sizeLabel
    }
    val description = when {
        transfer is TransferUi.Failed -> "إعادة تنزيل $title"
        transfer is TransferUi.Paused -> "استئناف تنزيل $title، $label"
        finishing -> "جارٍ إنهاء تنزيل $title"
        transfer is TransferUi.Running -> "إيقاف تنزيل $title مؤقتًا، $label"
        downloaded -> "$title محمّل، الحجم $sizeLabel. اضغط للإزالة"
        else -> "تنزيل $title، الحجم $sizeLabel"
    }
    val tint = when {
        transfer is TransferUi.Failed -> colors.accent
        transfer != null -> colors.accent
        downloaded -> colors.success
        else -> colors.secondaryText
    }
    Column(
        modifier = modifier
            .width(56.dp)
            .defaultMinSize(minHeight = 48.dp)
            .clip(AtharCorners.Card)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                if (transfer is TransferUi.Running && !finishing) {
                    progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
                }
            }
            .padding(vertical = 6.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            if (transfer != null && transfer !is TransferUi.Failed) {
                if (finishing) CircularProgressIndicator(
                    modifier = Modifier.size(24.dp), color = colors.accent, strokeWidth = 2.5.dp,
                ) else CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(24.dp), color = colors.accent,
                    trackColor = colors.divider, strokeWidth = 2.5.dp,
                )
                if (!finishing) Icon(
                    imageVector = if (transfer is TransferUi.Paused) AtharIcons.Play else AtharIcons.Pause,
                    contentDescription = null, tint = tint, modifier = Modifier.size(11.dp),
                )
            } else Icon(
                imageVector = when {
                    transfer is TransferUi.Failed -> AtharIcons.Repeat
                    downloaded -> AtharIcons.Check
                    else -> AtharIcons.Download
                },
                contentDescription = null, tint = tint, modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label,
            color = if (transfer == null && !downloaded) colors.secondaryText else tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (transfer != null) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
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
            .clip(AtharCorners.Tag)
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

/** Favourite / unfavourite (المفضّلة). Filled star means saved; the bookmark icon is for reader pages only. */
@Composable
fun AtharFavouriteButton(
    saved: Boolean,
    onToggle: () -> Unit,
    itemTitle: String,
    tag: String,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier
            .size(48.dp)
            .testTag(tag),
    ) {
        Icon(
            // Filled when saved: the state should be legible without relying on colour.
            imageVector = if (saved) AtharIcons.StarFilled else AtharIcons.Star,
            contentDescription = if (saved) {
                "إزالة $itemTitle من المفضّلة"
            } else {
                "إضافة $itemTitle إلى المفضّلة"
            },
            tint = if (saved) {
                AtharTheme.colors.accent
            } else {
                AtharTheme.colors.secondaryText
            },
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Fills from the start edge, which under RTL is the right. Nothing mirrors by hand;
 * the layout direction does it.
 */
@Composable
fun AtharProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    track: Color = AtharTheme.colors.divider,
) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(track)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f) },
    ) {
        Box(
            Modifier
                .fillMaxWidth(clamped)
                .height(height)
                .clip(CircleShape)
                .background(AtharTheme.colors.accent),
        )
    }
}

/** A heading inside a scrolling list — one rank below the page title. */
@Composable
fun AtharSectionHeading(
    text: String,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
    top: Dp = 16.dp,
) {
    Text(
        text = text,
        modifier = modifier.padding(
            start = horizontalPadding,
            end = horizontalPadding,
            top = top,
            bottom = 4.dp,
        ),
        color = MaterialTheme.colorScheme.onBackground,
        fontFamily = AtharEditorialFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = AtharTextSize.Title,
    )
}
