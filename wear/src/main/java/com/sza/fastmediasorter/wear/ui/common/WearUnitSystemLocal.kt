package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.sza.fastmediasorter.wear.core.util.WearUnitDateTimeFormatter
import com.sza.fastmediasorter.wear.domain.model.UnitSystem

/**
 * S2795: the stored measurement system, published to the whole screen tree.
 *
 * Fed from `WearAppearancePreferences.unitSystem` and from nowhere else, so a screen reading this is
 * reading what the phone last pushed. Not static: the value changes while the app is running, and a
 * push from the phone has to reach the clock and the history rows without a restart.
 *
 * The default is [UnitSystem.DEFAULT] rather than the device's own clock setting, so a subtree composed
 * outside the provider - a preview, a test host - renders the same way a fresh install does.
 */
val LocalWearUnitSystem = compositionLocalOf { UnitSystem.DEFAULT }

/**
 * S2795: the formatter itself. Static because the instance never changes within a composition - only
 * the system it is handed does, and that is [LocalWearUnitSystem]'s job.
 */
val LocalWearDateTimeFormatter = staticCompositionLocalOf { WearUnitDateTimeFormatter() }
