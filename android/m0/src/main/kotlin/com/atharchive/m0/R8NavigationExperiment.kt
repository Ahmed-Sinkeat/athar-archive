package com.atharchive.m0

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.Serializable

@Serializable
private data object R8Library : NavKey

@Serializable
private data class R8Reader(val bookId: String) : NavKey

/**
 * R8 is intentionally a visible, manual spike rather than production navigation.
 * The instance number proves ViewModel ownership; position proves SavedStateHandle
 * restoration after the process is killed and recreated.
 */
@Composable
internal fun R8Screen() {
    val backStack = rememberNavBackStack(R8Library)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<R8Library> {
                R8LibraryPane(
                    onOpen = { bookId -> backStack.add(R8Reader(bookId)) },
                )
            }
            entry<R8Reader> { route ->
                R8ReaderDestination(
                    route = route,
                    onOpenAnother = { backStack.add(R8Reader("book-${backStack.size}")) },
                    onOpenBook = { bookId -> backStack.add(R8Reader(bookId)) },
                )
            }
        },
    )
}

@Composable
private fun R8LibraryPane(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("R8 — Navigation 3", style = MaterialTheme.typography.titleLarge)
        Text(
            "Open a reader, increase its position, then open another reader and go back. " +
                "Each back-stack entry must retain its own instance and position.",
        )
        Button(onClick = { onOpen("book-1") }) { Text("Open book-1") }
        Button(onClick = { onOpen("book-2") }) { Text("Open book-2") }
    }
}

@Composable
private fun R8ReaderDestination(
    route: R8Reader,
    onOpenAnother: () -> Unit,
    onOpenBook: (String) -> Unit,
) {
    val readerViewModel = viewModel<R8ReaderViewModel>()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableWidth = maxWidth
        if (availableWidth >= 600.dp) {
            Row(Modifier.fillMaxSize()) {
                R8LibraryPane(
                    onOpen = onOpenBook,
                    modifier = Modifier.weight(0.38f),
                )
                R8ReaderPane(
                    route = route,
                    state = readerViewModel,
                    onOpenAnother = onOpenAnother,
                    layout = "two-pane (${availableWidth.value.toInt()}dp)",
                    modifier = Modifier.weight(0.62f),
                )
            }
        } else {
            R8ReaderPane(
                route = route,
                state = readerViewModel,
                onOpenAnother = onOpenAnother,
                layout = "single-pane (${availableWidth.value.toInt()}dp)",
            )
        }
    }
}

@Composable
private fun R8ReaderPane(
    route: R8Reader,
    state: R8ReaderViewModel,
    onOpenAnother: () -> Unit,
    layout: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Reader · ${route.bookId}", style = MaterialTheme.typography.titleLarge)
        Text("Layout: $layout")
        HorizontalDivider(Modifier.fillMaxWidth())
        Text("ViewModel instance: ${state.instanceId}")
        Text("Saved position: ${state.position}")
        Button(onClick = state::advance) { Text("Advance +25") }
        Button(onClick = onOpenAnother) { Text("Push another reader") }
        Text(
            "Process-death check: change position, background the app, run " +
                "adb shell am kill com.atharchive.m0, then relaunch. Route and position must return.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

internal class R8ReaderViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    val instanceId: Int = nextInstance.incrementAndGet()

    var position: Int
        get() = savedStateHandle[POSITION] ?: 0
        private set(value) {
            savedStateHandle[POSITION] = value
        }

    fun advance() {
        position += 25
    }

    private companion object {
        const val POSITION = "reader-position"
        val nextInstance = AtomicInteger(0)
    }
}
