package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ButtonColors
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.LocalContentAlpha
import androidx.wear.compose.material.LocalContentColor
import androidx.wear.compose.material.LocalTextStyle
import androidx.wear.compose.material.MaterialTheme

/** The one cell radius of the watch module. Anything rectangular here asks for it instead of writing 8.dp again. */
internal val WearCellCorner: Dp = 8.dp

internal val WearCellShape: Shape = RoundedCornerShape(WearCellCorner)

/**
 * The rectangular button of the watch module, and the only button surface it draws.
 *
 * It restates `androidx.wear.compose.material.Button` rather than wrapping it, for the same reason
 * [LongPressChip] restates `Chip`: the library button owns its own `clickable`, so a caller that
 * wants a long press can only layer a gesture detector against that handler, and two handlers on one
 * node is exactly the arrangement S1953 was opened for. Here one `combinedClickable` serves the tap
 * and the long press, so [onLongClick] costs the caller nothing and the tap keeps its ripple and its
 * `Role.Button` semantics - which is what TalkBack and the hardware buttons activate.
 *
 * The modifier order below is copied from `androidx.wear.compose.materialcore.Button`: clip, click,
 * caller modifier, size, background. A caller that passed `Modifier.size(48.dp)` therefore keeps the
 * same tap target it had before, and the ripple still covers the whole shape.
 *
 * The library's `border` parameter is not restated: its default resolves to no border and no call
 * site in this module ever passed one, so carrying it would only be a slot nothing fills.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RectangularButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.primaryButtonColors(),
    shape: Shape = WearCellShape,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val contentColor = colors.contentColor(enabled = enabled).value
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(shape)
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = {
                    onClick()
                }
            )
            .then(modifier)
            .size(ButtonDefaults.DefaultButtonSize)
            .clip(shape)
            .background(color = colors.backgroundColor(enabled = enabled).value, shape = shape)
    ) {
        // The slot is a BoxScope one, so the scope is carried into the provider's own scopeless slot.
        val boxScope = this
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            LocalContentAlpha provides contentColor.alpha,
            LocalTextStyle provides MaterialTheme.typography.button
        ) {
            boxScope.content()
        }
    }
}
