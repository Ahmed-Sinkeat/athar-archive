package com.atharchive.ui.components

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
import com.atharchive.R
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.icons.AtharMark
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme

/** One row of the app's ⋮ menu. */
data class AtharAppMenuEntry(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * The app-wide ⋮ menu — المقالات, المسائل, التنزيلات, الإعدادات. Provided once by
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
            // Branding only. It used to open الأقسام, which hid articles and fatwas behind an
            // unmarked logo; they now sit in the ⋮ menu.
            Box(
                modifier = Modifier.size(48.dp).testTag("athar_app_icon"),
                contentAlignment = Alignment.Center,
            ) {
                AtharMark(modifier = Modifier.size(30.dp), contentDescription = null)
            }
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                modifier = Modifier
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
                        AtharMenuItem(entry.label, icon = entry.icon, onClick = { open = false; entry.onClick() })
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
