package com.atharchive.ui.settings

import com.atharchive.ui.theme.AtharCorners
import com.atharchive.ui.theme.AtharTextSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import com.atharchive.feature.reader.SegmentRow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.feature.reader.ReaderSettings
import com.atharchive.feature.reader.ReaderSpacing
import com.atharchive.feature.reader.rememberReaderColors
import com.atharchive.ui.components.AtharSectionHeading
import com.atharchive.ui.components.AtharTopBar
import com.atharchive.ui.components.AtharMenu
import com.atharchive.ui.components.AtharMenuDivider
import com.atharchive.ui.components.AtharMenuItem
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme
import com.atharchive.ui.theme.ThemeMode
import com.atharchive.ui.navigation.AtharDestination
import com.atharchive.ui.navigation.AtharDestination.Companion.withSlot

/**
 * How the app looks. Font size, line spacing and the reading page live in the reader's «Aa»
 * panel only — one place per setting.
 */
@Composable
fun AppearanceScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    style: com.atharchive.ui.theme.AtharStyle = com.atharchive.ui.theme.AtharStyle.Paper,
    onStyleChange: (com.atharchive.ui.theme.AtharStyle) -> Unit = {},
    accent: com.atharchive.ui.theme.AccentChoice = com.atharchive.ui.theme.AccentChoice(),
    onAccentChange: (com.atharchive.ui.theme.AccentChoice) -> Unit = {},
    readerSettings: ReaderSettings = ReaderSettings(),
    onReaderSettingsChange: (ReaderSettings) -> Unit = {},
    startPlace: com.atharchive.data.StartPlace,
    onStartPlaceChange: (com.atharchive.data.StartPlace) -> Unit,
    bar: List<AtharDestination> = AtharDestination.DefaultBar,
    onBarChange: (List<AtharDestination>) -> Unit = {},
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
        val segmentColors = rememberReaderColors()

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
                val dark = com.atharchive.ui.theme.LocalAtharDarkTheme.current
                // One row per setting: its name, what it is now, and a small menu to change it.
                AtharSectionHeading("المظهر", pad, top = 10.dp)
                ChoiceRow("السمة", themeMode.label, pad, "appearance_theme") { close ->
                    ThemeMode.entries.forEach { AtharMenuItem(it.label, { onThemeModeChange(it); close() }, checked = it == themeMode) }
                }
                val styles = com.atharchive.ui.theme.AtharStyle.entries
                ChoiceRow("النمط", style.label, pad, "appearance_style") { close ->
                    styles.forEach { AtharMenuItem(it.label, { onStyleChange(it); close() }, checked = it == style, subtitle = it.hint) }
                }
                val accents = com.atharchive.ui.theme.AtharAccent.entries
                val tones = accent.tones
                ChoiceRow("اللون", accent.preset.label, pad, "appearance_accent", dot = if (dark) tones.night else tones.base) { close ->
                    accents.forEach { a ->
                        val t = if (a == com.atharchive.ui.theme.AtharAccent.Custom) tones.takeIf { accent.preset == a } else null
                        AtharMenuItem(a.label, {
                            onAccentChange(accent.copy(preset = a))
                            if (a != com.atharchive.ui.theme.AtharAccent.Custom) close()
                        }, checked = a == accent.preset, dot = t?.base ?: if (a == com.atharchive.ui.theme.AtharAccent.Custom) null else a.base)
                    }
                    if (accent.preset == com.atharchive.ui.theme.AtharAccent.Custom) CustomColorPicker(accent.custom, 12.dp, "appearance_accent_custom") {
                        onAccentChange(accent.copy(custom = it))
                    }
                }
                val customAccent = accent.preset == com.atharchive.ui.theme.AtharAccent.Custom
                ChoiceRow("أيقونة التطبيق", if (accent.originalIcon || customAccent) "الأصلية" else "بلون التطبيق", pad, "appearance_icon") { close ->
                    AtharMenuItem("الأصلية", { onAccentChange(accent.copy(originalIcon = true)); close() }, checked = accent.originalIcon || customAccent)
                    AtharMenuItem("بلون التطبيق", { if (!customAccent) { onAccentChange(accent.copy(originalIcon = false)); close() } },
                        checked = !accent.originalIcon && !customAccent,
                        subtitle = if (customAccent) "غير متاحة مع اللون المخصّص" else "تتغيّر عند الخروج من التطبيق")
                }

                SectionRule(pad)
                AtharSectionHeading("القراءة", pad, top = 18.dp)
                FontPicker(readerSettings, onReaderSettingsChange, pad)
                val hues = com.atharchive.feature.reader.TextHue.entries
                class HueLine(val label: String, val hue: com.atharchive.feature.reader.TextHue, val custom: Int,
                    val set: (com.atharchive.feature.reader.TextHue, Int) -> ReaderSettings)
                listOf(
                    HueLine("لون القرآن", readerSettings.quranHue, readerSettings.quranColor) { h, c -> readerSettings.copy(quranHue = h, quranColor = c) },
                    HueLine("لون الحديث والآثار", readerSettings.hadithHue, readerSettings.hadithColor) { h, c -> readerSettings.copy(hadithHue = h, hadithColor = c) },
                    HueLine("لون العناوين", readerSettings.headingHue, readerSettings.headingColor) { h, c -> readerSettings.copy(headingHue = h, headingColor = c) },
                    HueLine("لون الشعر", readerSettings.poetryHue, readerSettings.poetryColor) { h, c -> readerSettings.copy(poetryHue = h, poetryColor = c) },
                ).forEachIndexed { row, line ->
                    ChoiceRow(line.label, line.hue.label, pad, "appearance_hue_$row", dot = line.hue.resolve(segmentColors, dark, line.custom)) { close ->
                        hues.forEach { h ->
                            AtharMenuItem(h.label, {
                                onReaderSettingsChange(line.set(h, line.custom))
                                if (h != com.atharchive.feature.reader.TextHue.Custom) close()
                            }, checked = h == line.hue, dot = h.resolve(segmentColors, dark, line.custom))
                        }
                        if (line.hue == com.atharchive.feature.reader.TextHue.Custom) CustomColorPicker(line.custom, 12.dp, "appearance_custom_$row") {
                            onReaderSettingsChange(line.set(line.hue, it))
                        }
                    }
                }

                SectionRule(pad)
                AtharSectionHeading("عام", pad, top = 18.dp)
                val places = com.atharchive.data.StartPlace.entries
                ChoiceRow("يفتح التطبيق على", startPlace.label, pad, "appearance_start") { close ->
                    places.forEach { AtharMenuItem(it.label, { onStartPlaceChange(it); close() }, checked = it == startPlace) }
                }

                SectionRule(pad)
                AtharSectionHeading("الشريط السفلي", pad, top = 18.dp)
                // Five tabs; what has none stays in ⋮. الكتب holds the first: back always leads to it.
                bar.forEachIndexed { slot, tab ->
                    if (slot == 0) ChoiceRow("الأول", tab.label, pad, "appearance_bar_0") { close ->
                        AtharMenuItem(tab.label, close, checked = true, subtitle = "ثابت: إليه يعود زر الرجوع")
                    } else ChoiceRow(BarSlotNames[slot], tab.label, pad, "appearance_bar_$slot") { close ->
                        AtharDestination.entries.filter { it != AtharDestination.Books }.forEach { choice ->
                            AtharMenuItem(choice.label, { onBarChange(bar.withSlot(slot, choice)); close() }, icon = choice.icon, checked = choice == tab)
                        }
                    }
                }
                SectionRule(pad)
                val analyticsOn by com.atharchive.data.Analytics.enabled.collectAsState()
                ChoiceRow("إحصاءات مجهولة", if (analyticsOn) "تشغيل" else "إيقاف", pad, "appearance_analytics") { close ->
                    AtharMenuItem("تشغيل", { com.atharchive.data.Analytics.setEnabled(true); close() }, checked = analyticsOn,
                        subtitle = "عدد مرات الفتح والقراءة والبحث، بلا اسم ولا كلمات بحث")
                    AtharMenuItem("إيقاف", { com.atharchive.data.Analytics.setEnabled(false); close() }, checked = !analyticsOn)
                }

            }
        }
    }
}

