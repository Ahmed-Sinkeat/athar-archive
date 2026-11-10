package com.atharchive.ui

import kotlinx.coroutines.flow.first
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.atharchive.feature.adhkar.AdhkarContent
import com.atharchive.feature.adhkar.AdhkarScreen
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import androidx.navigation3.runtime.EntryProviderScope

/** الأذكار, with one's own adhkar and reminders. */
internal fun EntryProviderScope<NavKey>.adhkarEntries(app: AppNav) = with(app) {
        entry<AdhkarRoute> {
            val favourites by contentViewModel.favouriteIds.collectAsStateWithLifecycle()
            val adhkar = remember(favourites, userAdhkarState) {
                val own = userAdhkarState.items.map { com.atharchive.feature.adhkar.DhikrUi(it.id, it.text, it.repeat, it.source, custom = true) }
                val inList = { list: String -> userAdhkarState.items.zip(own).filter { list in it.first.lists }.map { it.second } }
                AdhkarContent.copy(
                    babs = AdhkarContent.babs + userAdhkarState.lists.map { com.atharchive.feature.adhkar.AdhkarBabUi(it.id, it.label) },
                    entries = (AdhkarContent.entries.mapValues { (bab, list) -> list + inList(bab) } +
                        userAdhkarState.lists.associate { it.id to inList(it.id) })
                        .mapValues { (_, list) -> list.map { it.copy(saved = "dhikr:${it.id}" in favourites) } },
                )
            }
            AdhkarScreen(
                state = adhkar,
                onSettings = openSettings,
                onAdd = { list -> addDhikr("", "", setOf(list), actionAnchor.value) },
                onEdit = { dhikr -> userAdhkarState.items.firstOrNull { it.id == dhikr.id }?.let { editorAnchor = actionAnchor.value; editingDhikr = it } },
                onDeleteList = { id ->
                    com.atharchive.data.AdhkarReminders.set(context, id, "", null)
                    adhkarReminders = com.atharchive.data.AdhkarReminders.all(context)
                    userAdhkar.deleteList(id)
                },
                reminders = adhkarReminders,
                onReminder = { id, label, minutes ->
                    if (minutes != null && android.os.Build.VERSION.SDK_INT >= 33 &&
                        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    com.atharchive.data.AdhkarReminders.set(context, id, label, minutes)
                    adhkarReminders = com.atharchive.data.AdhkarReminders.all(context)
                    announce(if (minutes == null) "أُوقف التذكير" else "سيُذكّرك كل يوم")
                },
                openList = adhkarOpen,
                onOpened = { adhkarOpen = null },
                onCopy = { copyText(context, it.text); announce("نُسخ الذكر") },
                onSave = { contentViewModel.toggleFavourite("dhikr:${it.id}") },
                onBack = null,
                readerSettings = readerSettings,
            )
        }
}
