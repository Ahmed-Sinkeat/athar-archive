package com.atharchive.ui.components

import androidx.compose.foundation.layout.widthIn
import com.atharchive.ui.theme.AtharTextSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.atharchive.R
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.icons.AtharMark
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme

/** The section popup's «no section» choice. */
const val SectionAll = "كل الأقسام"

/** One row of the app's ⋮ menu. */
data class AtharAppMenuEntry(val label: String, val icon: ImageVector, val current: Boolean = false, val onClick: () -> Unit)

/**
 * The app-wide ⋮ menu — المقالات, المسائل, الإعدادات. Provided once by
 * the app shell so every root screen shows the same menu without threading it through.
 */
val LocalAtharAppMenu = androidx.compose.runtime.staticCompositionLocalOf<List<AtharAppMenuEntry>> { emptyList() }

@Composable
fun AtharTopBar(
    title: String,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    showAppIcon: Boolean = false,
    horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp,
    onBack: (() -> Unit)? = null,
    actionIcon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    /** «الأقسام»: set (to «الكل» or a section), the title opens a popup of sections. */
    section: String? = null,
    onSection: (String) -> Unit = {},
    sectionCounts: Map<String, Int> = emptyMap(),
) {
    if (!showAppIcon) {
        LegacyAtharTopBar(
            title = title,
            onSettings = onSettings,
            modifier = modifier,
            horizontalPadding = horizontalPadding,
        )
        return
    }


    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding - 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("athar_back"),
            ) {
                Icon(
                    imageVector = AtharIcons.Back,
                    contentDescription = "رجوع",
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        } else {
            // Branding: a tap only plays the mark's writing-in again. It used to open الأقسام, which
            // hid articles and fatwas behind an unmarked logo; they now sit in the ⋮ menu.
            val reveal = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(1f) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            Box(
                modifier = Modifier.size(48.dp).testTag("athar_app_icon")
                    .clickable(interactionSource = null, indication = null) {
                        scope.launch { reveal.snapTo(0f); reveal.animateTo(1f, com.atharchive.ui.icons.AtharMarkMotion) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                AtharMark(modifier = Modifier.size(30.dp), contentDescription = null, reveal = { reveal.value })
            }
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            var sectionsOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            Row(
                modifier = if (section == null) Modifier else Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.DropdownList, onClickLabel = "الأقسام") { sectionsOpen = true }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .testTag("athar_section_title"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = androidx.compose.ui.text.buildAnnotatedString {
                        append(title)
                        if (section != null && section != SectionAll) {
                            append(" · ")
                            pushStyle(androidx.compose.ui.text.SpanStyle(color = AtharTheme.colors.accent))
                            append(section)
                            pop()
                        }
                    },
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .testTag("athar_page_title")
                        .semantics { heading() },
                    color = MaterialTheme.colorScheme.onBackground,
                    fontFamily = AtharEditorialFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = AtharTextSize.Large,
                    lineHeight = 28.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (section != null) Icon(
                    AtharIcons.ChevronDown, null,
                    Modifier.padding(start = 4.dp).size(16.dp),
                    tint = AtharTheme.colors.secondaryText,
                )
            }
            if (section != null) AtharMenu(expanded = sectionsOpen, onDismiss = { sectionsOpen = false }, width = 200.dp, modifier = Modifier.testTag("section_menu")) {
                (listOf(SectionAll) + com.atharchive.data.SubjectOrder).forEachIndexed { index, option ->
                    if (index == 1) AtharMenuDivider()
                    AtharMenuItem(
                        if (sectionCounts.isEmpty()) option else "$option  ${arabicDigits(sectionCounts[option] ?: 0)}",
                        checked = option == section, onClick = { sectionsOpen = false; onSection(option) })
                }
            }
        }
        // A screen-specific action sits inboard of الإعدادات, the way the readers
        // put البحث inboard of المزيد.
        if (actionIcon != null && onAction != null) {
            IconButton(
                onClick = onAction,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("athar_action"),
            ) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = actionLabel,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        val appMenu = LocalAtharAppMenu.current
        if (onBack == null && appMenu.isNotEmpty()) {
            var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { open = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("settings_action"),
                ) {
                    Icon(
                        imageVector = AtharIcons.More,
                        contentDescription = "القائمة",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                AtharMenu(expanded = open, onDismiss = { open = false }, width = 220.dp, modifier = Modifier.testTag("app_menu")) {
                    appMenu.forEachIndexed { index, entry ->
                        if (index == appMenu.lastIndex) AtharMenuDivider()
                        AtharMenuItem(entry.label, icon = entry.icon, checked = entry.current, onClick = { open = false; entry.onClick() })
                    }
                }
            }
        } else if (onBack == null) {
            IconButton(
                onClick = onSettings,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("settings_action"),
            ) {
                Icon(
                    imageVector = AtharIcons.Settings,
                    contentDescription = stringResource(R.string.settings),
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}

@Composable
private fun LegacyAtharTopBar(
    title: String,
    onSettings: () -> Unit,
    modifier: Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .testTag("athar_brand")
                .semantics { heading() },
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = AtharEditorialFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = AtharTextSize.Hero,
            lineHeight = 36.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(
            onClick = onSettings,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = AtharIcons.Settings,
                contentDescription = stringResource(R.string.settings),
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/**
 * «الأقسام» beside the filter icon in a section's search field: the section's name when one is
 * set, and the same small popup of sections, each with how many it holds. With a section set,
 * its [topics] follow as chips in the same popup: the topic is part of the section, not a filter.
 */
@Composable
fun AtharSectionButton(section: String, onSection: (String) -> Unit, counts: Map<String, Int>, tag: String, topics: AtharFacet? = null) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val set = section != SectionAll && section in com.atharchive.data.SubjectOrder
    Box {
        Row(
            Modifier.clip(CircleShape)
                .clickable(role = Role.DropdownList, onClickLabel = "الأقسام") { open = true }
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                // With no chips under the field, the ticked topics show as a count after the section.
                if (set) section + (topics?.selected?.size?.takeIf { it > 0 }?.let { " · ${arabicDigits(it)}" } ?: "") else "الأقسام",
                Modifier.widthIn(max = 110.dp),
                color = if (set) AtharTheme.colors.accent else AtharTheme.colors.secondaryText,
                fontSize = AtharTextSize.Small,
                fontWeight = if (set) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val withTopics = set && topics != null && topics.options.isNotEmpty()
        AtharMenu(expanded = open, onDismiss = { open = false }, width = if (topics != null) 280.dp else 200.dp, modifier = Modifier.testTag("section_menu")) {
            (listOf(SectionAll) + com.atharchive.data.SubjectOrder).forEachIndexed { index, option ->
                if (index == 1) AtharMenuDivider()
                AtharMenuItem(
                    if (counts.isEmpty()) option else "$option  ${arabicDigits(counts[option] ?: 0)}",
                    // A new section keeps the popup open when it has topics to pick from.
                    checked = option == section || (option == SectionAll && !set), onClick = { if (option == SectionAll || topics == null) open = false; onSection(option) },
                    // The arrow says a section opens onto its topics; they show right under it.
                    icon = if (topics != null && option != SectionAll && option != section) AtharIcons.ChevronDown else null)
                if (withTopics && option == section) Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp)) {
                    AtharFilterRow(topics!!.title) {
                        topics.options.forEach { value ->
                            val ticked = value in topics.selected
                            AtharFilterChip(value, ticked) { topics.onChange(if (ticked) topics.selected - value else topics.selected + value) }
                        }
                    }
                }
            }
        }
    }
}
