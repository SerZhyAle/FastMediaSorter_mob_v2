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
 * S2161: the tones this app adds on top of the Wear Material palette.
 *
 * Held here rather than as loose top-level values so a second state - a pause, say - extends one
 * type instead of adding one more literal to whichever screen needs it first (strategic 5.5).
 */
@Immutable
data class WearAppColors(
    val recording: Color = Color(RECORDING_RED)
)

internal val LocalWearAppColors = staticCompositionLocalOf { WearAppColors() }
