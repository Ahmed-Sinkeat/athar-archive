package com.atharchive.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.atharchive.ui.theme.AtharCorners
import com.atharchive.ui.theme.AtharTextSize
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
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
    /** Sits at the end of the field, after ×: the search screen's filter button. */
    trailing: (@Composable () -> Unit)? = null,
    /** How many rows the typed words and filters left, shown in the field while there is a query or a filter. */
    resultCount: Int? = null,
    filtered: Boolean = false,
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
            if ((query.isNotBlank() || filtered) && resultCount != null) Text(
                text = com.atharchive.data.arabicCount(resultCount, "لا نتائج", "نتيجة واحدة", "نتيجتان", "نتائج", "نتيجة"),
                modifier = Modifier.padding(start = 6.dp).testTag("${tagPrefix}_search_count"),
                color = AtharTheme.colors.secondaryText,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
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
            } else {
                // Voice search through the phone's own recogniser: no permission, no library.
                val context = LocalContext.current
                val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onQueryChange)
                }
                IconButton(
                    onClick = {
                        try {
                            voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
                                .putExtra(RecognizerIntent.EXTRA_PROMPT, placeholder))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, "البحث الصوتي غير متاح على هذا الجهاز", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("${tagPrefix}_search_voice"),
                ) {
                    Icon(
                        imageVector = AtharIcons.Mic,
                        contentDescription = "البحث الصوتي",
                        tint = AtharTheme.colors.secondaryText,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            trailing?.invoke()
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
                    // By label length: equal cells cut «مسائل» to «مسائ» once a row holds seven.
                    modifier = Modifier.weight(tab.label.length + 2f),
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
    /** [AtharDirectionGroup]: drawn as one arrow beside «الترتيب», not as a row or a filter. */
    val isDirection: Boolean = false,
    val onSelect: (String) -> Unit,
) {
    val default get() = options.first()
    val active get() = !isDirection && selected != default
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
    /** Set: always chips, each showing this short label (centuries «٨» under «القرن»). */
    val chipLabel: ((String) -> String)? = null,
    /** Always the typed picker, however few the values (الكناشة's authors and books, as in الكتب). */
    val typed: Boolean = false,
)

/** «القرن» with all fifteen centuries as short chips, whether or not the list has one yet. */
fun centuryFacet(selected: Set<String>, onChange: (Set<String>) -> Unit) =
    AtharFacet("القرن", com.atharchive.data.AllCenturies, selected, onChange, chipLabel = { it.removePrefix("القرن ") })

/** «الصوت»: one tick, «له تسجيل». */
const val HasAudioChoice = "له تسجيل"

/** Whether [topics] fall under [section] («كل الأقسام» takes everything). */
/** How many of [items] fall in each section (and [SectionAll]), for the section popup. */
fun <T> sectionCounts(items: List<T>, topics: (T) -> Collection<String>): Map<String, Int> =
    items.flatMap { com.atharchive.data.subjectsOf(topics(it)) }.groupingBy { it }.eachCount() + (SectionAll to items.size)

// A section saved under an old name («القرآن») reads as all, not as an empty list.
fun inSection(section: String, topics: Collection<String>) =
    section == SectionAll || section !in com.atharchive.data.SubjectOrder || section in com.atharchive.data.subjectsOf(topics)

/**
 * Every filter behind one icon at the end of the search field (as the main search): one popup, a
 * line per setting with its label beside the choices, and a badge counting what is set. A facet
 * of a dozen values or fewer is chips; a long one (authors, topics) is typed, five matches shown.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AtharFilterIcon(groups: List<AtharChipGroup>, facets: List<AtharFacet>, tagPrefix: String) {
    var open by remember { mutableStateOf(false) }
    val count = groups.count { it.active } + facets.sumOf { it.selected.size }
    val pins = rememberFilterPins(groups, facets, tagPrefix)
    // The sort's direction is one tap, so it sits outside the popup, beside the icon.
    groups.firstOrNull { it.isDirection }?.let { AtharDirectionButton(it) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(48.dp).testTag("${tagPrefix}_button")) {
            Box {
                Icon(AtharIcons.Filter, "المرشحات", tint = if (count > 0) AtharTheme.colors.accent else AtharTheme.colors.secondaryText, modifier = Modifier.size(21.dp))
                if (count > 0) Text(
                    arabicDigits(count),
                    Modifier.align(Alignment.TopEnd).padding(start = 14.dp).size(15.dp).clip(androidx.compose.foundation.shape.CircleShape).background(AtharTheme.colors.accent),
                    color = AtharTheme.colors.onPrimaryAction,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        AtharMenu(expanded = open, onDismiss = { open = false }, width = 310.dp, modifier = Modifier.testTag("${tagPrefix}_popup")) {
            AtharFilterPopupContent(groups, facets, tagPrefix)
            if (!pins.atDefault || pins.pinned) AtharMenuDivider()
            if (!pins.atDefault || pins.pinned) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!pins.atDefault) AtharFilterChip("مسح", false) { pins.reset() }
                if (pins.pinned && pins.atDefault) AtharFilterChip("إلغاء التثبيت", false) { pins.unpin() }
                else if (!pins.atDefault) AtharFilterChip("تثبيت كافتراضي", false) { pins.pin() }
            }
        }
    }
}

/**
 * «تثبيت كافتراضي»: what the popup holds now becomes the list's default. The app opens on it and
 * «مسح» returns to it; with nothing pinned the default is no filter. Stored beside the choices in
 * "list_state" as `pin.<list>.<title>`, so «إعادة الإعدادات الافتراضية» clears pins too.
 */
class AtharFilterPins(
    val pinned: Boolean,
    /** Every choice is at its default (the pin, or no filter). */
    val atDefault: Boolean,
    val pin: () -> Unit,
    val unpin: () -> Unit,
    val reset: () -> Unit,
)

// Pins already applied in this process: a cold start opens on the pin once, then choices are free.
private val pinsApplied = mutableSetOf<String>()

@Composable
private fun rememberFilterPins(groups: List<AtharChipGroup>, facets: List<AtharFacet>, tagPrefix: String): AtharFilterPins {
    val prefs = LocalContext.current.getSharedPreferences("list_state", android.content.Context.MODE_PRIVATE)
    var generation by remember { mutableStateOf(0) }
    generation.let { }   // read, so a pin written below recomposes the popup
    val all = groups.filter { it.options.isNotEmpty() }
    fun key(title: String) = "pin.$tagPrefix.$title"
    fun stored(title: String): String? = runCatching { prefs.getString(key(title), null) }.getOrNull()
    fun groupDefault(g: AtharChipGroup) = stored(g.title)?.takeIf { it in g.options } ?: g.default
    // A pinned value the catalog no longer has drops out, as the screens drop it from the choices.
    fun facetDefault(f: AtharFacet) = (stored(f.title)?.asChoiceSet() ?: emptySet()) intersect f.options.toSet()
    fun reset() {
        all.forEach { g -> groupDefault(g).let { if (it != g.selected) g.onSelect(it) } }
        facets.forEach { f -> facetDefault(f).let { if (it != f.selected) f.onChange(it) } }
    }
    // Per choice, not per list: a facet whose values load later (authors) still gets its pin.
    LaunchedEffect(tagPrefix, all.map { it.title }, facets.map { it.title }) {
        all.filter { pinsApplied.add(key(it.title)) }.forEach { g -> stored(g.title)?.takeIf { it in g.options && it != g.selected }?.let(g.onSelect) }
        facets.filter { pinsApplied.add(key(it.title)) }.forEach { f -> stored(f.title)?.asChoiceSet()?.takeIf { it != f.selected }?.let(f.onChange) }
    }
    val titles = all.map { it.title } + facets.map { it.title }
    return AtharFilterPins(
        pinned = titles.any { stored(it) != null },
        atDefault = all.all { groupDefault(it) == it.selected } && facets.all { facetDefault(it) == it.selected },
        pin = {
            prefs.edit().apply {
                all.forEach { putString(key(it.title), it.selected) }
                facets.forEach { putString(key(it.title), it.selected.asStoredChoices()) }
            }.apply()
            generation++
        },
        unpin = { prefs.edit().apply { titles.forEach { remove(key(it)) } }.apply(); generation++ },
        reset = ::reset,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AtharFilterPopupContent(groups: List<AtharChipGroup>, facets: List<AtharFacet>, tagPrefix: String) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
        var first = true
        @Composable fun divider() { if (!first) AtharMenuDivider(); first = false }
        groups.filter { it.options.isNotEmpty() && !it.isDirection }.forEach { group ->
            divider()
            AtharFilterRow(group.title) {
                group.options.forEach { AtharFilterChip(it, it == group.selected) { group.onSelect(it) } }
            }
        }
        facets.filter { it.options.isNotEmpty() }.forEachIndexed { index, facet ->
            divider()
            // Centuries: one line that scrolls sideways, not three lines of chips.
            if (facet.chipLabel != null) AtharFilterRow(facet.title, scroll = true) {
                facet.options.forEach { value ->
                    val ticked = value in facet.selected
                    AtharFilterChip(facet.chipLabel.invoke(value), ticked) { facet.onChange(if (ticked) facet.selected - value else facet.selected + value) }
                }
            } else if (facet.options.size <= 12 && !facet.typed) AtharFilterRow(facet.title) {
                facet.options.forEach { value ->
                    val ticked = value in facet.selected
                    AtharFilterChip(value, ticked) { facet.onChange(if (ticked) facet.selected - value else facet.selected + value) }
                }
            } else AtharFilterPicker(facet.title, facet.options, facet.selected, facet.subtitle, "${tagPrefix}_f$index") {
                facet.onChange(if (it in facet.selected) facet.selected - it else facet.selected + it)
            }
        }
    }
}

/** One popup line: the setting's name, then its choices. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AtharFilterRow(title: String, scroll: Boolean = false, choices: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.width(58.dp), color = AtharTheme.colors.secondaryText,
            fontSize = AtharTextSize.Small, fontWeight = FontWeight.SemiBold)
        if (scroll) Row(Modifier.weight(1f).horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            choices()
        } else androidx.compose.foundation.layout.FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            choices()
        }
    }
}

/** The sort's direction as one arrow that turns over when tapped. */
@Composable
fun AtharDirectionButton(direction: AtharChipGroup) {
    val descending = direction.selected == direction.options.last()
    val turn by androidx.compose.animation.core.animateFloatAsState(if (descending) 0f else 180f, label = "direction")
    Box(
        Modifier.size(26.dp)
            .atharControl(AtharTheme.colors.divider.copy(alpha = 0.32f), AtharCorners.Control)
            .clickable(role = Role.Button, onClickLabel = "عكس الترتيب") { direction.onSelect(direction.options.first { it != direction.selected }) }
            .semantics { contentDescription = direction.selected },
        contentAlignment = Alignment.Center,
    ) { Icon(AtharIcons.ChevronDown, null, Modifier.size(16.dp).graphicsLayer { rotationZ = turn }, tint = AtharTheme.colors.accent) }
}

/** A choice pill sized for the popup: solid accent when on, plain readable text when off. */
@Composable
fun AtharFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        Modifier
            .heightIn(min = 26.dp)
            .then(
                if (selected) Modifier.clip(AtharCorners.Control).background(AtharTheme.colors.primaryAction)
                else Modifier.atharControl(AtharTheme.colors.divider.copy(alpha = 0.32f), AtharCorners.Control),
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 9.dp, vertical = 3.dp),
        color = if (selected) AtharTheme.colors.onPrimaryAction else MaterialTheme.colorScheme.onSurface,
        fontSize = AtharTextSize.Body,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
    )
}

