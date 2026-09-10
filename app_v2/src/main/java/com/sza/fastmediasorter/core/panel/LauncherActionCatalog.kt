package com.sza.fastmediasorter.core.panel

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R

/**
 * S1402: the actions a launcher cell can perform on the launcher itself - the same four the Start menu
 * offers. One catalog, because the label, the icon and the execution all key off the same string: the
 * gear/robot mix-up (S1405) is what a second, drifting copy of such a list looks like.
 *
 * These are deliberately NOT [InternalRouteCatalog] routes. A route is a screen any part of the app can
 * open with an Intent; three of these four exist only inside a running launcher and cannot be expressed
 * as one, so the launcher host performs them itself.
 */
object LauncherActionCatalog {

    const val KEY_APP_SETTINGS = "app_settings"
    const val KEY_LAUNCHER_SETTINGS = "launcher_settings"
    const val KEY_EDIT_DESKTOP = "edit_desktop"
    const val KEY_ALL_APPS = "all_apps"

    /**
     * S2859: opens the Add Resource flow. The Resources-section tile seeded by
     * [com.sza.fastmediasorter.domain.usecase.launcher.PlaceAddResourceTileUseCase] carries this
     * action, and the content picker's action category lists it as a manual placement option - both
     * render through this catalog, and the launcher host executes it.
     */
    const val KEY_CREATE_RESOURCE = "create_resource"

    const val KEY_BLACK_SCREEN = "black_screen"
    const val KEY_EXIT_LAUNCHER_MODE = "exit_launcher_mode"

    data class Action(
        val key: String,
        @StringRes val labelRes: Int,
        @DrawableRes val iconRes: Int,
    )

    /** Order is the order the Start menu lists them in, so the two surfaces read the same. */
    val all: List<Action> = listOf(
        Action(KEY_APP_SETTINGS, R.string.launcher_menu_app_settings, R.drawable.ic_settings),
        Action(KEY_LAUNCHER_SETTINGS, R.string.launcher_menu_launcher_settings, R.drawable.ic_launcher_mode),
        Action(KEY_EDIT_DESKTOP, R.string.launcher_edit_enter, R.drawable.ic_tune),
        Action(KEY_ALL_APPS, R.string.launcher_action_all_apps, R.drawable.ic_apps),
        // S2859: the Resources section's persistent Add-resource tile renders from this row - the
        // label and icon are the picker's own "Add resource.." wording, so the tile never drifts
        // from the flow it opens (strategic ADR-4). The content picker's action category gains a
        // manual placement option for the same action as a side effect of listing it here.
        Action(KEY_CREATE_RESOURCE, R.string.launcher_create_resource_menu_row, R.drawable.ic_add),
        Action(KEY_BLACK_SCREEN, R.string.launcher_action_black_screen, R.drawable.ic_black_screen),
        Action(KEY_EXIT_LAUNCHER_MODE, R.string.launcher_menu_exit_mode, R.drawable.ic_exit_to_app),
    )

    fun byKey(key: String): Action? = all.firstOrNull { it.key == key }
}
