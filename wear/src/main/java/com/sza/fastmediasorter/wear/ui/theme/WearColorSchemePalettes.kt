package com.sza.fastmediasorter.wear.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import com.sza.fastmediasorter.wear.domain.model.WearColorScheme

/**
 * S2522: the palette each colour scheme expands into.
 *
 * Every one of the thirteen roles Wear Compose declares is passed explicitly for every scheme, and no
 * scheme is built by copying another (strategic 5.2). A role left to the constructor default would
 * inherit the library's dark value, which is invisible on the dark schemes and lands exactly one
 * mismatched element in the middle of a light one.
 *
 * Accent hues are the phone's own values, unchanged, so a family reads as the same family across the
 * pair. Surfaces are not: the phone's dark surfaces sit far below the level Wear Compose assumes, and a
 * chip painted with one would disappear against the scrimmed background the watch draws behind every
 * screen - so each dark accent scheme takes the lifted member of its own family instead (ADR-4).
 */

// The plain dark scheme is the library's own default, member for member. Reproduced here as literals
// rather than by calling Colors() with no arguments, because "unchanged" has to survive a library
// upgrade silently changing what its defaults are - that would repaint the watch of an owner who never
// opened this setting, which strategic 3.2 forbids.
private const val DARK_PRIMARY = 0xFFAECBFA
private const val DARK_PRIMARY_VARIANT = 0xFF8AB4F8
private const val DARK_SECONDARY = 0xFFFDE293
private const val DARK_SECONDARY_VARIANT = 0xFF594F33
private const val DARK_SURFACE = 0xFF303133
private const val DARK_ON_PRIMARY = 0xFF303133
private const val DARK_ON_SECONDARY = 0xFF303133
private const val DARK_ON_SURFACE_VARIANT = 0xFFDADCE0

// Shared neutrals. The light schemes all resolve content to the same near-black rather than to pure
// black: pure black against a white surface is harsher than any of the phone's light themes uses.
private const val NEUTRAL_WHITE = 0xFFFFFFFF
private const val NEUTRAL_NEAR_BLACK = 0xFF1B1B1B
private const val LIGHT_ERROR = 0xFFB3261E
private const val DARK_ERROR = 0xFFEE675C
private const val DARK_ON_ERROR = 0xFF000000

// Plain light: the neutral member, mirroring the plain dark above. Its accent is a blue because the
// library default it mirrors is one; the blue family scheme sits one step deeper, the way the phone's
// own plain and blue light themes differ.
private const val LIGHT_PRIMARY = 0xFF1A73E8
private const val LIGHT_PRIMARY_VARIANT = 0xFF1557B0
private const val LIGHT_SECONDARY = 0xFF7B5800
private const val LIGHT_SECONDARY_VARIANT = 0xFFFDE293
private const val LIGHT_BACKGROUND = 0xFFF5F5F5
private const val LIGHT_ON_SURFACE_VARIANT = 0xFF45464F

// Green family - phone values.
private const val GREEN_DARK_PRIMARY = 0xFF81C784
private const val GREEN_DARK_PRIMARY_VARIANT = 0xFF1B5E20
private const val GREEN_DARK_SECONDARY = 0xFFC8E6C9
private const val GREEN_DARK_SECONDARY_VARIANT = 0xFF33472F
private const val GREEN_DARK_BACKGROUND = 0xFF0F1A0F
private const val GREEN_DARK_SURFACE = 0xFF263826
private const val GREEN_DARK_ON_SURFACE_VARIANT = 0xFFC2D0C2
private const val GREEN_DEEP = 0xFF0A2E0A
private const val GREEN_LIGHT_PRIMARY = 0xFF2E7D32
private const val GREEN_LIGHT_BACKGROUND = 0xFFF1F8E9
private const val GREEN_LIGHT_ON_SURFACE_VARIANT = 0xFF42493E