/**
 * A long list (hundreds of authors) typed, not listed: the filter popup keeps its size, and what
 * is typed opens a small list just under the field (five rows tall, scrolling inside), the only
 * scrolling list in the filter. Ticked names show as chips under the search field.
 */
@Composable
fun AtharFilterPicker(title: String, options: List<String>, selected: Set<String>, subtitle: (String) -> String?, tag: String, onToggle: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var fieldSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val density = LocalDensity.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.width(58.dp), color = AtharTheme.colors.secondaryText,
            fontSize = AtharTextSize.Small, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f)) {
            // A slim line like the chips beside it, not a boxed form field.
            androidx.compose.foundation.text.BasicTextField(
                query, { query = it },
                Modifier.fillMaxWidth().heightIn(min = 28.dp)
                    .onSizeChanged { fieldSize = it }
                    .atharControl(AtharTheme.colors.divider.copy(alpha = 0.32f), AtharCorners.Control)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("${tag}_query"),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(AtharTheme.colors.accent),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text("اكتب للبحث…", color = AtharTheme.colors.placeholderText, fontSize = AtharTextSize.Body, maxLines = 1)
                        inner()
                    }
                },
            )
            val term = com.atharchive.core.ArNormalize.normalize(query.trim())
            val matches = remember(term, options) { if (term.isEmpty()) emptyList() else options.filter { normKey(it).contains(term) } }
            if (term.isNotEmpty()) androidx.compose.ui.window.Popup(
                offset = androidx.compose.ui.unit.IntOffset(0, fieldSize.height + with(density) { 4.dp.roundToPx() }),
                // Not focusable: the keyboard stays on the field while the list follows the typing.
                properties = androidx.compose.ui.window.PopupProperties(focusable = false),
            ) {
                val colors = atharMenuColors()
                Box(
                    Modifier.width(with(density) { fieldSize.width.toDp() })
                        .atharChrome(colors.card, AtharCorners.Card, elevation = 6.dp, edge = AtharTheme.colors.divider)
                        .testTag("${tag}_results"),
                ) {
                    if (matches.isEmpty()) Text("لا نتائج", Modifier.padding(12.dp), color = colors.secondary, fontSize = AtharTextSize.Small)
                    else LazyColumn(Modifier.heightIn(max = 250.dp)) {
                        items(matches.size, key = { matches[it] }) { i ->
                            val name = matches[i]
                            AtharMenuItem(name, checked = name in selected, subtitle = subtitle(name), onClick = { onToggle(name); query = "" })
                        }
                    }
                }
            }
        }
    }
}

