package com.atharchive

import com.atharchive.ui.theme.family
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.setValue
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

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) appearance.syncLauncherIcon()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        downloadTarget.value = intent.getStringExtra("athar_download")
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val turn = com.atharchive.feature.reader.VolumePaging.turn
        val code = event.keyCode
        if (turn == null || (code != android.view.KeyEvent.KEYCODE_VOLUME_DOWN && code != android.view.KeyEvent.KEYCODE_VOLUME_UP)) return super.dispatchKeyEvent(event)
        if (event.action == android.view.KeyEvent.ACTION_DOWN) turn(code == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) com.atharchive.data.Analytics.hit("open")
        enableEdgeToEdge()
        downloadTarget.value = intent.getStringExtra("athar_download")
        // Only a fresh start from the launcher, and never with system animations off.
        val intro = savedInstanceState == null && intent.action == android.content.Intent.ACTION_MAIN && downloadTarget.value == null &&
            android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        setContent {
            var showIntro by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(intro) }
            val target by downloadTarget.collectAsStateWithLifecycle()
            val themeMode by appearance.themeMode.collectAsStateWithLifecycle()
            val style by appearance.style.collectAsStateWithLifecycle()
            val accent by appearance.accent.collectAsStateWithLifecycle()
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
            AtharTheme(darkTheme = dark, warm = themeMode == com.atharchive.ui.theme.ThemeMode.Warm, eink = themeMode == com.atharchive.ui.theme.ThemeMode.Eink, style = style, choice = accent) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                    com.atharchive.ui.theme.LocalReaderFont provides readerSettings.font.family(readerSettings.fontStamp),
                ) {
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
                        style = style,
                        onStyleChange = appearance::setStyle,
                        accent = accent,
                        onAccentChange = appearance::setAccent,
                        startPlace = startPlace,
                        onStartPlaceChange = appearance::setStartPlace,
                        readerSettings = readerSettings,
                        onReaderSettingsChange = appearance::setReaderSettings,
                    )
                    if (showIntro) com.atharchive.ui.icons.AtharLaunchIntro { showIntro = false }
                    }
                }
            }
        }
    }
}