/** The reading face, and «خطّي»: a .ttf/.otf the reader brings, checked before it is kept. */
@Composable
private fun FontPicker(settings: ReaderSettings, onChange: (ReaderSettings) -> Unit, pad: Dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var note by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val fonts = com.atharchive.ui.theme.ReaderFont.reading
    val custom = com.atharchive.ui.theme.ReaderFont.Custom
    // Which line asked for the import: -1 «خطّ القراءة», else the role row; it gets «خطّي» once the file is in.
    var importFor by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(-1) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            note = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { importFont(context, uri) }
            val stamped = settings.copy(fontStamp = System.currentTimeMillis())
            if (note == null) onChange(when (importFor) {
                0 -> stamped.copy(quranFont = custom)
                1 -> stamped.copy(poetryFont = custom)
                else -> stamped.copy(font = custom)
            })
        }
    }
    val types = arrayOf("font/*", "application/octet-stream", "application/x-font-ttf", "application/vnd.ms-opentype")
    val hasCustom = androidx.compose.runtime.remember(settings.fontStamp) { com.atharchive.ui.theme.ReaderFont.userFile(context).isFile }
    ChoiceRow("خطّ القراءة", settings.font.label, pad, "appearance_font") { close ->
        fonts.forEach { font ->
            AtharMenuItem(font.label, {
                close()
                if (font == custom && !hasCustom) { importFor = -1; pick.launch(types) } else onChange(settings.copy(font = font))
            }, checked = font == settings.font, subtitle = if (font == custom) "ملف ‎.ttf‎ أو ‎.otf‎ من جهازك" else null)
        }
        AtharMenuDivider()
        AtharMenuItem(if (hasCustom) "استبدال خطّي" else "استيراد خط", { close(); importFor = -1; pick.launch(types) }, modifier = Modifier.testTag("appearance_font_import"))
    }
    // «خطوط النص»: one face per role, like «ألوان النص» below; «خطّي» imports first when there is none yet.
    class RoleLine(val label: String, val font: com.atharchive.ui.theme.ReaderFont, val choices: List<com.atharchive.ui.theme.ReaderFont>,
        val set: (com.atharchive.ui.theme.ReaderFont) -> ReaderSettings)
    val roles = com.atharchive.ui.theme.ReaderFont.roles
    listOf(
        RoleLine("خط القرآن", settings.quranFont, com.atharchive.ui.theme.ReaderFont.quranRoles) { settings.copy(quranFont = it) },
        RoleLine("خط الشعر", settings.poetryFont, roles) { settings.copy(poetryFont = it) },
    ).forEachIndexed { row, line ->
        ChoiceRow(line.label, line.font.label, pad, "appearance_role_font_$row") { close ->
            line.choices.forEach { font ->
                val elgharib = font == com.atharchive.ui.theme.ReaderFont.NoonHafs || font == com.atharchive.ui.theme.ReaderFont.Tharwat
                AtharMenuItem(font.label, {
                    close()
                    if (font == custom && !hasCustom) { importFor = row; pick.launch(types) } else onChange(line.set(font))
                }, checked = font == line.font, subtitle = if (elgharib) "خطوط أحمد الغريب" else null)
            }
        }
    }
    note?.let {
        Text(it, Modifier.padding(horizontal = pad, vertical = 4.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

/** One setting: its name at the start, its value (and colour) at the end; a tap opens the menu to change it. */
private val BarSlotNames = listOf("الأول", "الثاني", "الثالث", "الرابع", "الخامس")

@Composable
private fun ChoiceRow(
    label: String,
    value: String,
    pad: Dp,
    tag: String,
    dot: androidx.compose.ui.graphics.Color? = null,
    menu: @Composable androidx.compose.foundation.layout.ColumnScope.(close: () -> Unit) -> Unit,
) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).clickable(role = Role.Button) { open = true }
            .padding(horizontal = pad).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyLarge)
        if (dot != null) Box(Modifier.padding(end = 8.dp).size(14.dp).clip(CircleShape).background(dot))
        Box {
            Text(value, color = AtharTheme.colors.secondaryText, style = MaterialTheme.typography.bodyMedium)
            AtharMenu(expanded = open, onDismiss = { open = false }, width = 260.dp) { menu { open = false } }
        }
    }
}

