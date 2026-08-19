package com.atharchive.ui.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.R
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.icons.AtharMark
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme

@Composable
fun AtharTopBar(
    title: String,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    showAppIcon: Boolean = false,
    horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp,
    onBack: (() -> Unit)? = null,
    onLogo: (() -> Unit)? = null,
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
            val logoModifier = if (onLogo != null) {
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onLogo)
            } else {
                Modifier.size(48.dp)
            }
            Box(modifier = logoModifier, contentAlignment = Alignment.Center) {
                AtharMark(
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("athar_app_icon"),
                    contentDescription = if (onLogo != null) "الأقسام" else null,
                )
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
                fontSize = 19.sp,
                lineHeight = 28.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onBack == null) {
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
            fontSize = 26.sp,
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