// Blue family - phone values.
private const val BLUE_DARK_PRIMARY = 0xFF64B5F6
private const val BLUE_DARK_PRIMARY_VARIANT = 0xFF0D47A1
private const val BLUE_DARK_SECONDARY = 0xFFD6E8FF
private const val BLUE_DARK_SECONDARY_VARIANT = 0xFF2A3F5A
private const val BLUE_DARK_BACKGROUND = 0xFF0F1420
private const val BLUE_DARK_SURFACE = 0xFF243551
private const val BLUE_DARK_ON_SURFACE_VARIANT = 0xFFC0C8D6
private const val BLUE_DEEP = 0xFF06243B
private const val BLUE_LIGHT_PRIMARY = 0xFF1565C0
private const val BLUE_LIGHT_BACKGROUND = 0xFFE3F2FD
private const val BLUE_LIGHT_ON_SURFACE_VARIANT = 0xFF3E4650

// Red family - phone values.
private const val RED_DARK_PRIMARY = 0xFFEF9A9A
private const val RED_DARK_PRIMARY_VARIANT = 0xFF8E0000
private const val RED_DARK_SECONDARY = 0xFFFFEAE7
private const val RED_DARK_SECONDARY_VARIANT = 0xFF4A2F2F
private const val RED_DARK_BACKGROUND = 0xFF1A0F0F
private const val RED_DARK_SURFACE = 0xFF3F2626
private const val RED_DARK_ON_SURFACE_VARIANT = 0xFFD6C2C2
private const val RED_DEEP = 0xFF3B0A0A
private const val RED_LIGHT_PRIMARY = 0xFFC62828
private const val RED_LIGHT_BACKGROUND = 0xFFFFEBEE
private const val RED_LIGHT_ON_SURFACE_VARIANT = 0xFF50403F

/** The palette [scheme] is drawn in. Total: every scheme has one, so no call site needs a fallback. */
fun paletteFor(scheme: WearColorScheme): Colors = when (scheme) {
    WearColorScheme.DARK -> darkPalette()
    WearColorScheme.LIGHT -> lightPalette()
    WearColorScheme.DARK_GREEN -> darkGreenPalette()
    WearColorScheme.DARK_BLUE -> darkBluePalette()
    WearColorScheme.DARK_RED -> darkRedPalette()
    WearColorScheme.LIGHT_GREEN -> lightGreenPalette()
    WearColorScheme.LIGHT_BLUE -> lightBluePalette()
    WearColorScheme.LIGHT_RED -> lightRedPalette()
}

private fun darkPalette(): Colors = Colors(
    primary = Color(DARK_PRIMARY),
    primaryVariant = Color(DARK_PRIMARY_VARIANT),
    secondary = Color(DARK_SECONDARY),
    secondaryVariant = Color(DARK_SECONDARY_VARIANT),
    background = Color.Black,
    surface = Color(DARK_SURFACE),
    error = Color(DARK_ERROR),
    onPrimary = Color(DARK_ON_PRIMARY),
    onSecondary = Color(DARK_ON_SECONDARY),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(DARK_ON_SURFACE_VARIANT),
    onError = Color(DARK_ON_ERROR)
)

private fun lightPalette(): Colors = Colors(
    primary = Color(LIGHT_PRIMARY),
    primaryVariant = Color(LIGHT_PRIMARY_VARIANT),
    secondary = Color(LIGHT_SECONDARY),
    secondaryVariant = Color(LIGHT_SECONDARY_VARIANT),
    background = Color(LIGHT_BACKGROUND),
    surface = Color(NEUTRAL_WHITE),
    error = Color(LIGHT_ERROR),
    onPrimary = Color(NEUTRAL_WHITE),
    onSecondary = Color(NEUTRAL_WHITE),
    onBackground = Color(NEUTRAL_NEAR_BLACK),
    onSurface = Color(NEUTRAL_NEAR_BLACK),
    onSurfaceVariant = Color(LIGHT_ON_SURFACE_VARIANT),
    onError = Color(NEUTRAL_WHITE)
)

private fun darkGreenPalette(): Colors = Colors(
    primary = Color(GREEN_DARK_PRIMARY),
    primaryVariant = Color(GREEN_DARK_PRIMARY_VARIANT),
    secondary = Color(GREEN_DARK_SECONDARY),
    secondaryVariant = Color(GREEN_DARK_SECONDARY_VARIANT),
    background = Color(GREEN_DARK_BACKGROUND),
    surface = Color(GREEN_DARK_SURFACE),
    error = Color(DARK_ERROR),
    onPrimary = Color(GREEN_DEEP),
    onSecondary = Color(GREEN_DEEP),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(GREEN_DARK_ON_SURFACE_VARIANT),
    onError = Color(DARK_ON_ERROR)
)

