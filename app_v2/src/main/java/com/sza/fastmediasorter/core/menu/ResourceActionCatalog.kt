package com.sza.fastmediasorter.core.menu

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R

/**
 * S1424: one row of a resource's action menu, wherever that menu is drawn.
 *
 * The entries and their order mirror `res/menu/resource_item_actions.xml` exactly, because the main
 * window still inflates that file and the two must not disagree about what comes after what.
 * [menuItemId] is what makes that correspondence mechanical rather than a promise in a comment: an
 * entry added here without its XML row, or a row added there without its entry, stops matching.
 *
 * Label and icon travel with the entry so a surface that builds rows programmatically - the
 * launcher's popup - needs no second table mapping actions to captions.
 */
enum class ResourceMenuAction(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    @param:IdRes val menuItemId: Int,
) {
    OPEN(R.string.resource_menu_open, R.drawable.ic_open_in_browse, R.id.action_open_resource),
    LAUNCH_PLAYER(R.string.resource_menu_launch, R.drawable.ic_play, R.id.action_launch_player),
    OPEN_IN_VR_CINEMA(
        R.string.action_open_in_vr_cinema,
        R.drawable.ic_play,
        R.id.action_open_in_vr_cinema,
    ),
    ADD_TO_HOME_SCREEN(
        R.string.widget_add_to_home_screen,
        R.drawable.ic_widget_resource_launch,
        R.id.action_add_to_home_screen,
    ),
    EDIT(R.string.edit, R.drawable.ic_edit_20, R.id.action_edit),
    COPY(R.string.copy_resource, R.drawable.ic_copy, R.id.action_copy),
    EXPORT(R.string.resource_menu_export, R.drawable.ic_share, R.id.action_export_resource),
    SHARE_SFTP_ACCESS(
        R.string.resource_menu_share_sftp_access,
        R.drawable.ic_share,
        R.id.action_share_sftp_access,
    ),
    SCAN(R.string.action_refresh_resource, R.drawable.ic_refresh, R.id.action_scan),
    MOVE_UP(R.string.move_up, R.drawable.ic_arrow_upward, R.id.action_move_up),
    MOVE_DOWN(R.string.move_down, R.drawable.ic_arrow_downward, R.id.action_move_down),
    MOVE_TO_TOP(
        R.string.resource_menu_move_to_top,
        R.drawable.ic_arrow_upward,
        R.id.action_move_to_top,
    ),
    MOVE_TO_BOTTOM(
        R.string.resource_menu_move_to_bottom,
        R.drawable.ic_arrow_downward,
        R.id.action_move_to_bottom,
    ),
    DELETE(R.string.delete, R.drawable.ic_delete, R.id.action_delete),
    OPEN_IN_SEPARATE_WINDOW(
        R.string.action_open_in_separate_window,
        R.drawable.ic_open_in_browse,
        R.id.action_open_in_separate_window,
    ),
    ;

    companion object {

        /** The action an inflated menu row stands for, or null for a row no action owns. */
        fun byMenuItemId(@IdRes itemId: Int): ResourceMenuAction? =
            entries.firstOrNull { it.menuItemId == itemId }
    }
}

/**
 * S1424: the single source of a resource menu's composition (strategic ADR-1).
 *
 * Extracted rather than copied into the launcher: the resource menu gains items regularly, and a
 * second hand-maintained list would diverge from the first at the next one. A pure function of
 * [Facts] and [MenuActionSurface], so what a resource offers is provable in a unit test instead of
 * only on a device (strategic 11.6).
 *
 * Composition only - this object decides presence, never enabled state, and never what an entry does
 * once tapped.
 */
object ResourceActionCatalog {

    /**
     * Everything about a resource that changes what it offers. Deliberately booleans rather than the
     * resource itself: the decision must stay testable without constructing a `MediaResource`, and
     * two of these - a separate window being available, XR being present - are facts about the
     * device rather than about the resource.
     */
    data class Facts(
        val isPredefinedVirtual: Boolean = false,
        val isSftp: Boolean = false,
        val isQuickSlideshowEligible: Boolean = false,
        val isNewWindowAvailable: Boolean = false,
        val isVrCinemaAvailable: Boolean = false,
    )

    /**
     * Strategic 5.2. Reordering needs the whole ordered list rather than one identifier, and moving an
     * element inside a list the user cannot see at that moment gives no feedback; "open in a separate
     * window" means "beside the window I am in", and a desktop cell is in no window.
     */
    private val DESKTOP_EXCLUDED = setOf(
        ResourceMenuAction.MOVE_UP,
        ResourceMenuAction.MOVE_DOWN,
        ResourceMenuAction.MOVE_TO_TOP,
        ResourceMenuAction.MOVE_TO_BOTTOM,
        ResourceMenuAction.OPEN_IN_SEPARATE_WINDOW,
    )

    /**
     * Items [surface] offers for a resource described by [facts], in menu order.
     *
     * [canRun] is the host's own veto, and it exists so that a surface which cannot yet carry out an
     * action leaves it out rather than showing a row that does nothing - strategic ADR-2 treats a
     * dead item as worse than an absent one, that being the defect already present in the main
     * window's tile branch.
     */
    fun actionsFor(
        surface: MenuActionSurface,
        facts: Facts,
        canRun: (ResourceMenuAction) -> Boolean = { true },
    ): List<ResourceMenuAction> = ResourceMenuAction.entries.filter { action ->
        isRelevant(action, facts) && isOfferedOn(surface, action) && canRun(action)
    }

    private fun isOfferedOn(surface: MenuActionSurface, action: ResourceMenuAction): Boolean =
        surface != MenuActionSurface.LAUNCHER_DESKTOP || action !in DESKTOP_EXCLUDED

    /** The main window's six existing visibility rules, moved here unchanged. */
    private fun isRelevant(action: ResourceMenuAction, facts: Facts): Boolean = when (action) {
        ResourceMenuAction.COPY, ResourceMenuAction.EXPORT -> !facts.isPredefinedVirtual
        ResourceMenuAction.SHARE_SFTP_ACCESS -> facts.isSftp
        ResourceMenuAction.LAUNCH_PLAYER -> facts.isQuickSlideshowEligible
        ResourceMenuAction.OPEN_IN_SEPARATE_WINDOW -> facts.isNewWindowAvailable
        ResourceMenuAction.OPEN_IN_VR_CINEMA -> facts.isVrCinemaAvailable
        else -> true
    }
}
