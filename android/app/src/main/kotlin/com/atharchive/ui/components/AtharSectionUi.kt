package com.atharchive.ui.components

import com.atharchive.ui.theme.AtharCorners
import com.atharchive.ui.theme.AtharTextSize
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
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
    onReselect: () -> Unit = {},
) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val soap = rememberSoapTargets()
    Box(modifier.fillMaxWidth().padding(top = 2.dp)) {
        SoapLayer(soap, tabs.indexOfFirst { it.key == selectedKey }, AtharTheme.colors.primaryAction, Modifier.matchParentSize())
        Row(Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, tab ->
                SectionTabCell(
                    tab = tab,
                    selected = tab.key == selectedKey,
                    largeText = largeText,
                    onClick = {
                        if (tab.key == selectedKey) { soap.reselect(); onReselect() } else onSelect(tab.key)
                    },
                    tagPrefix = tagPrefix,
                    modifier = Modifier.weight(1f),
                    textModifier = Modifier.soapTarget(soap, index),
                )
            }
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
    textModifier: Modifier = Modifier,
) {
    val textColor by animateColorAsState(
        if (selected) AtharTheme.colors.onPrimaryAction else AtharTheme.colors.secondaryText, tween(160), label = "tab_text",
    )
    Box(
        modifier = modifier
            .testTag("${tagPrefix}_tab_${tab.key}")
            .defaultMinSize(minHeight = if (largeText) 60.dp else 48.dp)
            // The soap pill is the feedback; a squish on the label alone would slide off it.
            .clickable(interactionSource = null, indication = null, role = Role.Tab, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = tab.label
            }
            .padding(horizontal = 2.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Solid fill + light text when selected (S9); the fill is the row's soap pill, which travels here.
        Text(
            text = tab.label,
            modifier = textModifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = textColor,
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
    Favourites("favourites", "المفضّلة"),
    Downloaded("downloaded", "المحمّلة"),
    Recent("recent", "الأخيرة"),
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
 * A multi-select filter: its menu stays open while values are ticked, and every ticked value
 * shows under the filter row as a chip with ×.
 */
class AtharFacet(
    val title: String,
    val options: List<String>,
    val selected: Set<String>,
    val onChange: (Set<String>) -> Unit,
    /** A quieter second line per option, e.g. «ت ٧٢٨ · ٥ كتب». */
    val subtitle: (String) -> String? = { null },
)

/** The sort and other single choices as menu buttons, then the facets, then the ticked values as chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AtharFilterButton(
    groups: List<AtharChipGroup>,
    tagPrefix: String,
    modifier: Modifier = Modifier,
    facets: List<AtharFacet> = emptyList(),
) {
    Column(modifier) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            groups.filter { it.options.isNotEmpty() }.forEachIndexed { index, group ->
                AtharMenuButton(group.title, group.options, group.selected, group.default, group.onSelect, "${tagPrefix}_$index")
            }
            facets.filter { it.options.isNotEmpty() }.forEachIndexed { index, facet -> FacetMenu(facet, "${tagPrefix}_f$index") }
        }
        val chips = facets.flatMap { facet -> facet.selected.map { facet to it } }
        if (chips.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(
            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            chips.forEach { (facet, value) -> RemovableChip(value) { facet.onChange(facet.selected - value) } }
            if (chips.size >= 2) Text(
                "مسح الكل",
                Modifier
                    .clip(AtharCorners.Control)
                    .clickable(role = Role.Button) { facets.forEach { if (it.selected.isNotEmpty()) it.onChange(emptySet()) } }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("${tagPrefix}_clear"),
                color = AtharTheme.colors.accent,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun FacetMenu(facet: AtharFacet, tag: String) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // Ticked values float to the top when the menu opens, not while ticking (rows would jump).
    var pinned by remember { mutableStateOf(emptySet<String>()) }
    val active = facet.selected.isNotEmpty()
    Box {
        Row(
            Modifier
                .clip(AtharCorners.Control)
                .clickable(role = Role.DropdownList) { pinned = facet.selected; open = true }
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .testTag("${tag}_menu"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (active) "${facet.title} · ${arabicDigits(facet.selected.size)}" else facet.title,
                color = if (active) AtharTheme.colors.accent else AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
            Icon(AtharIcons.ChevronDown, null, tint = AtharTheme.colors.secondaryText, modifier = Modifier.padding(start = 3.dp).size(12.dp))
        }
        AtharMenu(expanded = open, onDismiss = { open = false; query = "" }, width = 280.dp) {
            val long = facet.options.size > 10
            if (long) OutlinedTextField(query, { query = it }, label = { Text("ابحث في ${facet.title}") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("${tag}_query"))
            val normalized = remember(facet.options) { facet.options.associateWith { com.atharchive.core.ArNormalize.normalize(it) } }
            val term = com.atharchive.core.ArNormalize.normalize(query)
            val matching = remember(facet.options, pinned, term) {
                facet.options.filter { normalized.getValue(it).contains(term) }.sortedByDescending { it in pinned }
            }
            if (matching.isEmpty()) Text("لا توجد نتائج", Modifier.padding(14.dp), color = AtharTheme.colors.secondaryText)
            @Composable fun option(value: String) {
                val ticked = value in facet.selected
                AtharMenuItem(value, checked = ticked, subtitle = facet.subtitle(value),
                    onClick = { facet.onChange(if (ticked) facet.selected - value else facet.selected + value) },
                    modifier = Modifier.testTag("${tag}_${facet.options.indexOf(value)}"))
            }
            // ponytail: fixed-height list inside the menu so hundreds of authors stay lazy
            if (long) LazyColumn(Modifier.fillMaxWidth().height(280.dp)) {
                items(matching.size, key = { matching[it] }) { option(matching[it]) }
            } else matching.forEach { option(it) }
        }
    }
}

@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 36.dp)
            .clip(AtharCorners.Control)
            .background(AtharTheme.colors.accent.copy(alpha = 0.13f))
            .clickable(role = Role.Button, onClick = onRemove)
            .semantics { contentDescription = "إزالة $label" }
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(AtharIcons.Close, null, tint = AtharTheme.colors.secondaryText, modifier = Modifier.size(14.dp))
    }
}

private const val SetSeparator = '\u001F'

/** A persisted multi-choice ([rememberPersisted] keeps strings). */
fun String.asChoiceSet(): Set<String> = if (isEmpty()) emptySet() else split(SetSeparator).toSet()
fun Set<String>.asStoredChoices(): String = joinToString(SetSeparator.toString())

@Composable
fun AtharFilterMenu(title: String, options: List<String>, selected: Set<String>, tag: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    Box {
        TextButton(onClick = { open = true }, modifier = Modifier.testTag("${tag}_menu")) {
            Text("$title ▾", color = AtharTheme.colors.secondaryText)
        }
        AtharMenu(expanded = open, onDismiss = { open = false }) {
            if (options.size > 10) OutlinedTextField(query, { query = it }, label = { Text("ابحث في الخيارات") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("${tag}_query"))
            val term = com.atharchive.core.ArNormalize.normalize(query)
            val matching = options.distinct().filter { com.atharchive.core.ArNormalize.normalize(it).contains(term) }
            if (matching.isEmpty()) Text("لا توجد نتائج", Modifier.padding(14.dp), color = AtharTheme.colors.secondaryText)
            @Composable fun option(value: String) {
                AtharMenuItem(value, checked = value in selected, onClick = { onSelect(value); open = false }, modifier = Modifier.testTag("${tag}_${options.indexOf(value)}"))
            }
            if (options.size > 10) LazyColumn(Modifier.fillMaxWidth().height(240.dp)) {
                items(matching.size) { option(matching[it]) }
            } else matching.forEach { option(it) }

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
 * A section's header (title, search, tabs, filters), moved like the reader's bars: it floats
 * over the list and travels with it 1:1 (its own height of scroll hides it, so it never outruns
 * the finger), and settles open or closed with the same spring when the finger lets go.
 *
 * Driven by nested-scroll deltas (the user's gesture), not by the list position.
 */
@androidx.compose.runtime.Stable
class AtharCollapsingHeader(private val minDistance: Float) {
    /** 0 shown … 1 hidden. */
    var hidden by androidx.compose.runtime.mutableFloatStateOf(0f)
        private set
    /** Measured by [AtharFloatingHeader]; the list's top padding. */
    var height by mutableStateOf(0.dp)
        internal set
    /** [height] in px: the scroll that hides it. */
    internal var heightPx = 0f
    /** How far the list is from its top; the header never hides further, so no blank band opens above the first row. */
    internal var scrolled: () -> Float = { Float.MAX_VALUE }
    private fun maxHidden() = (scrolled() / maxOf(heightPx, minDistance)).coerceIn(0f, 1f)

    suspend fun settle(target: Float) = androidx.compose.animation.core.animate(
        hidden, target,
        animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
    ) { value, _ -> hidden = value }

    /** Back in view, e.g. after a filter or search shrank the list and left nothing to scroll. */
    suspend fun show() = settle(0f)

    val connection = object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
        override fun onPreScroll(
            available: androidx.compose.ui.geometry.Offset,
            source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
        ): androidx.compose.ui.geometry.Offset {
            hidden = (hidden - available.y / maxOf(heightPx, minDistance)).coerceIn(0f, maxHidden())
            return androidx.compose.ui.geometry.Offset.Zero
        }

        override suspend fun onPostFling(
            consumed: androidx.compose.ui.unit.Velocity,
            available: androidx.compose.ui.unit.Velocity,
        ): androidx.compose.ui.unit.Velocity {
            settle(if (hidden > 0.5f && maxHidden() >= 1f) 1f else 0f)
            return androidx.compose.ui.unit.Velocity.Zero
        }
    }
}

/** Shows the header whenever [keys] (tab, query, filters, sort) change or the list is at its top. */
@Composable
fun AtharCollapsingHeader.ShowOnChange(listState: androidx.compose.foundation.lazy.LazyListState, vararg keys: Any?) {
    LaunchedEffect(*keys) { show() }
    // Row 0's own offset: the index alone moves on once a row slides under the header's padding.
    scrolled = {
        val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
        when {
            first == null -> 0f
            first.index == 0 -> -first.offset.toFloat()
            else -> Float.MAX_VALUE
        }
    }
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.canScrollBackward }.collect { if (!it) show() }
    }
}

@Composable
fun rememberAtharCollapsingHeader(): AtharCollapsingHeader {
    val density = LocalDensity.current
    return remember(density) { AtharCollapsingHeader(with(density) { 64.dp.toPx() }) }
}

/** The header itself: put it in a Box with the list, which takes [AtharCollapsingHeader.height] as its top padding. */
@Composable
fun AtharFloatingHeader(header: AtharCollapsingHeader, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val density = LocalDensity.current
    Column(
        modifier = Modifier
            .zIndex(1f)
            .fillMaxWidth()
            .onSizeChanged { header.height = with(density) { it.height.toDp() }; header.heightPx = it.height.toFloat() }
            .graphicsLayer { translationY = -header.hidden * size.height }
            .background(AtharTheme.colors.canvas),
        content = content,
    )
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
    val actionAnchor = LocalAtharActionAnchor.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
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
        transfer is TransferUi.Running -> "إلغاء تنزيل $title، $label"
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
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .clickable(role = Role.Button) { actionAnchor?.value = bounds; onClick() }
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
                    imageVector = if (transfer is TransferUi.Paused) AtharIcons.Play else AtharIcons.Close,
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