/**
 * Copies the chosen font into app storage, after checking it is a font Android can load and not
 * absurdly large. Returns the reason it was refused, or null once it is in place.
 */
private fun importFont(context: android.content.Context, uri: android.net.Uri): String? {
    val target = com.atharchive.ui.theme.ReaderFont.userFile(context)
    val temp = java.io.File(target.parentFile, "incoming.tmp")
    return try {
        target.parentFile?.mkdirs()
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MaxFontBytes) return "الملف أكبر من ٣٠ م.ب؛ اختر ملف خطّ أصغر."
                    out.write(buffer, 0, n)
                }
                total
            }
        } ?: return "تعذّر فتح الملف."
        if (copied == 0L) return "الملف فارغ."
        val face = runCatching { android.graphics.Typeface.Builder(temp).build() }.getOrNull()
        if (face == null || face == android.graphics.Typeface.DEFAULT) return "هذا الملف ليس خطًّا يمكن قراءته."
        if (!temp.renameTo(target)) return "تعذّر حفظ الخط."
        null
    } catch (e: java.io.IOException) {
        "تعذّر نسخ الخط."
    } finally {
        temp.delete()
    }
}

private const val MaxFontBytes = 30L * 1024 * 1024

@Composable
private fun SectionRule(horizontalPadding: Dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = horizontalPadding, end = horizontalPadding, top = 12.dp),
        thickness = 0.7.dp,
        color = AtharTheme.colors.divider.copy(alpha = 0.42f),
    )
}

