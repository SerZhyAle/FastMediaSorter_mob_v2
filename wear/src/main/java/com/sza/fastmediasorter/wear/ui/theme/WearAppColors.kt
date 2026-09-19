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
 * S3258: the tourist dashboard's metric identity tones.
 *
 * Each one answers "which metric is this", not "which scheme is active" - the heart rate reads red on
 * the hero card and on the athlete card alike, so repainting them per scheme would make two different
 * metrics indistinguishable at a glance while running.
 */
private const val TOURIST_HEART_RATE_RED = 0xFFFF5252
private const val TOURIST_ACCENT_TEAL = 0xFF00BFA5
private const val TOURIST_STEPS_AMBER = 0xFFFFAB00
private const val TOURIST_DISTANCE_BLUE = 0xFF448AFF
private const val TOURIST_GPS_FIX_GREEN = 0xFF00E676

/**
 * S3258: sun and compass tones on the tourist hero card.
 *
 * Sunrise and sunset are read as warmth, north and south as a physical needle - meanings the user
 * brings from outside the app, so they must not follow the scheme.
 */
private const val TOURIST_SUNRISE_YELLOW = 0xFFFFFF00
private const val TOURIST_SUNSET_CORAL = 0xFFFF8A80
private const val COMPASS_NORTH_RED = 0xFFE53935
private const val COMPASS_SOUTH_GREY = 0xFF9E9E9E

/**
 * S3258: the athlete canvas and what rides on it.
 *
 * The athlete card and the calculator, game and motion-monitor scaffolds keep a true-black canvas in
 * every scheme, so their foreground cannot come from `onSurface`, which follows the scheme - the hint
 * amber and the focal digits are dedicated tones for that reason.
 */
private const val TOURIST_UNLOCK_HINT_AMBER = 0xFFFFCC00
private const val CANVAS_TRUE_BLACK = 0xFF000000
private const val ATHLETE_ON_CANVAS_WHITE = 0xFFFFFFFF

/**
 * S2161 / S2468 / S2494 / S3258: the tones this app adds on top of the Wear Material palette.
 *
 * Held here rather than as loose top-level values so a second state - a pause, say - extends one
 * type instead of adding one more literal to whichever screen needs it first (strategic 5.5).
 *
 * S2522: [isLight] rides here because `androidx.wear.compose.material.Colors` carries no light/dark
 * signal at all - it declares thirteen colour roles and nothing else, and `MaterialTheme` does not
 * provide one either. So this companion set is the only place a composable can ask which way round the
 * current scheme is, which the background layer and the clock both have to know.
 *
 * Every tone here stays the same in every scheme on purpose. The four state tones report what is
 * happening - a live recording, a switch position, a hint - not which scheme is active, and
 * repainting them per scheme would make a state indistinguishable from an accent. The tourist metric,
 * sun and compass tones carry a meaning the user brings from outside the app. The canvas pair paints
 * a surface that is deliberately black under every scheme, so the scheme-following roles cannot
 * serve it.
 */
@Immutable
data class WearAppColors(
    val recording: Color = Color(RECORDING_RED),
    val toggleOn: Color = Color(TOGGLE_ON_BLUE),
    val toggleOff: Color = Color(TOGGLE_OFF_BROWN),
    val guideArrow: Color = Color(GUIDE_ARROW_AMBER),
    val heartRate: Color = Color(TOURIST_HEART_RATE_RED),
    val touristAccent: Color = Color(TOURIST_ACCENT_TEAL),
    val steps: Color = Color(TOURIST_STEPS_AMBER),
    val distance: Color = Color(TOURIST_DISTANCE_BLUE),
    val gpsFix: Color = Color(TOURIST_GPS_FIX_GREEN),
    val sunrise: Color = Color(TOURIST_SUNRISE_YELLOW),
    val sunset: Color = Color(TOURIST_SUNSET_CORAL),
    val compassNorth: Color = Color(COMPASS_NORTH_RED),
    val compassSouth: Color = Color(COMPASS_SOUTH_GREY),
    val unlockHint: Color = Color(TOURIST_UNLOCK_HINT_AMBER),
    val canvasBlack: Color = Color(CANVAS_TRUE_BLACK),
    val athleteOnCanvas: Color = Color(ATHLETE_ON_CANVAS_WHITE),
    val isLight: Boolean = false
)

internal val LocalWearAppColors = staticCompositionLocalOf { WearAppColors() }
