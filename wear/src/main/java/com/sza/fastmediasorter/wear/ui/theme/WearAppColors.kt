package com.sza.fastmediasorter.wear.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * S2161: an active microphone session.
 *
 * Deliberately NOT `MaterialTheme.colors.error`. The watch module already spends the Material error
 * tone on a single meaning - something went wrong - across every error caption, the calculator's
 * destructive keys and a game tile, so a recording painted with it would tell the user the recording
 * had failed. This is a red of its own: warmer and lighter than the error tone, so the two read as
 * different things when seen one after the other on the same glass (ADR-1).
 */
private const val RECORDING_RED = 0xFFFF4438

/**
 * S2468: toggle states on the watch.
 *
 * Deliberately NOT `MaterialTheme.colors.secondary`. The watch module already uses the Material
 * accent for other surfaces, so a toggle painted with it would report membership in the accent
 * rather than "on". These are dedicated tones: blue for on and brown for off (ADR-1).
 */
private const val TOGGLE_ON_BLUE = 0xFF4A90E2
private const val TOGGLE_OFF_BROWN = 0xFF8D6E63

/**
 * S2494: the start-of-level guide arrow in the game.
 *
 * The phone's amber, taken as is so the same hint reads the same on both devices. Deliberately NOT
 * one of the Material roles: the board already spends `surface`, `onSurfaceVariant` and `secondary`
 * on floor, wall and exit tiles, and the arrow crosses all three - a colour borrowed from any of
 * them would vanish over its own tiles exactly where the hint is needed.
 */
private const val GUIDE_ARROW_AMBER = 0xFFFFA000

/**
 * S2161 / S2468 / S2494: the tones this app adds on top of the Wear Material palette.
 *
 * Held here rather than as loose top-level values so a second state - a pause, say - extends one
 * type instead of adding one more literal to whichever screen needs it first (strategic 5.5).
 */
@Immutable
data class WearAppColors(
    val recording: Color = Color(RECORDING_RED),
    val toggleOn: Color = Color(TOGGLE_ON_BLUE),
    val toggleOff: Color = Color(TOGGLE_OFF_BROWN),
    val guideArrow: Color = Color(GUIDE_ARROW_AMBER)
)

internal val LocalWearAppColors = staticCompositionLocalOf { WearAppColors() }
