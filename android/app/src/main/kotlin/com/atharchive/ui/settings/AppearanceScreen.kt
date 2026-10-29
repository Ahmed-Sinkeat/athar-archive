package com.atharchive.ui.settings

import com.atharchive.ui.theme.AtharCorners
import com.atharchive.ui.theme.AtharTextSize
import androidx.compose.foundation.background
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
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme
import com.atharchive.ui.theme.ThemeMode

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
    accent: com.atharchive.ui.theme.AtharAccent = com.atharchive.ui.theme.AtharAccent.Burgundy,
    onAccentChange: (com.atharchive.ui.theme.AtharAccent) -> Unit = {},
    readerSettings: ReaderSettings = ReaderSettings(),
    onReaderSettingsChange: (ReaderSettings) -> Unit = {},
    startPlace: com.atharchive.data.StartPlace,
    onStartPlaceChange: (com.atharchive.data.StartPlace) -> Unit,
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
        val segmentPad = Modifier.padding(horizontal = pad, vertical = 6.dp)

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
                AtharSectionHeading("سمة التطبيق", pad, top = 10.dp)
                Box(segmentPad) {
                    SegmentRow(ThemeMode.entries.map { it.label }, themeMode.label, segmentColors, "appearance_theme") { label ->
                        onThemeModeChange(ThemeMode.entries.first { it.label == label })
                    }
                }

                SectionRule(pad)
                AtharSectionHeading("اللون", pad, top = 18.dp)
                val accents = com.atharchive.ui.theme.AtharAccent.entries
                val dark = com.atharchive.ui.theme.LocalAtharDarkTheme.current
                Box(segmentPad) {
                    SegmentRow(accents.map { it.label }, accent.label, segmentColors, "appearance_accent",
                        dots = accents.map { if (dark) it.night else it.base }) { label ->
                        onAccentChange(accents.first { it.label == label })
                    }
                }
                Text(
                    "لون التحديد والأزرار والعلامات، ولون أيقونة التطبيق.",
                    Modifier.padding(horizontal = pad).padding(top = 2.dp, bottom = 4.dp),
                    color = AtharTheme.colors.secondaryText,
                    style = MaterialTheme.typography.bodySmall,
                )

                SectionRule(pad)
                AtharSectionHeading("النمط", pad, top = 18.dp)
                val styles = com.atharchive.ui.theme.AtharStyle.entries
                Box(segmentPad) {
                    SegmentRow(styles.map { it.label }, style.label, segmentColors, "appearance_style") { label ->
                        onStyleChange(styles.first { it.label == label })
                    }
                }
                Text(
                    style.hint,
                    Modifier.padding(horizontal = pad).padding(top = 2.dp, bottom = 4.dp),
                    color = AtharTheme.colors.secondaryText,
                    style = MaterialTheme.typography.bodySmall,
                )

                SectionRule(pad)
                AtharSectionHeading("ألوان النص", pad, top = 18.dp)
                val hues = com.atharchive.feature.reader.TextHue.entries
                val hueDots = hues.map { it.resolve(segmentColors, dark) }
                listOf<Triple<String, com.atharchive.feature.reader.TextHue, (com.atharchive.feature.reader.TextHue) -> ReaderSettings>>(
                    Triple("القرآن", readerSettings.quranHue) { readerSettings.copy(quranHue = it) },
                    Triple("الحديث والآثار", readerSettings.hadithHue) { readerSettings.copy(hadithHue = it) },
                    Triple("العناوين", readerSettings.headingHue) { readerSettings.copy(headingHue = it) },
                    Triple("الشعر", readerSettings.poetryHue) { readerSettings.copy(poetryHue = it) },
                ).forEachIndexed { row, (label, hue, set) ->
                    Text(label, Modifier.padding(horizontal = pad).padding(top = 6.dp), color = AtharTheme.colors.secondaryText, style = MaterialTheme.typography.labelMedium)
                    Box(Modifier.padding(horizontal = pad, vertical = 3.dp)) {
                        SegmentRow(hues.map { it.label }, hue.label, segmentColors, "appearance_hue_$row", dots = hueDots) { picked ->
                            onReaderSettingsChange(set(hues.first { it.label == picked }))
                        }
                    }
                }

                SectionRule(pad)
                AtharSectionHeading("خطّ القراءة", pad, top = 18.dp)
                FontPicker(readerSettings, onReaderSettingsChange, segmentColors, segmentPad, pad)

                SectionRule(pad)
                AtharSectionHeading("يفتح التطبيق على", pad, top = 18.dp)
                val places = com.atharchive.data.StartPlace.entries
                places.chunked(3).forEachIndexed { row, group ->
                    Box(segmentPad) {
                        SegmentRow(group.map { it.label }, startPlace.label, segmentColors, "appearance_start_$row") { label ->
                            onStartPlaceChange(places.first { it.label == label })
                        }
                    }
                }

                SectionRule(pad)
                AtharSectionHeading("إحصاءات مجهولة", pad, top = 18.dp)
                val analyticsOn by com.atharchive.data.Analytics.enabled.collectAsState()
                Box(segmentPad) {
                    SegmentRow(listOf("تشغيل", "إيقاف"), if (analyticsOn) "تشغيل" else "إيقاف", segmentColors, "appearance_analytics") { label ->
                        com.atharchive.data.Analytics.setEnabled(label == "تشغيل")
                    }
                }
                Text(
                    "عدد مرات الفتح والقراءة والبحث، بلا اسم ولا معرّف ولا كلمات بحث.",
                    modifier = Modifier.padding(horizontal = pad, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = AtharTheme.colors.secondaryText,
                )
            }
        }
    }
}

