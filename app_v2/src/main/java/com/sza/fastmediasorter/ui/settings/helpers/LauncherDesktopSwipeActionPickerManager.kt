package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.LauncherDesktopSwipeAction
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import com.sza.fastmediasorter.util.showBoundTo

/**
 * Presents the launcher route plus every edge-gesture action executable in this build.
 *
 * S2256: through the same grouped dialog the edge-gesture slots use, over the same catalog metadata, so
 * an action is named, grouped, explained and iconed identically on both surfaces. The only difference
 * between the two surfaces is which actions each offers.
 */
class LauncherDesktopSwipeActionPickerManager(
    private val edgeGestureActionPicker: ScreenshotGestureActionPickerManager,
) {

    fun labelFor(context: Context, action: LauncherDesktopSwipeAction): String =
        context.getString(metaFor(action).labelRes)

    fun showPicker(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        current: LauncherDesktopSwipeAction,
        onPicked: (LauncherDesktopSwipeAction) -> Unit,
    ) {
        GesturePickerDialog(
            context = context,
            title = context.getString(R.string.launcher_desktop_swipe_picker_title),
            lifecycleOwner = lifecycleOwner,
            rows = GesturePickerRowBuilder().build(
                items = wrappedEdgeActions(),
                launcherRoutes = LOCAL_ROUTES.map { GesturePickerItem(it, metaFor(it)) },
            ),
            selectedKey = current,
            onPicked = onPicked,
        ).showBoundTo(lifecycleOwner)
    }

    /**
     * The edge picker is constructed without the launcher route, so `OPEN_ALL_APPS` is absent here and
     * the panel is offered once - as the desktop-local value, which reuses the already-open home task
     * instead of routing back through the overlay seam.
     */
    private fun wrappedEdgeActions(): List<GesturePickerItem<LauncherDesktopSwipeAction>> =
        edgeGestureActionPicker.pickerItems().map {
            GesturePickerItem(LauncherDesktopSwipeAction.EdgeGestureAction(it.key), it.meta, it.enabled)
        }

    /**
     * S2301: the two paging routes carry their metadata here rather than in the shared catalog, because
     * that catalog is keyed by [ScreenshotGestureAction] and neither route has - or should have - an
     * enum constant of its own.
     */
    private fun metaFor(action: LauncherDesktopSwipeAction): GestureActionMeta = when (action) {
        LauncherDesktopSwipeAction.OpenAllApps ->
            ScreenshotGestureActionCatalog.metaFor(ScreenshotGestureAction.OPEN_ALL_APPS)
        LauncherDesktopSwipeAction.NextScreen -> GestureActionMeta(
            GestureActionGroup.LAUNCH,
            R.string.launcher_desktop_swipe_action_next_screen,
            R.string.launcher_desktop_swipe_explain_next_screen,
            R.drawable.ic_arrow_forward,
        )
        LauncherDesktopSwipeAction.PreviousScreen -> GestureActionMeta(
            GestureActionGroup.LAUNCH,
            R.string.launcher_desktop_swipe_action_previous_screen,
            R.string.launcher_desktop_swipe_explain_previous_screen,
            R.drawable.ic_arrow_back,
        )
        is LauncherDesktopSwipeAction.EdgeGestureAction ->
            ScreenshotGestureActionCatalog.metaFor(action.action)
    }

    private companion object {
        /** The desktop's own routes, in the order they lead their group. */
        val LOCAL_ROUTES = listOf(
            LauncherDesktopSwipeAction.OpenAllApps,
            LauncherDesktopSwipeAction.NextScreen,
            LauncherDesktopSwipeAction.PreviousScreen,
        )
    }
}