private fun darkBluePalette(): Colors = Colors(
    primary = Color(BLUE_DARK_PRIMARY),
    primaryVariant = Color(BLUE_DARK_PRIMARY_VARIANT),
    secondary = Color(BLUE_DARK_SECONDARY),
    secondaryVariant = Color(BLUE_DARK_SECONDARY_VARIANT),
    background = Color(BLUE_DARK_BACKGROUND),
    surface = Color(BLUE_DARK_SURFACE),
    error = Color(DARK_ERROR),
    onPrimary = Color(BLUE_DEEP),
    onSecondary = Color(BLUE_DEEP),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(BLUE_DARK_ON_SURFACE_VARIANT),
    onError = Color(DARK_ON_ERROR)
)

private fun darkRedPalette(): Colors = Colors(
    primary = Color(RED_DARK_PRIMARY),
    primaryVariant = Color(RED_DARK_PRIMARY_VARIANT),
    secondary = Color(RED_DARK_SECONDARY),
    secondaryVariant = Color(RED_DARK_SECONDARY_VARIANT),
    background = Color(RED_DARK_BACKGROUND),
    surface = Color(RED_DARK_SURFACE),
    error = Color(DARK_ERROR),
    onPrimary = Color(RED_DEEP),
    onSecondary = Color(RED_DEEP),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(RED_DARK_ON_SURFACE_VARIANT),
    onError = Color(DARK_ON_ERROR)
)

private fun lightGreenPalette(): Colors = Colors(
    primary = Color(GREEN_LIGHT_PRIMARY),
    primaryVariant = Color(GREEN_DARK_PRIMARY_VARIANT),
    secondary = Color(GREEN_DEEP),
    secondaryVariant = Color(GREEN_DARK_SECONDARY_VARIANT),
    background = Color(GREEN_LIGHT_BACKGROUND),
    surface = Color(NEUTRAL_WHITE),
    error = Color(LIGHT_ERROR),
    onPrimary = Color(NEUTRAL_WHITE),
    onSecondary = Color(NEUTRAL_WHITE),
    onBackground = Color(NEUTRAL_NEAR_BLACK),
    onSurface = Color(NEUTRAL_NEAR_BLACK),
    onSurfaceVariant = Color(GREEN_LIGHT_ON_SURFACE_VARIANT),
    onError = Color(NEUTRAL_WHITE)
)

private fun lightBluePalette(): Colors = Colors(
    primary = Color(BLUE_LIGHT_PRIMARY),
    primaryVariant = Color(BLUE_DARK_PRIMARY_VARIANT),
    secondary = Color(BLUE_DEEP),
    secondaryVariant = Color(BLUE_DARK_SECONDARY_VARIANT),
    background = Color(BLUE_LIGHT_BACKGROUND),
    surface = Color(NEUTRAL_WHITE),
    error = Color(LIGHT_ERROR),
    onPrimary = Color(NEUTRAL_WHITE),
    onSecondary = Color(NEUTRAL_WHITE),
    onBackground = Color(NEUTRAL_NEAR_BLACK),
    onSurface = Color(NEUTRAL_NEAR_BLACK),
    onSurfaceVariant = Color(BLUE_LIGHT_ON_SURFACE_VARIANT),
    onError = Color(NEUTRAL_WHITE)
)

private fun lightRedPalette(): Colors = Colors(
    primary = Color(RED_LIGHT_PRIMARY),
    primaryVariant = Color(RED_DARK_PRIMARY_VARIANT),
    secondary = Color(RED_DEEP),
    secondaryVariant = Color(RED_DARK_SECONDARY_VARIANT),
    background = Color(RED_LIGHT_BACKGROUND),
    surface = Color(NEUTRAL_WHITE),
    error = Color(LIGHT_ERROR),
    onPrimary = Color(NEUTRAL_WHITE),
    onSecondary = Color(NEUTRAL_WHITE),
    onBackground = Color(NEUTRAL_NEAR_BLACK),
    onSurface = Color(NEUTRAL_NEAR_BLACK),
    onSurfaceVariant = Color(RED_LIGHT_ON_SURFACE_VARIANT),
    onError = Color(NEUTRAL_WHITE)
)
