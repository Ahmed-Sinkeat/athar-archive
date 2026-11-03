package com.atharchive.feature.adhkar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.atharchive.data.UserAdhkarList
import com.atharchive.data.UserDhikr
import com.atharchive.ui.components.AtharFilterChip
import com.atharchive.ui.components.AtharTailPopover
import com.atharchive.ui.components.arabicDigits
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme

/** The built-in lists a dhikr of one's own can join, beside the reader's own lists. */
val BuiltInAdhkarLists = listOf(UserAdhkarList("morning", "الصباح"), UserAdhkarList("evening", "المساء"))

/**
 * Add or edit one's own dhikr: its text, how many times, where it is from, and which lists
 * it is said in (several at once; a new list is named in place). [draft] with an id already
 * saved is an edit, and offers حذف.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DhikrEditorPopover(
    draft: UserDhikr,
    editing: Boolean,
    lists: List<UserAdhkarList>,
    anchor: () -> Rect,
    onSave: (UserDhikr, newList: String?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(draft.id) { mutableStateOf(draft.text) }
    // Western digits typed are read too; shown back as typed.
    var count by rememberSaveable(draft.id) { mutableStateOf(arabicDigits(draft.repeat)) }
    var source by rememberSaveable(draft.id) { mutableStateOf(draft.source.orEmpty()) }
    var chosen by rememberSaveable(draft.id) { mutableStateOf(draft.lists.toList()) }
    var newList by rememberSaveable(draft.id) { mutableStateOf("") }
    val repeat = count.map { if (it in '٠'..'٩') '0' + (it - '٠') else it }.joinToString("").filter(Char::isDigit).toIntOrNull()
    val canSave = text.isNotBlank() && (repeat ?: 0) > 0 && (chosen.isNotEmpty() || newList.isNotBlank())

    AtharTailPopover(anchor = { anchor() }, ground = AtharTheme.colors.raisedSurface, onDismiss = onDismiss, maxWidth = 340.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp).testTag("dhikr_editor")) {
            Text(
                if (editing) "تعديل الذكر" else "إضافة إلى الأذكار",
                fontFamily = AtharEditorialFontFamily,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(4_000) },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).testTag("dhikr_editor_text"),
                label = { Text("نص الذكر") }, minLines = 2, maxLines = 5,
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = count, onValueChange = { count = it.take(6) },
                    modifier = Modifier.width(96.dp).testTag("dhikr_editor_count"),
                    label = { Text("العدد") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = repeat == null || repeat <= 0,
                )
                OutlinedTextField(
                    value = source, onValueChange = { source = it.take(300) },
                    modifier = Modifier.weight(1f).testTag("dhikr_editor_source"),
                    label = { Text("المصدر أو التخريج") }, singleLine = true,
                )
            }
            Text("في", Modifier.padding(top = 12.dp, bottom = 6.dp), color = AtharTheme.colors.secondaryText, style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lists.forEach { list ->
                    val on = list.id in chosen
                    AtharFilterChip(list.label, on) { chosen = if (on) chosen - list.id else chosen + list.id }
                }
            }
            OutlinedTextField(
                value = newList, onValueChange = { newList = it.take(40) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag("dhikr_editor_new_list"),
                label = { Text("قائمة جديدة (اختياري)") }, singleLine = true,
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                if (editing) TextButton(onClick = onDelete, modifier = Modifier.testTag("dhikr_editor_delete")) {
                    Text("حذف", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                } else TextButton(onClick = onDismiss) { Text("إلغاء") }
                TextButton(
                    enabled = canSave,
                    onClick = { onSave(draft.copy(text = text, repeat = repeat ?: 1, source = source, lists = chosen.toSet()), newList.ifBlank { null }) },
                    modifier = Modifier.testTag("dhikr_editor_save"),
                ) { Text("حفظ") }
            }
        }
    }
}
