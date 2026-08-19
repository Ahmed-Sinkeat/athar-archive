package com.atharchive.feature.sections

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.ui.components.AtharTopBar
import com.atharchive.ui.icons.AtharIcons
import com.atharchive.ui.theme.AtharEditorialFontFamily
import com.atharchive.ui.theme.AtharTheme

/**
 * A directory, not a home screen. It exists only for the sections that do not earn a
 * permanent bottom-navigation slot, so it deliberately does not repeat الكتب، الشعر،
 * الصوتيات، البحث or الكناشة.
 */
enum class AtharSection(
    val route: String,
    val label: String,
    val description: String,
    val icon: ImageVector,
) {
    Articles("articles", "المقالات", "مقالات وقراءات", AtharIcons.Articles),
    Issues("issues", "المسائل", "مسائل علمية مرتبة حسب الموضوع", AtharIcons.Issues),
    Adhkar("adhkar", "الأذكار", "أذكار وأدعية", AtharIcons.Adhkar),
}

@Composable
fun SectionsScreen(
    onBack: () -> Unit,
    onSectionClick: (AtharSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AtharTheme.colors.canvas)
            .testTag("section_sections"),
    ) {
        AtharTopBar(
            title = "الأقسام",
            onSettings = {},
            showAppIcon = true,
            onBack = onBack,
        )
        Column(modifier = Modifier.padding(top = 6.dp)) {
            AtharSection.entries.forEachIndexed { index, section ->
                SectionRow(section = section, onClick = { onSectionClick(section) })
                if (index != AtharSection.entries.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 68.dp, end = 20.dp),
                        thickness = 0.7.dp,
                        color = AtharTheme.colors.divider.copy(alpha = 0.42f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionRow(section: AtharSection, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("sections_row_${section.route}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = section.icon,
            contentDescription = null,
            tint = AtharTheme.colors.secondaryText,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = section.label,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = AtharEditorialFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                lineHeight = 27.sp,
            )
            Text(
                text = section.description,
                modifier = Modifier.padding(top = 2.dp),
                color = AtharTheme.colors.secondaryText.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Icon(
            imageVector = AtharIcons.Forward,
            contentDescription = null,
            tint = AtharTheme.colors.secondaryText.copy(alpha = 0.6f),
            modifier = Modifier.size(17.dp),
        )
    }
}
