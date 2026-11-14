package com.sza.fastmediasorter.ui.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.sza.fastmediasorter.R

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

    /** Title resource of the page menu entry. */
    @get:StringRes
    val tabTitleResId: Int

    /** S4621: the ICON-SET glyph of the page menu entry, drawn in the surface's one monochrome treatment. */
    @get:DrawableRes
    val iconResId: Int get() = R.drawable.ic_settings

    /** S4621: stable destination id - what the last page, scroll memory, search and help are keyed by. */
    val destinationId: String get() = "extension_$order"

    /** `true` if the tab should be shown at adapter construction time. */
    val isVisible: Boolean

    /** Fresh Fragment instance for this tab. Called by `FragmentStateAdapter`. */
    fun createFragment(): Fragment
}
