package com.atharchive

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atharchive.data.AppearanceStore
import com.atharchive.ui.AtharApp
import com.atharchive.ui.theme.AtharTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Read before the first frame so a dark-mode reader never sees a light one.
    @Inject lateinit var appearance: AppearanceStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by appearance.themeMode.collectAsStateWithLifecycle()
            val readerSettings by appearance.reader.collectAsStateWithLifecycle()
            AtharTheme(darkTheme = themeMode.resolve()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AtharApp(
                        themeMode = themeMode,
                        onThemeModeChange = appearance::setThemeMode,
                        readerSettings = readerSettings,
                        onReaderSettingsChange = appearance::setReaderSettings,
                    )
                }
            }
        }
    }
}
