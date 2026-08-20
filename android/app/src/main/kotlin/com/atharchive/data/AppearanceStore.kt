package com.atharchive.data

import android.content.Context
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

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KeyThemeMode, mode.name).apply()
    }

    fun setReaderSettings(settings: ReaderSettings) {
        _reader.value = settings
        prefs.edit()
            .putInt(KeyFontSize, settings.fontSize)
            .putString(KeySpacing, settings.spacing.name)
            .putString(KeyPalette, settings.palette.name)
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
            palette = enumOrDefault(prefs.getString(KeyPalette, null), defaults.palette),
        )
    }

    /** A renamed or removed constant must fall back, never crash a cold start. */
    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, fallback: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    private companion object {
        const val Preferences = "athar_appearance"
        const val KeyThemeMode = "theme_mode"
        const val KeyFontSize = "reader_font_size"
        const val KeySpacing = "reader_spacing"
        const val KeyPalette = "reader_palette"
    }
}
