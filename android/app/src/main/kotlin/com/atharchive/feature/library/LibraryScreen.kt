package com.atharchive.feature.library

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.ui.components.AtharEmptyState
import com.atharchive.ui.components.AtharMenuButton
import com.atharchive.ui.components.AtharProgressBar
import com.atharchive.ui.components.AtharTopBar
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme

private const val NoStatus = "بلا حالة"

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onBack: () -> Unit,
    onOpenWork: (LibraryWorkUi) -> Unit,
    onStatusChange: (LibraryWorkUi, LibraryShelf?) -> Unit,
    onCreateCollection: (String) -> Unit,
    onDeleteCollection: (LibraryCollectionUi) -> Unit,
    onAddToCollection: (LibraryWorkUi, LibraryCollectionUi) -> Unit,
    onRemoveFromCollection: (LibraryWorkUi, LibraryCollectionUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    var shelf by rememberSaveable { mutableStateOf(LibraryShelf.Continue) }
    var collectionId by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingCollection by rememberSaveable { mutableStateOf(false) }
    val collection = state.collections.firstOrNull { it.id == collectionId }
    val visible = remember(state, shelf, collectionId) { state.worksFor(shelf, collectionId) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(AtharTheme.colors.canvas)
            .testTag("library_screen"),
    ) {
        val horizontalPadding = if (maxWidth >= 720.dp) 32.dp else 20.dp
        Column(Modifier.fillMaxSize()) {
            AtharTopBar(
                title = "مكتبتي",
                onSettings = {},
                showAppIcon = true,
                onBack = onBack,
                actionIcon = AtharIcons.PlusCircle,
                actionLabel = "إنشاء مجموعة",
                onAction = { creatingCollection = true },
                horizontalPadding = horizontalPadding,
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                item("library-controls") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding - 4.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AtharMenuButton(
                            label = "الرف",
                            options = LibraryShelf.entries.map { it.label },
                            selected = if (collection == null) shelf.label else "الرف",
                            defaultOption = "الرف",
                            onSelect = { label ->
                                shelf = LibraryShelf.entries.first { it.label == label }
                                collectionId = null
                            },
                            tagPrefix = "library_shelf",
                        )
                        if (state.collections.isNotEmpty()) {
                            AtharMenuButton(
                                label = "المجموعة",
                                options = state.collections.map { it.title },
                                selected = collection?.title ?: "المجموعة",
                                defaultOption = "المجموعة",
                                onSelect = { title ->
                                    collectionId = state.collections.first { it.title == title }.id
                                },
                                tagPrefix = "library_collection",
                            )
                        }
                    }
                }
                item("library-heading") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = collection?.title ?: shelf.label,
                            modifier = Modifier.weight(1f),
                            fontFamily = AtharEditorialFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                        Text(
                            text = arabicDigits(visible.size),
                            color = AtharTheme.colors.secondaryText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (collection != null) {
                            TextButton(
                                onClick = {
                                    onDeleteCollection(collection)
                                    collectionId = null
                                },
                            ) { Text("حذف") }
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                        color = AtharTheme.colors.divider.copy(alpha = 0.5f),
                    )
                }
                if (visible.isEmpty()) {
                    item("library-empty") {
                        AtharEmptyState(
                            title = "هذا الرف فارغ",
                            body = if (collection == null) {
                                "غيّر حالة عمل من مكتبته ليظهر هنا."
                            } else {
                                "أضف عملًا إلى هذه المجموعة من أحد الرفوف."
                            },
                            tag = "library_empty",
                            horizontalPadding = horizontalPadding,
                        )
                    }
                } else {
                    itemsIndexed(visible, key = { _, work -> work.id }) { index, work ->
                        LibraryWorkRow(
                            work = work,
                            collections = state.collections,
                            selectedCollection = collection,
                            horizontalPadding = horizontalPadding,
                            onOpen = { onOpenWork(work) },
                            onStatus = { onStatusChange(work, it) },
                            onAddToCollection = { onAddToCollection(work, it) },
                            onRemoveFromCollection = { selected ->
                                onRemoveFromCollection(work, selected)
                            },
                        )
                        if (index != visible.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = horizontalPadding),
                                color = AtharTheme.colors.divider.copy(alpha = 0.42f),
                            )
                        }
                    }
                }
            }
        }
    }

    if (creatingCollection) {
        CreateCollectionDialog(
            onDismiss = { creatingCollection = false },
            onCreate = {
                onCreateCollection(it)
                creatingCollection = false
            },
        )
    }
}

@Composable
private fun LibraryWorkRow(
    work: LibraryWorkUi,
    collections: List<LibraryCollectionUi>,
    selectedCollection: LibraryCollectionUi?,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onOpen: () -> Unit,
    onStatus: (LibraryShelf?) -> Unit,
    onAddToCollection: (LibraryCollectionUi) -> Unit,
    onRemoveFromCollection: (LibraryCollectionUi) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 88.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onOpen,
            )
            .padding(horizontal = horizontalPadding, vertical = 11.dp)
            .testTag("library_work_${work.id}"),
    ) {
        Text(
            text = work.title,
            fontFamily = AtharEditorialFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            lineHeight = 27.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOf(work.author, work.kind).filter(String::isNotBlank).joinToString(" · "),
            color = AtharTheme.colors.secondaryText,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )
        work.progress?.let { progress ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AtharProgressBar(progress, Modifier.weight(1f))
                Text(
                    text = work.progressLabel.orEmpty(),
                    modifier = Modifier.padding(start = 8.dp),
                    color = AtharTheme.colors.secondaryText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Row(
            modifier = Modifier.padding(top = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val statusOptions = listOf(NoStatus) + listOf(
                LibraryShelf.ReadLater,
                LibraryShelf.Reading,
                LibraryShelf.Finished,
            ).map { it.label }
            AtharMenuButton(
                label = "الحالة",
                options = statusOptions,
                selected = work.status?.label ?: NoStatus,
                defaultOption = NoStatus,
                onSelect = { label ->
                    onStatus(LibraryShelf.entries.firstOrNull { it.label == label })
                },
                tagPrefix = "library_status_${work.id}",
            )
            val available = collections.filter { it.id !in work.collectionIds }
            if (available.isNotEmpty()) {
                AtharMenuButton(
                    label = "أضف إلى مجموعة",
                    options = available.map { it.title },
                    selected = "أضف إلى مجموعة",
                    defaultOption = "أضف إلى مجموعة",
                    onSelect = { title -> onAddToCollection(available.first { it.title == title }) },
                    tagPrefix = "library_add_${work.id}",
                )
            }
            if (selectedCollection != null) {
                TextButton(onClick = { onRemoveFromCollection(selectedCollection) }) {
                    Text("إزالة")
                }
            }
        }
    }
}

@Composable
private fun CreateCollectionDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مجموعة جديدة") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(80) },
                label = { Text("اسم المجموعة") },
                singleLine = true,
                modifier = Modifier.testTag("collection_title"),
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title.trim()) }, enabled = title.isNotBlank()) {
                Text("إنشاء")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

private fun arabicDigits(value: Int): String = value.toString().map { "٠١٢٣٤٥٦٧٨٩"[it - '0'] }.joinToString("")
