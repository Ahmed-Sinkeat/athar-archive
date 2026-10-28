package com.atharchive

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atharchive.data.AppearanceStore
import com.atharchive.ui.AtharApp
import com.atharchive.ui.theme.AtharTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@OptIn(ExperimentalComposeUiApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Read before the first frame so a dark-mode reader never sees a light one.
    @Inject lateinit var appearance: AppearanceStore

    private val downloadTarget = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        downloadTarget.value = intent.getStringExtra("athar_download")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) com.atharchive.data.Analytics.hit("open")
        enableEdgeToEdge()
        downloadTarget.value = intent.getStringExtra("athar_download")
        setContent {
            val target by downloadTarget.collectAsStateWithLifecycle()
            val themeMode by appearance.themeMode.collectAsStateWithLifecycle()
            val readerSettings by appearance.reader.collectAsStateWithLifecycle()
            val startPlace by appearance.startPlace.collectAsStateWithLifecycle()
            val dark = themeMode.resolve()
            // Bar icons follow the app's theme, not the system's: a forced-light app under a
            // dark system used to get white icons on a white status bar.
            androidx.compose.runtime.DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = androidx.activity.SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
                    ) { dark },
                    navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                        android.graphics.Color.argb(0xe6, 0xff, 0xff, 0xff), android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b),
                    ) { dark },
                )
                onDispose {}
            }
            AtharTheme(darkTheme = dark, warm = themeMode == com.atharchive.ui.theme.ThemeMode.Warm) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    // Macrobenchmarks drive the app through UiAutomator, which can only
                    // see resource-ids. This publishes every testTag as one; it changes
                    // nothing a user sees and costs one semantics flag.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .semantics { testTagsAsResourceId = true },
                    ) {
                    AtharApp(
                        downloadTarget = target,
                        onDownloadTargetHandled = { downloadTarget.value = null },
                        themeMode = themeMode,
                        onThemeModeChange = appearance::setThemeMode,
                        startPlace = startPlace,
                        onStartPlaceChange = appearance::setStartPlace,
                        readerSettings = readerSettings,
                        onReaderSettingsChange = appearance::setReaderSettings,
                    )
                    }
                }
            }
        }
    }
}
