package com.sza.fastmediasorter.wear.ui.theme

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Typography

private val BASE_TYPOGRAPHY = Typography()

/**
 * The watch's type scale: the Wear Material one, with a single token re-sized.
 *
 * S3257: `docs/ui/WEAR_UI_COMPONENT_PATTERNS.md` section 1.3 forbids an inline `fontSize = ..sp` in a
 * composable, and five screens held 24 of them anyway. Every literal maps onto a library token except
 * the athlete card's focal digits, which the library scale has no size for - its largest, `display1`,
 * is 40sp against the 48sp that card is drawn at, and the card exists to be read at a glance while
 * running. Re-sizing the token rather than shrinking the card keeps the screen and obeys the rule.
 *
 * `display1` is the one token touched because nothing in the module referenced it, so this override
 * changes no existing screen. `display2` and `display3` are deliberately left at their library values:
 * three other screens already take them, and moving one to suit the tourist hero card would re-size
 * the body-sensor reading, the stopwatch lap and the video player overlay along with it.
 */
val WearAppTypography: Typography = BASE_TYPOGRAPHY.copy(
    display1 = BASE_TYPOGRAPHY.display1.copy(
        fontSize = 48.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.ExtraBold
    )
)