private const val SetSeparator = '\u001F'

/** A persisted multi-choice ([rememberPersisted] keeps strings). */
fun String.asChoiceSet(): Set<String> = if (isEmpty()) emptySet() else split(SetSeparator).toSet()
fun Set<String>.asStoredChoices(): String = joinToString(SetSeparator.toString())

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
                .then(
                    if (selected) Modifier.clip(AtharCorners.Control).background(AtharTheme.colors.primaryAction)
                    else Modifier.atharControl(AtharTheme.colors.divider.copy(alpha = 0.28f), AtharCorners.Control),
                )
                .padding(horizontal = 14.dp, vertical = 7.dp),
            color = if (selected) AtharTheme.colors.onPrimaryAction else AtharTheme.colors.secondaryText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

private val listStateGeneration = androidx.compose.runtime.mutableIntStateOf(0)

/** Every list's remembered sort and filters back to their defaults, on screens already open too. */
fun resetListState(context: android.content.Context) {
    context.getSharedPreferences("list_state", android.content.Context.MODE_PRIVATE).edit().clear().apply()
    listStateGeneration.intValue++
}

/** A String backed by SharedPreferences "list_state": sort/filter choices survive restarts. */
@Composable
fun rememberPersisted(key: String, default: String): MutableState<String> {
    val prefs = LocalContext.current.getSharedPreferences("list_state", android.content.Context.MODE_PRIVATE)
    return remember(key, listStateGeneration.intValue) { PersistedState(prefs, key, runCatching { prefs.getString(key, null) }.getOrNull() ?: default) }
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
    /** The list under the header, for «زجاج»: the header blurs what scrolls beneath it. */
    val haze = dev.chrisbanes.haze.HazeState()
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
            .atharChrome(AtharTheme.colors.canvas, haze = header.haze),
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
    // Flips on the tap itself: the stored state arrives after a database round trip and a
    // rebuild of the whole section list, which is a visible pause on a 1000-book library.
    var shown by remember(saved) { mutableStateOf(saved) }
    IconButton(
        onClick = { shown = !shown; onToggle() },
        modifier = modifier
            .size(48.dp)
            .testTag(tag),
    ) {
        Icon(
            // Filled when saved: the state should be legible without relying on colour.
            imageVector = if (shown) AtharIcons.StarFilled else AtharIcons.Star,
            contentDescription = if (shown) {
                "إزالة $itemTitle من المفضّلة"
            } else {
                "إضافة $itemTitle إلى المفضّلة"
            },
            tint = if (shown) {
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

private val normKeys = java.util.concurrent.ConcurrentHashMap<String, String>()

/**
 * [com.atharchive.core.ArNormalize.normalize] remembered per string, for catalog fields that list
 * screens filter and sort on: a list re-emits on every ★, download tick or reading position, and
 * renormalising a thousand titles each time was a visible pause.
 */
// ponytail: unbounded, but keys are catalog strings (a few thousand); swap for an LRU if free text lands here.
fun normKey(text: String): String = normKeys.getOrPut(text) { com.atharchive.core.ArNormalize.normalize(text) }

/** The main search's catalog engine, handed to every section list (see [rememberCatalogRanks]). */
val LocalCatalogSearch = androidx.compose.runtime.staticCompositionLocalOf<suspend (String, Set<String>) -> List<String>> { { _, _ -> emptyList() } }

/**
 * Each work in [collections] that the main search finds for [query], with its rank (0 = best);
 * null while the query is blank. The last result stays up while the next is computed, so typing
 * never flashes the whole list.
 */
@Composable
fun rememberCatalogRanks(query: String, vararg collections: String): Map<String, Int>? {
    val search = LocalCatalogSearch.current
    val term = query.trim()
    val scope = remember(*collections) { collections.toSet() }
    val ranks by androidx.compose.runtime.produceState<Map<String, Int>?>(null, term, scope) {
        if (term.isNotEmpty()) value = runCatching { search(term, scope) }
            .onFailure { if (it !is kotlinx.coroutines.CancellationException) android.util.Log.w("AtharSearch", "list search failed for $scope", it) }
            .getOrDefault(emptyList())
            .also { android.util.Log.i("AtharSearch", "list search $scope: ${it.size} matches") }
            .withIndex().associate { (i, id) -> id to i }
    }
    return if (term.isEmpty()) null else ranks
}

/**
 * The list searches' one rule: every word of [term] (normalized) is somewhere in [fields], in any
 * order, so «قصيدة أبي الأسود» finds «قصيدة ابن أبي الأسود» as the main search does.
 */
fun matchesWords(term: String, vararg fields: String): Boolean =
    term.split(' ').filter(String::isNotBlank).all { word -> fields.any { it.contains(word, ignoreCase = true) } }

/** «الاتجاه»: an arrow at the end of «الترتيب» (see [AtharDirectionButton]), the same in every section. */
fun AtharDirectionGroup(descending: Boolean, onChange: (Boolean) -> Unit) =
    AtharChipGroup("الاتجاه", listOf("تصاعدي", "تنازلي"), if (descending) "تنازلي" else "تصاعدي", isDirection = true) { onChange(it == "تنازلي") }

fun <T> Comparator<T>.directed(descending: Boolean): Comparator<T> = if (descending) reversed() else this

/**
 * An edge thumb for long lists (the library): it appears while the list moves, and dragging it
 * jumps straight to that point of the whole list. Hidden lists keep the edge for normal scrolling.
 */
@Composable
fun androidx.compose.foundation.layout.BoxScope.AtharFastScroller(listState: androidx.compose.foundation.lazy.LazyListState, top: Dp = 0.dp) {
    // Read through derivedStateOf / in the layout lambda below: layoutInfo changes every scroll
    // frame, and reading it here recomposed the scroller on each one (the library's stutter).
    val total by remember(listState) { androidx.compose.runtime.derivedStateOf { listState.layoutInfo.totalItemsCount } }
    if (total < 40) return
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    val shown = dragging || listState.isScrollInProgress
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (shown) 1f else 0f, tween(if (shown) 150 else 900), label = "fast_scroller")
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.align(Alignment.TopEnd).padding(top = top, bottom = 28.dp)
            .fillMaxHeight().width(28.dp)
            .graphicsLayer { this.alpha = alpha }
            .testTag("fast_scroller")
            .then(if (alpha == 0f) Modifier else Modifier.pointerInput(total) {
                fun jump(y: Float) { scope.launch { listState.scrollToItem(((y / size.height).coerceIn(0f, 1f) * (total - 1)).toInt()) } }
                detectVerticalDragGestures(
                    onDragStart = { dragging = true; jump(it.y) },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ -> change.consume(); jump(change.position.y) }
            }),
    ) {
        val thumb = 44.dp
        val travel = maxHeight - thumb
        Box(
            Modifier.align(Alignment.TopCenter)
                // the first row's scrolled share too, so the thumb glides instead of stepping a row at a time
                .offset {
                    val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                    val within = first?.let { listState.firstVisibleItemScrollOffset / it.size.coerceAtLeast(1).toFloat() } ?: 0f
                    val fraction = ((listState.firstVisibleItemIndex + within) / (total - 1)).coerceIn(0f, 1f)
                    androidx.compose.ui.unit.IntOffset(0, (travel.toPx() * fraction).toInt())
                }
                .size(width = if (dragging) 8.dp else 5.dp, height = thumb)
                .background(AtharTheme.colors.accent.copy(alpha = 0.75f), CircleShape),
        )
    }
}
