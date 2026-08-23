package com.sza.fastmediasorter.wear.ui.common

import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.ScalingParams

/**
 * Where a list begins shrinking an item, as a fraction of the viewport measured from each edge.
 *
 * The library defaults are 0.35..0.55 and they are tuned for a circle, which never reaches the glass:
 * a rectangular cell's corner sits about 40 px further from the screen centre than the edge of the
 * circle inscribed in the same box, and that is what put four watch grids past the rim (S1970).
 *
 * The arithmetic, on the 480x480 round panel the ticket was measured on: an outer-column cell spans
 * x 48..432, so its corner is 192 px off the vertical axis and stays inside the 240 px display radius
 * only while its own y is within 240 +/- sqrt(240^2 - 192^2) = 240 +/- 144. A 145 px cell therefore
 * has to start shrinking once its top edge is nearer than 241 px to the viewport edge - a transition
 * line of at least 0.502 - while the default line for that cell height lands at 0.401, some 48 px too
 * late. 0.55..0.65 puts the line at 0.576 for a grid cell and leaves the worst full-size corner at
 * 222 px, back inside the radius and level with the circle this shape replaced.
 */
private const val GRID_MIN_TRANSITION_AREA = 0.55f

/** Upper end of the same line, reached by the tallest items; see [GRID_MIN_TRANSITION_AREA]. */
private const val GRID_MAX_TRANSITION_AREA = 0.65f

/**
 * Scaling shared by every watch list that draws rectangular cells or tiles.
 *
 * `edgeScale` and `edgeAlpha` stay at the library defaults on purpose: the defect is WHEN the shrink
 * starts, not how deep it goes, and a deeper edge scale would shrink the tap target of a cell the
 * user can still reach - the accessibility minimum is a Wear OS review item (WO-V2), not a taste.
 */
val WearGridScalingParams: ScalingParams = ScalingLazyColumnDefaults.scalingParams(
    minTransitionArea = GRID_MIN_TRANSITION_AREA,
    maxTransitionArea = GRID_MAX_TRANSITION_AREA
)
