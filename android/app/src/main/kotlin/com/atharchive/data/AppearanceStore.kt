package com.atharchive.data

import android.content.Context
import com.atharchive.feature.reader.FootnoteMode
import com.atharchive.feature.reader.ReaderSettings
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
class AppearanceStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(readThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode

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
            .putString(KeySpacing, settings.spacing.name)
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
        )
    }

    /** A renamed or removed constant must fall back, never crash a cold start. */
    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, fallback: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    private companion object {
        const val Preferences = "athar_appearance"
        const val KeyThemeMode = "theme_mode"
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
