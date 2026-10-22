package com.atharchive.ui.components

import androidx.compose.foundation.text.contextmenu.data.TextContextMenuData
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AtharTextToolbarTest {
    @Test fun dismissalAndCancellationClearTheActiveMenu() = runBlocking {
        val state = AtharTextMenuState()
        val provider = object : TextContextMenuDataProvider {
            override fun position(destinationCoordinates: LayoutCoordinates) = Offset.Zero
            override fun contentBounds(destinationCoordinates: LayoutCoordinates) = Rect.Zero
            override fun data() = TextContextMenuData.Empty
        }
        val dismissed = launch(start = CoroutineStart.UNDISPATCHED) { state.showTextContextMenu(provider) }
        assertNotNull(state.session)
        state.session!!.close()
        dismissed.join()
        assertNull(state.session)
        val cancelled = launch(start = CoroutineStart.UNDISPATCHED) { state.showTextContextMenu(provider) }
        assertNotNull(state.session)
        cancelled.cancel()
        cancelled.join()
        assertNull(state.session)
    }
}