/** The reading face, and «خطّي»: a .ttf/.otf the reader brings, checked before it is kept. */
@Composable
private fun FontPicker(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
    colors: com.atharchive.feature.reader.ReaderColors,
    segmentPad: Modifier,
    pad: Dp,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var note by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val fonts = com.atharchive.ui.theme.ReaderFont.entries
    val custom = com.atharchive.ui.theme.ReaderFont.Custom
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            note = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { importFont(context, uri) }
            if (note == null) onChange(settings.copy(font = custom, fontStamp = System.currentTimeMillis()))
        }
    }
    val hasCustom = androidx.compose.runtime.remember(settings.fontStamp) { com.atharchive.ui.theme.ReaderFont.userFile(context).isFile }
    Box(segmentPad) {
        SegmentRow(fonts.map { it.label }, settings.font.label, colors, "appearance_font") { label ->
            val font = fonts.first { it.label == label }
            if (font == custom && !hasCustom) pick.launch(arrayOf("font/*", "application/octet-stream", "application/x-font-ttf", "application/vnd.ms-opentype"))
            else onChange(settings.copy(font = font))
        }
    }
    Row(Modifier.padding(horizontal = pad), verticalAlignment = Alignment.CenterVertically) {
        Text(
            note ?: "يغيّر خطّ نصّ الكتب والشعر. «خطّي» ملف خطّ ‎.ttf‎ أو ‎.otf‎ من جهازك.",
            Modifier.weight(1f).padding(vertical = 4.dp),
            color = if (note != null) MaterialTheme.colorScheme.error else AtharTheme.colors.secondaryText,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            if (hasCustom) "استبدال خطّي" else "استيراد خط",
            Modifier.clip(AtharCorners.Control).clickable(role = Role.Button) {
                pick.launch(arrayOf("font/*", "application/octet-stream", "application/x-font-ttf", "application/vnd.ms-opentype"))
            }.padding(horizontal = 8.dp, vertical = 6.dp).testTag("appearance_font_import"),
            color = AtharTheme.colors.accent,
            style = MaterialTheme.typography.labelLarge,
        )
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
