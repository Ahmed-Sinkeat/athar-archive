package com.atharchive.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharchive.ui.theme.AtharEditorialFontFamily

/**
 * One block, rendered by kind. The hierarchy is carried by size, weight and space —
 * never by a card around the text.
 *
 * Selection is per block here. Arbitrary character-range selection needs the bounded
 * native text surface that M0/R1 selected, because Compose exposes no public endpoint
 * callbacks; this keeps the interaction honest until the data layer lands.
 */
@Composable
fun BlockView(
    block: ReaderBlock,
    settings: ReaderSettings,
    colors: ReaderColors,
    selected: Boolean,
    highlighted: Boolean,
    isSearchTarget: Boolean,
    onLongPress: (fromTop: Boolean) -> Unit,
) {
    val body = settings.fontSize.sp
    val lineHeight = (settings.fontSize * settings.spacing.multiplier).sp

    val background = when {
        selected -> colors.selection
        highlighted -> colors.highlight
        isSearchTarget -> colors.accent.copy(alpha = 0.10f)
        else -> androidx.compose.ui.graphics.Color.Transparent
    }

    var pressedFromTop by remember { mutableStateOf(true) }
    val pressable = Modifier
        .fillMaxWidth()
        .onGloballyPositioned { pressedFromTop = it.positionInRoot().y < it.size.height * 2 }
        .pointerInput(block.id) {
            detectTapGestures(onLongPress = { onLongPress(pressedFromTop) })
        }

    when (block) {
        is ReaderBlock.Heading -> {
            val isKitab = block.level <= 2
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (isKitab) 34.dp else 24.dp, bottom = 10.dp)
                    .testTag("block_${block.id}"),
                horizontalAlignment = if (isKitab) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                Text(
                    text = block.text,
                    color = if (isKitab) colors.text else colors.accent,
                    fontFamily = AtharEditorialFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isKitab) (settings.fontSize + 5).sp else (settings.fontSize + 1).sp,
                    lineHeight = if (isKitab) (settings.fontSize + 14).sp else (settings.fontSize + 10).sp,
                    textAlign = if (isKitab) TextAlign.Center else TextAlign.Start,
                )
                if (isKitab) {
                    Box(
                        Modifier
                            .padding(top = 10.dp)
                            .width(52.dp)
                            .height(1.dp)
                            .background(colors.divider),
                    )
                }
            }
        }

        is ReaderBlock.Paragraph -> {
            Box(
                modifier = pressable
                    .clip(RoundedCornerShape(4.dp))
                    .background(background)
                    .padding(vertical = 7.dp)
                    .testTag("block_${block.id}"),
            ) {
                Text(
                    text = block.text,
                    color = colors.text,
                    fontFamily = AtharEditorialFontFamily,
                    fontSize = body,
                    lineHeight = lineHeight,
                    textAlign = TextAlign.Justify,
                )
            }
        }

        is ReaderBlock.Quote -> {
            Row(
                modifier = pressable
                    .clip(RoundedCornerShape(4.dp))
                    .background(background)
                    .padding(vertical = 10.dp)
                    .testTag("block_${block.id}"),
            ) {
                Box(
                    Modifier
                        .padding(end = 12.dp, top = 4.dp)
                        .width(2.dp)
                        .height((settings.fontSize * 1.6f).dp)
                        .background(colors.accent.copy(alpha = 0.55f)),
                )
                Text(
                    text = block.text,
                    color = colors.text.copy(alpha = 0.92f),
                    fontFamily = AtharEditorialFontFamily,
                    fontSize = (settings.fontSize - 1).sp,
                    lineHeight = lineHeight,
                )
            }
        }

        is ReaderBlock.Verse -> {
            Column(
                modifier = pressable
                    .clip(RoundedCornerShape(4.dp))
                    .background(background)
                    .padding(vertical = 12.dp)
                    .testTag("block_${block.id}"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (block.ajuz != null) {
                    // Two hemistichs, each centred in its own half — never a guessed break.
                    Row(modifier = Modifier.fillMaxWidth()) {
                        VerseHalf(block.sadr, colors, settings, Modifier.weight(1f))
                        VerseHalf(block.ajuz, colors, settings, Modifier.weight(1f))
                    }
                } else {
                    VerseHalf(block.sadr, colors, settings, Modifier.fillMaxWidth())
                }
            }
        }

        is ReaderBlock.PageBreak -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .testTag("block_${block.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    thickness = 0.7.dp,
                    color = colors.divider,
                )
                Text(
                    text = if (block.volume != null) {
                        "ج${arabic(block.volume)} · ص${arabic(block.page)}"
                    } else {
                        "ص${arabic(block.page)}"
                    },
                    modifier = Modifier.padding(horizontal = 10.dp),
                    color = colors.secondary.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall,
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    thickness = 0.7.dp,
                    color = colors.divider,
                )
            }
        }

        is ReaderBlock.FootnoteBody -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .testTag("block_${block.id}"),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "(${block.marker})",
                    color = colors.accent,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(end = 6.dp, top = 2.dp),
                )
                Text(
                    text = block.text,
                    color = colors.secondary,
                    fontFamily = AtharEditorialFontFamily,
                    fontSize = (settings.fontSize - 4).sp,
                    lineHeight = (settings.fontSize * 1.5f).sp,
                )
            }
        }
    }
}

@Composable
private fun VerseHalf(
    text: String,
    colors: ReaderColors,
    settings: ReaderSettings,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        color = colors.text,
        fontFamily = AtharEditorialFontFamily,
        fontSize = (settings.fontSize - 1).sp,
        lineHeight = (settings.fontSize * settings.spacing.multiplier).sp,
        textAlign = TextAlign.Center,
    )
}
