package com.sza.fastmediasorter.ui.settings

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment

/**
 * Plugin contract for flavor-supplied Settings tabs (S0245).
 *
 * Implementations live in flavor source sets (e.g. `src/vr/java/`) and bind via
 * `@IntoSet` into the `Set<SettingsTabExtension>` multibinding. `SettingsPagerAdapter`
 * injects the set, filters by [isVisible], sorts by [order], and appends to the static
 * 4 main tabs.
 *
 * Phone-only flavors (standard, lite, photos, legacy) provide no implementations - the
 * multibinding stays empty, so the static 4 tabs remain.
 */
interface SettingsTabExtension {
    /** Display order. Lower values appear first. Existing static tabs occupy 0..3. */
    val order: Int

    /** Title resource for the TabLayout entry. */
    @get:StringRes
    val tabTitleResId: Int

    /** `true` if the tab should be shown at adapter construction time. */
    val isVisible: Boolean

    /** Fresh Fragment instance for this tab. Called by `FragmentStateAdapter`. */
    fun createFragment(): Fragment
}
