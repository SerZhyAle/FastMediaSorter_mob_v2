package com.sza.fastmediasorter.wear.ui.player.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme

// Declared as const rather than as a `val ..: Dp` because detekt's MagicNumber is active on this
// module's main sources and exempts a constant declaration but not a property one.
private const val COMMAND_TOUCH_TARGET_DP = 48
private const val COMMAND_GLYPH_DP = 32

/**
 * The command surface of every watch player, and the only one they draw.
 *
 * Two sizes, and they are not the same thing: the [COMMAND_TOUCH_TARGET_DP] box is what a finger
 * hits, the [COMMAND_GLYPH_DP] icon is what an eye reads. Taking the plate away shrinks the mark,
 * never the target, which is why the box keeps its size with nothing painted in it.
 *
 * A caller passing [checked] must also pass a different [icon] per state. The tint is one signal and
 * the accessibility constraint asks for two, so a state told apart by colour alone is not told apart
 * on a watch held at arm's length or by an eye that does not separate those hues.
 *
 * The caller's [modifier] is applied before the default size, so a screen that needs a larger target
 * - the video player's play/pause - passes its own `size` and wins; everything else gets the default.
 *
 * S2140: a button carrying [onLongClick] should also carry [onLongClickLabel]. Without it TalkBack
 * offers the gesture as a bare "double tap and hold", which names the motion and not the command, so a
 * user who cannot see the icon has no way to learn what holding it would do.
 *
 * S2140 also dropped an `enabled` parameter that no caller had ever passed. It defaulted to true and
 * every one of the three players took the default, so it disabled nothing; kept alongside the long-press
 * pair it would have pushed this list to detekt's LongParameterList threshold for a switch nobody threw.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlayerCommandButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    checked: Boolean? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null
) {
    val tint = if (checked == true) {
        MaterialTheme.colors.primary
    } else {
        MaterialTheme.colors.onSurface
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(COMMAND_TOUCH_TARGET_DP.dp)
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick,
                onClick = onClick
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(COMMAND_GLYPH_DP.dp)
        )
    }
}
