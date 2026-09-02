package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.LauncherAllAppsSwipeAction
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import com.sza.fastmediasorter.util.showBoundTo

/**
 * S2304: presents the five actions the All apps panel offers, through the same grouped dialog the edge
 * slots and the desktop swipes use.
 *
 * The narrow set is expressed as the rows handed to the shared dialog, never as a second dialog: the
 * divergence between two hand-built pickers is the defect S2256 was filed for, and a third surface
 * building its own would restore it.
 */
class LauncherAllAppsSwipeActionPickerManager(
    /**
     * Whether the accessibility seam that executes SYSTEM-group actions is compiled into this build.
     * Screen lock is dropped from the list where it is absent, which is what the shared availability
     * filter already does for every other surface - an offered action that silently does nothing is
     * worse than one the user never sees.
     */
    private val systemActionsAvailable: Boolean,
) {

    fun labelFor(context: Context, action: LauncherAllAppsSwipeAction): String =
        context.getString(metaFor(action).labelRes)

    fun showPicker(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        current: LauncherAllAppsSwipeAction,
        onPicked: (LauncherAllAppsSwipeAction) -> Unit,
    ) {
        GesturePickerDialog(
            context = context,
            title = context.getString(R.string.launcher_all_apps_swipe_picker_title),
            lifecycleOwner = lifecycleOwner,
            rows = GesturePickerRowBuilder().build(
                items = sharedActions().map { GesturePickerItem(it, metaFor(it)) },
                launcherRoutes = LOCAL_ROUTES.map { GesturePickerItem(it, metaFor(it)) },
            ),
            selectedKey = current,
            onPicked = onPicked,
        ).showBoundTo(lifecycleOwner)
    }

    /** The wrapped edge actions this panel offers, in the order they lead their groups. */
    private fun sharedActions(): List<LauncherAllAppsSwipeAction> = buildList {
        add(LauncherAllAppsSwipeAction.EdgeGestureAction(ScreenshotGestureAction.OPEN_APP))
        if (systemActionsAvailable) {
            add(LauncherAllAppsSwipeAction.EdgeGestureAction(ScreenshotGestureAction.LOCK_SCREEN))
        }
        add(LauncherAllAppsSwipeAction.Unassigned)
    }

    /**
     * The two panel-local routes carry their metadata here rather than in the shared catalog, for the
     * reason the desktop routes do: that catalog is keyed by [ScreenshotGestureAction], and neither
     * route has - or should have - an enum constant of its own.
     */
    private fun metaFor(action: LauncherAllAppsSwipeAction): GestureActionMeta = when (action) {
        LauncherAllAppsSwipeAction.BackToDesktop -> GestureActionMeta(
            GestureActionGroup.LAUNCH,
            R.string.launcher_all_apps_swipe_action_back_to_desktop,
            R.string.launcher_all_apps_swipe_explain_back_to_desktop,
            R.drawable.ic_launcher_mode,
        )
        LauncherAllAppsSwipeAction.ExpandAllApps -> GestureActionMeta(
            GestureActionGroup.LAUNCH,
            R.string.launcher_all_apps_swipe_action_expand,
            R.string.launcher_all_apps_swipe_explain_expand,
            R.drawable.ic_apps,
        )
        is LauncherAllAppsSwipeAction.EdgeGestureAction ->
            ScreenshotGestureActionCatalog.metaFor(action.action)
    }

    private companion object {
        /** The panel's own routes, in the order they lead their group. */
        val LOCAL_ROUTES = listOf(
            LauncherAllAppsSwipeAction.BackToDesktop,
            LauncherAllAppsSwipeAction.ExpandAllApps,
        )
    }
}
