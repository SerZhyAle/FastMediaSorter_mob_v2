package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ChipColors
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.LocalContentAlpha
import androidx.wear.compose.material.LocalContentColor
import androidx.wear.compose.material.LocalTextStyle
import androidx.wear.compose.material.MaterialTheme

// Wear Material keeps both of these internal to its own Chip, so a chip drawn outside the library
// has to restate them; they are the 1.2.1 values and the row looks wrong the moment they drift.
private val CHIP_HEIGHT = 52.dp
private val ICON_SPACING = 6.dp

/**
 * A Wear list row that looks exactly like `Chip` but serves the tap and the long press from one
 * handler.
 *
 * The library `Chip` cannot do this: it applies the caller's modifier OUTSIDE its own internal
 * `clickable`, so a gesture detector added by the caller is the outer node, loses the down to that
 * inner handler and never fires - the list half of S1953. Nothing passed in from outside can win
 * that race, so the row has to own its click handling instead of layering against it.
 *
 * `combinedClickable` also carries its own click semantics and `Role.Button`, so TalkBack and the
 * hardware buttons activate the row without a second `onClick` behind it - they never went through
 * pointer input, which is why the original fix kept one.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LongPressChip(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    label: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable BoxScope.() -> Unit)? = null,
    secondaryLabel: (@Composable RowScope.() -> Unit)? = null,
    colors: ChipColors = ChipDefaults.primaryChipColors()
) {
    val background = colors.background(enabled = true).value
    val contentColor = colors.contentColor(enabled = true).value
    val secondaryColor = colors.secondaryContentColor(enabled = true).value
    val iconColor = colors.iconColor(enabled = true).value
    Row(
        modifier = modifier
            .height(CHIP_HEIGHT)
            .clip(MaterialTheme.shapes.small)
            .width(IntrinsicSize.Max)
            .paint(painter = background, contentScale = ContentScale.Crop)
            .combinedClickable(role = Role.Button, onLongClick = onLongClick, onClick = onClick)
            .padding(ChipDefaults.ContentPadding)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxHeight()
        ) {
            if (icon != null) {
                ChipSlot(contentColor = iconColor) {
                    Box(modifier = Modifier.wrapContentSize(align = Alignment.Center), content = icon)
                }
                Spacer(modifier = Modifier.size(ICON_SPACING))
            }
            Column {
                ChipSlot(contentColor = contentColor, textStyle = MaterialTheme.typography.button) {
                    Row(content = label)
                }
                if (secondaryLabel != null) {
                    ChipSlot(contentColor = secondaryColor, textStyle = MaterialTheme.typography.caption2) {
                        Row(content = secondaryLabel)
                    }
                }
            }
        }
    }
}

/** Mirrors the library's own slot providers so a slot reads its colour and style from the chip. */
@Composable
private fun ChipSlot(
    contentColor: Color,
    textStyle: TextStyle = LocalTextStyle.current,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalContentColor provides contentColor,
        LocalContentAlpha provides contentColor.alpha,
        LocalTextStyle provides textStyle,
        content = content
    )
}
