package com.atharchive.ui.components

import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.CompletableDeferred

internal class AtharTextMenuState : TextContextMenuProvider {
    private val mutex = MutatorMutex()
    var session by mutableStateOf<Session?>(null)
        private set

    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        mutex.mutate {
            val current = Session(dataProvider)
            session = current
            try { current.closed.await() }
            finally { if (session === current) session = null }
        }
    }

    class Session(val provider: TextContextMenuDataProvider) : TextContextMenuSession {
        val closed = CompletableDeferred<Unit>()
        override fun close() { closed.complete(Unit) }
    }
}

/** Compose's current text-menu providers cover touch selection and mouse context menus. */
@Composable
fun ProvideAtharTextToolbar(content: @Composable () -> Unit) {
    val state = remember { AtharTextMenuState() }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    DisposableEffect(state) { onDispose { state.session?.close() } }
    CompositionLocalProvider(
        LocalTextContextMenuToolbarProvider provides state,
        LocalTextContextMenuDropdownProvider provides state,
    ) {
        Box(Modifier.onGloballyPositioned { coordinates = it }, propagateMinConstraints = true) {
            content()
            val session = state.session
            val layout = coordinates
            if (session != null && layout?.isAttached == true) {
                val items = session.provider.data().components.filterIsInstance<TextContextMenuItem>()
                if (items.isEmpty()) LaunchedEffect(session) { session.close() }
                else AtharMenu(
                    expanded = true,
                    anchor = session.provider.contentBounds(layout).translate(layout.positionOnScreen()),
                    focusable = false,
                    onDismiss = session::close,
                    modifier = Modifier.testTag("athar_text_toolbar"),
                ) {
                    items.forEach { item ->
                        val (label, tag) = when (item.key) {
                            TextContextMenuKeys.CutKey -> "قص" to "text_cut"
                            TextContextMenuKeys.CopyKey -> "نسخ" to "text_copy"
                            TextContextMenuKeys.PasteKey -> "لصق" to "text_paste"
                            TextContextMenuKeys.SelectAllKey -> "تحديد الكل" to "text_select_all"
                            TextContextMenuKeys.AutofillKey -> "الملء التلقائي" to "text_autofill"
                            else -> item.label to "text_action"
                        }
                        AtharMenuItem(label, onClick = { item.onClick(session); session.close() }, modifier = Modifier.testTag(tag))
                    }
                }
            }
        }
    }
}