/** Swatches to tap, or any colour typed as hex («#A1352F»), for a «مخصّص» text colour. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CustomColorPicker(current: Int, pad: androidx.compose.ui.unit.Dp, tag: String, onPick: (Int) -> Unit) {
    val swatches = listOf(0xFFA1352F, 0xFFC0392B, 0xFFB5651D, 0xFF8E6A1C, 0xFF6B8E23, 0xFF2E6B45, 0xFF1F7A7A,
        0xFF2A4C8C, 0xFF4B3F8F, 0xFF7A3E7A, 0xFF5A4636, 0xFF555555).map { it.toInt() }
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 4.dp).testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        swatches.forEach { argb ->
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(argb))
                    .then(if (argb == current) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onBackground, CircleShape) else Modifier)
                    .clickable(role = Role.RadioButton) { onPick(argb) }
                    .semantics { selected = argb == current },
            )
        }
    }
    var hex by androidx.compose.runtime.saveable.rememberSaveable(current) {
        androidx.compose.runtime.mutableStateOf(if (current == 0) "" else "#%06X".format(current and 0xFFFFFF))
    }
    androidx.compose.material3.OutlinedTextField(
        value = hex,
        onValueChange = { typed ->
            hex = typed.take(7)
            // Six hex digits make a colour; anything else waits for more typing.
            typed.removePrefix("#").takeIf { it.length == 6 }?.toIntOrNull(16)?.let { onPick(it or 0xFF000000.toInt()) }
        },
        modifier = Modifier.padding(horizontal = pad).padding(bottom = 6.dp).fillMaxWidth().testTag("${tag}_hex"),
        label = { Text("لون من اختيارك (#RRGGBB)") },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
    )
}
