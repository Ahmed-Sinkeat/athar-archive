package com.atharchive.ui.components

import com.atharchive.ui.theme.AtharCorners
import com.atharchive.ui.theme.AtharTextSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
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
    Box(
        modifier = modifier
            .testTag("${tagPrefix}_tab_${tab.key}")
            .defaultMinSize(minHeight = if (largeText) 60.dp else 48.dp)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = tab.label
            }
            .padding(horizontal = 2.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Solid fill + light text when selected (S9); a tinted background with accent text read as disabled.
        Text(
            text = tab.label,
            modifier = Modifier
                .clip(AtharCorners.Control)
                .background(if (selected) AtharTheme.colors.primaryAction else Color.Transparent)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            color = if (selected) AtharTheme.colors.onPrimaryAction else AtharTheme.colors.secondaryText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = if (largeText) 2 else 1,
            overflow = TextOverflow.Clip,
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
                modifier = if (isDefault) Modifier else Modifier
                    .clip(AtharCorners.Control)
                    .background(AtharTheme.colors.primaryAction)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                color = if (isDefault) AtharTheme.colors.secondaryText else AtharTheme.colors.onPrimaryAction,
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

class AtharChipGroup(
    val title: String,
    /** The first option is the "no filter" default, e.g. «الكل». */
    val options: List<String>,
    val selected: String,
    val onSelect: (String) -> Unit,
) {
    val default get() = options.first()
    val active get() = selected != default
}

/**
 * One «تصفية» button per section. It opens one sheet of tap-to-choose chips per group;
 * taps apply immediately and «مسح» resets every group. The sheet is the only scroll.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AtharFilterButton(
    groups: List<AtharChipGroup>,
    tagPrefix: String,
    modifier: Modifier = Modifier,
    activeCount: Int = groups.count { it.active },
    onClear: () -> Unit = { groups.forEach { if (it.active) it.onSelect(it.default) } },
) {
    if (groups.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .clip(AtharCorners.Control)
            .clickable(role = Role.Button) { open = true }
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 4.dp)
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
    if (open) {
        ModalBottomSheet(
            onDismissRequest = { open = false },
            containerColor = AtharTheme.colors.canvas,
            scrimColor = AtharTheme.colors.scrim,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                    .testTag("${tagPrefix}_sheet"),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "تصفية",
                        modifier = Modifier.weight(1f),
                        fontFamily = AtharEditorialFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = AtharTextSize.Large,
                    )
                    TextButton(onClick = onClear, enabled = activeCount > 0, modifier = Modifier.testTag("${tagPrefix}_clear")) { Text("مسح") }
                }
                groups.forEachIndexed { g, group ->
                    Text(
                        text = group.title,
                        modifier = Modifier.padding(top = 6.dp),
                        color = AtharTheme.colors.secondaryText,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.options.forEachIndexed { index, option ->
                            AtharChip(
                                label = option,
                                selected = option == group.selected,
                                onClick = { group.onSelect(option) },
                                modifier = Modifier.testTag("${tagPrefix}_chip_${g}_$index"),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Solid accent when selected (S9); 48 dp touch target around a slimmer pill. */
@Composable
fun AtharChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            modifier = Modifier
                .clip(AtharCorners.Control)
                .background(if (selected) AtharTheme.colors.primaryAction else AtharTheme.colors.divider.copy(alpha = 0.28f))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            color = if (selected) AtharTheme.colors.onPrimaryAction else AtharTheme.colors.secondaryText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** A String backed by SharedPreferences "list_state": sort/filter choices survive restarts. */
@Composable
fun rememberPersisted(key: String, default: String): MutableState<String> {
    val prefs = LocalContext.current.getSharedPreferences("list_state", android.content.Context.MODE_PRIVATE)
    return remember(key) { PersistedState(prefs, key, runCatching { prefs.getString(key, null) }.getOrNull() ?: default) }
}

private class PersistedState(
    private val prefs: android.content.SharedPreferences,
    private val key: String,
    initial: String,
) : MutableState<String> {
    private val inner = mutableStateOf(initial)
    override var value: String
        get() = inner.value
        set(v) { inner.value = v; prefs.edit().putString(key, v).apply() }
    override fun component1() = value
    override fun component2(): (String) -> Unit = { value = it }
}

/** A stored enum name that no longer exists (older build) reads as null → caller falls back. */
inline fun <reified E : Enum<E>> enumValueOrNull(name: String): E? = enumValues<E>().firstOrNull { it.name == name }

/** A stored choice that is no longer among [options] reads as the default (first) option. */
fun String.orFirstIn(options: List<String>): String = if (this in options) this else options.first()

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

    /** Back in view, e.g. after a filter or search shrank the list and left nothing to scroll. */
    fun show() {
        visible = true
        travelled = 0f
    }

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

    private companion object { const val Threshold = 24f }
}

/** Shows the header whenever [keys] (tab, query, filters, sort) change or the list is at its top. */
@Composable
fun AtharCollapsingHeader.ShowOnChange(listState: androidx.compose.foundation.lazy.LazyListState, vararg keys: Any?) {
    LaunchedEffect(*keys) { show() }
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.canScrollBackward }.collect { if (!it) show() }
    }
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
