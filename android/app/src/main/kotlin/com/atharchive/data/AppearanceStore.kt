package com.atharchive.data

import android.content.Context
import com.atharchive.feature.reader.FootnoteMode
import com.atharchive.feature.reader.ReaderSettings
import com.atharchive.ui.theme.AtharAccent
import com.atharchive.ui.theme.AtharStyle
import com.atharchive.ui.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Appearance is a preference, not session state.
 *
 * The app's light/dark choice, the page palette, the font size and the line spacing all
 * outlive the process. They used to live in a `remember` inside the reader, which meant
 * setting 22sp on Tuesday and getting 19sp on Wednesday — and getting 19sp again the
 * moment the phone rotated.
 *
 * SharedPreferences rather than the user database: four scalars, read once at startup and
 * written on a tap. `ContentAccess` already keeps the cache budget this way.
 */
@Singleton
class AppearanceStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(readThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private val _style = MutableStateFlow(enumOrDefault(prefs.getString(KeyStyle, null), AtharStyle.Paper))
    /** «النمط»: how the app's surfaces are built. */
    val style: StateFlow<AtharStyle> = _style

    fun setStyle(style: AtharStyle) {
        _style.value = style
        prefs.edit().putString(KeyStyle, style.name).apply()
    }

    private val _accent = MutableStateFlow(enumOrDefault(prefs.getString(KeyAccent, null), AtharAccent.Burgundy))
    /** «اللون»: the accent across the app, and the launcher icon's colour. */
    val accent: StateFlow<AtharAccent> = _accent

    fun setAccent(accent: AtharAccent) {
        _accent.value = accent
        prefs.edit().putString(KeyAccent, accent.name).apply()
    }

    /**
     * Gives the launcher icon the chosen colour: one launcher alias per colour (AndroidManifest),
     * only the chosen one enabled. Disabling the alias that started the running task closes it,
     * so this runs only when the app leaves the screen. The launcher may drop a home-screen
     * shortcut of the old icon: that is the platform's price.
     */
    fun syncLauncherIcon() {
        val pm = context.packageManager
        AtharAccent.entries.forEach {
            val component = android.content.ComponentName(context, "com.atharchive.Launcher${it.name}")
            val on = it == _accent.value
            val now = pm.getComponentEnabledSetting(component)
            val enabled = now == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (now == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && it == AtharAccent.Burgundy)
            if (on != enabled) pm.setComponentEnabledSetting(
                component,
                if (on) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP,
            )
        }
    }

    private val _reader = MutableStateFlow(readReaderSettings())
    val reader: StateFlow<ReaderSettings> = _reader

    private val _startPlace = MutableStateFlow(
        enumOrDefault(prefs.getString(KeyStartPlace, null), StartPlace.Books),
    )
    val startPlace: StateFlow<StartPlace> = _startPlace

    fun setStartPlace(place: StartPlace) {
        _startPlace.value = place
        prefs.edit().putString(KeyStartPlace, place.name).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KeyThemeMode, mode.name).apply()
    }

    fun setReaderSettings(settings: ReaderSettings) {
        _reader.value = settings
        prefs.edit()
            .putInt(KeyFontSize, settings.fontSize)
            .putInt("reader_margin", settings.margin.coerceIn(12, 40))
            .putBoolean("reader_tashkeel", settings.showTashkeel)
            .putString("reader_footnote_mode", settings.footnoteMode.name)
            .putBoolean("reader_page_numbers", !settings.continuous) // the old «أرقام الصفحات» key: hidden numbers = متصل
            .putBoolean("poem_verse_numbers", settings.verseNumbers)
            .putBoolean("poem_prose", settings.poemProse)
            .putBoolean("poem_follow", settings.poemFollow)
            .putBoolean("reader_highlight_line", settings.underlineHighlights)
            .putString(KeySpacing, settings.spacing.name)
            .putString("text_hue_quran", settings.quranHue.name)
            .putString("text_hue_hadith", settings.hadithHue.name)
            .putString("text_hue_heading", settings.headingHue.name)
            .putString("text_hue_poetry", settings.poetryHue.name)
            .putString("reader_font", settings.font.name)
            .putLong("reader_font_stamp", settings.fontStamp)
            .apply()
    }

    private fun readThemeMode(): ThemeMode =
        enumOrDefault(prefs.getString(KeyThemeMode, null), ThemeMode.System)

    private fun readReaderSettings(): ReaderSettings {
        val defaults = ReaderSettings()
        return ReaderSettings(
            fontSize = prefs.getInt(KeyFontSize, defaults.fontSize)
                .coerceIn(ReaderSettings.MIN_SIZE, ReaderSettings.MAX_SIZE),
            spacing = enumOrDefault(prefs.getString(KeySpacing, null), defaults.spacing),
            margin = prefs.getInt("reader_margin", defaults.margin).coerceIn(12, 40),
            showTashkeel = prefs.getBoolean("reader_tashkeel", true),
            footnoteMode = footnoteModeOf(
                prefs.getString("reader_footnote_mode", null),
                if (prefs.contains("reader_footnotes")) prefs.getBoolean("reader_footnotes", true) else null,
            ),
            continuous = !prefs.getBoolean("reader_page_numbers", true),
            verseNumbers = prefs.getBoolean("poem_verse_numbers", false),
            poemProse = prefs.getBoolean("poem_prose", false),
            poemFollow = prefs.getBoolean("poem_follow", true),
            underlineHighlights = prefs.getBoolean("reader_highlight_line", false),
            quranHue = enumOrDefault(prefs.getString("text_hue_quran", null), defaults.quranHue),
            hadithHue = enumOrDefault(prefs.getString("text_hue_hadith", null), defaults.hadithHue),
            headingHue = enumOrDefault(prefs.getString("text_hue_heading", null), defaults.headingHue),
            poetryHue = enumOrDefault(prefs.getString("text_hue_poetry", null), defaults.poetryHue),
            font = enumOrDefault(prefs.getString("reader_font", null), defaults.font),
            fontStamp = prefs.getLong("reader_font_stamp", 0),
        )
    }

    /** A renamed or removed constant must fall back, never crash a cold start. */
    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, fallback: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    private companion object {
        const val Preferences = "athar_appearance"
        const val KeyThemeMode = "theme_mode"
        const val KeyStyle = "ui_style"
        const val KeyAccent = "ui_accent"
        const val KeyStartPlace = "start_place"
        const val KeyFontSize = "reader_font_size"
        const val KeySpacing = "reader_spacing"
    }
}

/** Where the app opens. [route] is the bottom-bar destination, null for the last-read work. */
enum class StartPlace(val label: String, val route: String?) {
    Books("الكتب", "books"),
    Poetry("الشعر", "poetry"),
    Search("البحث", "search"),
    Articles("المقالات", "articles"),
    Kannashah("الكناشة", "kannashah"),
    LastRead("آخر ما قرأت", null),
}

/** The stored name wins; the old "hidden" boolean maps to Tap (hidden no longer exists), anything else to Footer. */
internal fun footnoteModeOf(stored: String?, legacyShow: Boolean?): FootnoteMode =
    FootnoteMode.entries.firstOrNull { it.name == stored }
        ?: if (legacyShow == false) FootnoteMode.Tap else FootnoteMode.Footer
