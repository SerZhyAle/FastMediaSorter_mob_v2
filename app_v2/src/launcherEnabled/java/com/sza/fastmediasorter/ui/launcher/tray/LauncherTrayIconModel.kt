package com.sza.fastmediasorter.ui.launcher.tray

import androidx.annotation.DrawableRes

/**
 * S2023: everything one tray icon shows, in one value the renderer applies.
 *
 * A value the platform does not report is expressed by a null [badge] rather than by an empty string or a
 * zero: the tray already treats an unreadable SIM slot as absent instead of drawing level 0, which would
 * read as "no coverage", and strategic §5.1 extends that rule to every value added here.
 *
 * [cornerMarked] is deliberately separate from [badge]. Both would otherwise compete for the same corner of
 * an 18dp icon, and ADR-8 keeps the SIM data-type letter where it is by giving roaming its own corner.
 */
data class LauncherTrayIconModel(
    @DrawableRes val iconRes: Int,
    val badge: String? = null,
    val highlighted: Boolean = false,
    val cornerMarked: Boolean = false,
    val contentDescription: CharSequence? = null,
)
