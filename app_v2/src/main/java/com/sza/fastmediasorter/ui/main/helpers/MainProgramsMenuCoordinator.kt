package com.sza.fastmediasorter.ui.main.helpers

import android.content.Intent
import android.content.res.ColorStateList
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.MenuItemCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.panel.AppLaunchPanelRouteIntents
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.panel.SubProgramAccentCatalog
import com.sza.fastmediasorter.core.panel.SubProgramCatalog
import com.sza.fastmediasorter.core.panel.SubProgramEntry
import com.sza.fastmediasorter.core.panel.SubProgramSurface
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.ui.applaunchpanel.AppLaunchPanelActivity
import com.sza.fastmediasorter.ui.calculator.CalculatorActivity
import com.sza.fastmediasorter.ui.networkmonitor.NetworkMonitorActivity
import com.sza.fastmediasorter.ui.stopwatch.StopwatchActivity
import com.sza.fastmediasorter.ui.streams.StreamsActivity
import com.sza.fastmediasorter.ui.systeminfo.SystemInfoActivity
import com.sza.fastmediasorter.ui.wear.WatchListenLaunchActivity
import com.sza.fastmediasorter.ui.wear.WearCompanionActivity
import timber.log.Timber

/**
 * S0774: single home for the main-window programs menu - item registration, count, click dispatch,
 * and the S0770 per-item "Open in new window" / "Remove" resolvers.
 *
 * S2673: which sub-programs the menu shows, in what order, and whether each is available now come
 * from [SubProgramCatalog] and the one route-availability chain. Three items are not sub-programs and
 * are still added by hand: the streams entry, VR Cinema and the quick-launch panel. The live broadcast
 * is a registry entry; drawing it by hand as well listed it twice.
 *
 * S1736 phase 04: the label and the icon are now read from [InternalRouteCatalog] instead of a second
 * copy kept here, so a program is worded and drawn the same in the menu, the quick-access panel, the
 * widget picker and the launcher. The menu item id is all that stays: seven manager classes and the
 * programs panel dispatch taps on it, so it is an identity, not presentation.
 *
 * Taps still travel through the per-program managers. A route's intent says how to OPEN a program,
 * never what a tap does: quick capture and the link download act inside this window, and routing them
 * through the route catalog would replace an in-place action with a trampoline activity.
 */
@Suppress("LongParameterList")
class MainProgramsMenuCoordinator(
    private val activity: AppCompatActivity,
    private val miniGameMenuManager: MainMiniGameMenuManager,
    private val wearCompanionMenuManager: MainWearCompanionMenuManager,
    private val streamsMenuManager: MainStreamsMenuManager,
    private val quickCaptureMenuManager: MainQuickCaptureMenuManager,
    private val linkDownloadMenuManager: MainLinkDownloadMenuManager,
    private val screenRecordingMenuManager: MainScreenRecordingMenuManager,
    private val broadcastMenuManager: MainBroadcastMenuManager,
    private val hostActions: ProgramsHostActions,
) {

    /**
     * Host callbacks the coordinator delegates to, all resolved by MainActivity (via its panel-item
     * actions manager). Bundled into one holder so the constructor stays under the detekt
     * LongParameterList threshold as new program entries (S0962: VR Cinema) are added.
     */
    class ProgramsHostActions(
        val isNewWindowAvailable: () -> Boolean,
        val launchInNewWindow: (Intent) -> Unit,
        val confirmRemoveProgram: (Int, (AppSettings) -> AppSettings) -> Unit,
        // S0962 (VR Cinema, Pillar 1): tap handler - prompts for a resource, then opens the browser.
        val onVrCinemaSelected: () -> Unit,
    )

    /**
     * Resolved visibility for one menu build.
     *
     * S2673: the twelve per-program booleans are gone. Every sub-program answers through
     * [isSubProgramAvailable], which MainActivity binds to `ResolvePanelRouteAvailabilityUseCase`, so
     * neither side holds a second mapping from a route key to a boolean. The three fields that remain
     * belong to the items the registry does not govern.
     */
    data class ProgramsMenuGate(
        val streams: Boolean,
        val vrCinema: Boolean,
        val broadcast: Boolean,
        val isSubProgramAvailable: (String) -> Boolean,
    )

    /** What a programs-panel "Remove" tap needs: the program's own title and its own off-switch. */
    data class RemoveTarget(val titleRes: Int, val disable: (AppSettings) -> AppSettings)

    // S0757: the quick-launch panel entry is always present (no toggle), so the count starts at 1 and
    // the three-dots menu button stays visible even when every other program is disabled.
    fun itemCount(gate: ProgramsMenuGate): Int =
        1 + (if (gate.streams) 1 else 0) + (if (gate.vrCinema) 1 else 0) + visibleSubPrograms(gate).size

    // S0756: excludeStreams drops the "Streams" item (the programs panel hides it when the streams
    // panel is visible, to avoid duplicating that entry point). The dropdown menu always passes false.
    fun populate(popup: PopupMenu, excludeStreams: Boolean, gate: ProgramsMenuGate): Int {
        popup.menu.clear()
        streamsMenuManager.populate(popup, !excludeStreams && gate.streams, MENU_ORDER_STREAMS)
        // S0962 (VR Cinema, Pillar 1): immersive-cinema program - shown only when XR is available and
        // the VR-3D master toggle is on. Master-gated with no per-item toggle and not a window, so
        // newWindowActionFor/removeActionFor both leave it on their `else -> null` branch.
        if (gate.vrCinema) {
            popup.menu.add(
                0,
                MENU_ITEM_VR_CINEMA,
                MENU_ORDER_VR_CINEMA,
                R.string.vr_cinema_program_title,
            ).setIcon(R.drawable.ic_vr_headset)
        }
        // S0757: Quick Launch Panel - always present (no on/off; also reachable via tile/gesture/widget).
        popup.menu.add(
            0,
            MENU_ITEM_APP_LAUNCH_PANEL,
            MENU_ORDER_APP_LAUNCH_PANEL,
            R.string.app_launch_panel_title,
        ).setIcon(R.drawable.ic_view_grid)
        // S2673: the one call that draws a sub-program. Its order is the registry's own `order`, which
        // is what puts every surface on one sequence and what makes the accent pass below match.
        for (entry in visibleSubPrograms(gate)) {
            val route = InternalRouteCatalog.byKey(entry.routeKey) ?: continue
            popup.menu.add(
                0,
                MENU_ITEM_IDS.getValue(entry.routeKey),
                MainProgramsMenuOrder.menuOrderFor(entry),
                route.labelRes,
            ).setIcon(route.iconRes)
        }
        applyProgramAccents(popup)
        return popup.menu.size()
    }

    /**
     * S2510: colours each program's glyph with the accent that identifies it in every other list.
     *
     * Runs as one pass over the finished menu rather than at each `setIcon` call because the three
     * non-registry items are added around the loop. Items are matched by `order` through
     * [MainProgramsMenuOrder], which owns the offset both sides of the join must apply - see its KDoc for
     * the S2673 incident, and S2889 for why the arithmetic no longer lives here.
     *
     * Tints the MenuItem, never the drawable: `setIcon` hands out a drawable whose constant state is
     * shared with every other user of that vector, so tinting it here would recolour it app-wide.
     *
     * An item with no catalog entry keeps its current appearance - VR Cinema is deliberately outside
     * the registry, and the non-program items (new window, remove) are not sub-programs at all.
     */
    private fun applyProgramAccents(popup: PopupMenu) {
        for (index in 0 until popup.menu.size()) {
            val item = popup.menu.getItem(index)
            val accentRes = MainProgramsMenuOrder.entryForMenuOrder(item.order)
                ?.let { SubProgramAccentCatalog.accentFor(it.routeKey) }
                ?: continue
            MenuItemCompat.setIconTintList(
                item,
                ColorStateList.valueOf(ContextCompat.getColor(activity, accentRes)),
            )
        }
    }

    /** S0755: shared click routing for both the dropdown popup and the programs panel buttons. */
    fun handleMenuItem(itemId: Int): Boolean =
        handledByManager(itemId) ||
            launchIntentFor(itemId)?.also { activity.startActivity(it) } != null ||
            handledAsVrCinema(itemId)

    /** The seven per-program managers, each of which acts inside this window rather than opening one. */
    private fun handledByManager(itemId: Int): Boolean =
        miniGameMenuManager.handleMenuItem(itemId) ||
            wearCompanionMenuManager.handleMenuItem(itemId) ||
            streamsMenuManager.handleMenuItem(itemId) ||
            quickCaptureMenuManager.handleMenuItem(itemId) ||
            linkDownloadMenuManager.handleMenuItem(itemId) ||
            screenRecordingMenuManager.handleMenuItem(itemId) ||
            broadcastMenuManager.handleMenuItem(itemId)

    /**
     * S0962 (VR Cinema, Pillar 1): the one program that opens no Activity of its own - it prompts for a
     * resource first, so it cannot be expressed as an intent in [launchIntentFor].
     */
    private fun handledAsVrCinema(itemId: Int): Boolean {
        if (itemId != MENU_ITEM_VR_CINEMA) return false
        hostActions.onVrCinemaSelected()
        return true
    }

    /**
     * The Activity a menu item opens, or null when the item is not one of the plain launches.
     *
     * Split from [handleMenuItem] to keep both under detekt's cyclomatic ceiling, which the three
     * entries S2673 added pushed the single function past.
     */
    private fun launchIntentFor(itemId: Int): Intent? = when (itemId) {
        MENU_ITEM_CALCULATOR -> CalculatorActivity.createIntent(activity)
        MENU_ITEM_NETWORK_MONITOR -> NetworkMonitorActivity.createIntent(activity)
        MENU_ITEM_CAMERA_OCR ->
            com.sza.fastmediasorter.ui.cameraocr.CameraOcrTranslateActivity.createIntent(activity)
        MENU_ITEM_APP_LAUNCH_PANEL -> Intent(activity, AppLaunchPanelActivity::class.java)
        MENU_ITEM_SYSTEM_INFO -> SystemInfoActivity.createIntent(activity)
        MENU_ITEM_STOPWATCH -> StopwatchActivity.createIntent(activity)
        MENU_ITEM_FRONT_FLASHLIGHT -> AppLaunchPanelRouteIntents.frontFlashlight(activity)
        MENU_ITEM_WATER_FLASHLIGHT -> AppLaunchPanelRouteIntents.waterFlashlight(activity)
        // S2673: the three registry entries the hand-written menu never drew.
        MENU_ITEM_PHYSICAL_FLASHLIGHT -> AppLaunchPanelRouteIntents.physicalFlashlight(activity)
        MENU_ITEM_MIRROR -> AppLaunchPanelRouteIntents.mirror(activity)
        // S3216: manager-less like the two watch-listen rows above, so its launch lives here.
        MENU_ITEM_SOS -> AppLaunchPanelRouteIntents.sos(activity)
        MENU_ITEM_BLACK_SCREEN -> AppLaunchPanelRouteIntents.blackScreen(activity)
        // S2881: the two watch-listen programs are manager-less, so their launch lives here beside
        // the other registry rows - found on device, where a row without a branch here tapped dead.
        MENU_ITEM_WATCH_LISTEN ->
            WatchListenLaunchActivity.createIntent(activity, record = false)
        MENU_ITEM_WATCH_LISTEN_RECORD ->
            WatchListenLaunchActivity.createIntent(activity, record = true)
        MENU_ITEM_TOURIST ->
            com.sza.fastmediasorter.ui.tourist.TouristInfoActivity.createIntent(activity)
        else -> null
    }

    /**
     * S0770: "Open in new window" action for a programs-panel item, or null when multi-window is off or
     * the item is not a standalone window (quick capture / link download act in-place, not as a window).
     *
     * S2673 left this list hand-written on purpose: a route's intent says how to open a program, never
     * whether that program is a window, so the registry cannot answer this question.
     */
    fun newWindowActionFor(itemId: Int): (() -> Unit)? {
        if (!hostActions.isNewWindowAvailable()) return null
        val intent = when (itemId) {
            MainStreamsMenuManager.MENU_ITEM_STREAMS -> Intent(activity, StreamsActivity::class.java)
            MENU_ITEM_APP_LAUNCH_PANEL -> Intent(activity, AppLaunchPanelActivity::class.java)
            MENU_ITEM_CALCULATOR -> CalculatorActivity.createIntent(activity)
            MENU_ITEM_STOPWATCH -> StopwatchActivity.createIntent(activity)
            MENU_ITEM_NETWORK_MONITOR -> NetworkMonitorActivity.createIntent(activity)
            MENU_ITEM_CAMERA_OCR ->
                com.sza.fastmediasorter.ui.cameraocr.CameraOcrTranslateActivity.createIntent(activity)
            MENU_ITEM_SYSTEM_INFO -> SystemInfoActivity.createIntent(activity)
            MainMiniGameMenuManager.MENU_ITEM_GAME ->
                com.sza.fastmediasorter.core.game.GameLaunchIntents.game(activity)
            MainWearCompanionMenuManager.MENU_ITEM_WEAR_COMPANION ->
                WearCompanionActivity.createIntent(activity)
            MENU_ITEM_TOURIST ->
                com.sza.fastmediasorter.ui.tourist.TouristInfoActivity.createIntent(activity)
            else -> null
        }
        return intent?.let { resolved -> { hostActions.launchInNewWindow(resolved) } }
    }

    /**
     * S0770: "Remove" action for a programs-panel item = turn off its existing settings toggle, or null
     * when the item has no per-item toggle (Streams master feature + Quick Launch Panel stay un-removable).
     *
     * S2673: the toggle is the registry entry's own `disable`, so an entry declaring none - the
     * physical flashlight and the black screen - is simply not removable, with no branch saying so.
     */
    fun removeActionFor(itemId: Int): (() -> Unit)? =
        removeTargetFor(itemId)?.let { target ->
            {
                hostActions.confirmRemoveProgram(target.titleRes, target.disable)
            }
        }

    companion object {

        const val MENU_ITEM_CALCULATOR = 1
        const val MENU_ITEM_NETWORK_MONITOR = 18
        const val MENU_ITEM_CAMERA_OCR = 9
        const val MENU_ITEM_APP_LAUNCH_PANEL = 15
        const val MENU_ITEM_VR_CINEMA = 17

        // S1733: 19 is the first free id - 1, 2, 9, 10, 12-18 are taken across six manager classes, and a
        // collision would route one program's tap into another's branch.
        const val MENU_ITEM_SYSTEM_INFO = 19

        // S2212: front flashlight item id
        const val MENU_ITEM_FRONT_FLASHLIGHT = 21

        // S2516: water flashlight item id
        const val MENU_ITEM_WATER_FLASHLIGHT = 22

        // S1411: 23 is the first free id - 20 belongs to the Wear companion manager, 21 and 22 to the
        // two flashlights above, and a collision would route one program's tap into another's branch.
        const val MENU_ITEM_STOPWATCH = 23

        // S2673: 25-27 are the next free ids after the broadcast manager's 24 - the three registry
        // entries the hand-written menu never drew.
        const val MENU_ITEM_PHYSICAL_FLASHLIGHT = 25
        const val MENU_ITEM_MIRROR = 26
        const val MENU_ITEM_BLACK_SCREEN = 27

        // S2881: the two watch-listen programs, coordinator-dispatched like the stopwatch - they have
        // no manager of their own, so the id lives here and the generic route dispatch handles it.
        const val MENU_ITEM_WATCH_LISTEN = 28
        const val MENU_ITEM_WATCH_LISTEN_RECORD = 29
        const val MENU_ITEM_TOURIST = 30

        // S3216: 31 is the next free id. The distress signal has no manager of its own, so its launch
        // sits in launchIntentFor beside the other registry rows - a row without a branch there taps
        // dead, which is the S2881 finding.
        const val MENU_ITEM_SOS = 31

        /**
         * The menu item id each sub-program's tap is dispatched on - the one thing ADR-1 keeps out of
         * the registry that this file still has to own.
         *
         * S1736 phase 04 removed the label and the icon from this table: both now come from
         * [InternalRouteCatalog], so the menu cannot word or draw a program differently from every
         * other surface. The id cannot follow them. Seven manager classes own the id they dispatch on
         * and the programs panel raises the same ids as buttons, so an id derived from a list position
         * would change under every registry insertion and route one program's tap into another's branch
         * - the failure the free-id comments above exist to prevent.
         */
        private val MENU_ITEM_IDS: Map<String, Int> = mapOf(
            InternalRouteCatalog.KEY_QUICK_CAMERA to MainQuickCaptureMenuManager.MENU_ITEM_QUICK_CAMERA,
            InternalRouteCatalog.KEY_QUICK_VOICE to MainQuickCaptureMenuManager.MENU_ITEM_QUICK_VOICE,
            InternalRouteCatalog.KEY_CALCULATOR to MENU_ITEM_CALCULATOR,
            InternalRouteCatalog.KEY_NETWORK_MONITOR to MENU_ITEM_NETWORK_MONITOR,
            InternalRouteCatalog.KEY_OCR to MENU_ITEM_CAMERA_OCR,
            InternalRouteCatalog.KEY_SCREEN_RECORDING to MainScreenRecordingMenuManager.MENU_ITEM_SCREEN_RECORDING,
            InternalRouteCatalog.KEY_LINK_DOWNLOAD to MainLinkDownloadMenuManager.MENU_ITEM_LINK_DOWNLOAD,
            InternalRouteCatalog.KEY_GAME to MainMiniGameMenuManager.MENU_ITEM_GAME,
            InternalRouteCatalog.KEY_SYSTEM_INFO to MENU_ITEM_SYSTEM_INFO,
            InternalRouteCatalog.KEY_WEAR_COMPANION to MainWearCompanionMenuManager.MENU_ITEM_WEAR_COMPANION,
            InternalRouteCatalog.KEY_WATCH_LISTEN to MENU_ITEM_WATCH_LISTEN,
            InternalRouteCatalog.KEY_WATCH_LISTEN_RECORD to MENU_ITEM_WATCH_LISTEN_RECORD,
            InternalRouteCatalog.KEY_FRONT_FLASHLIGHT to MENU_ITEM_FRONT_FLASHLIGHT,
            InternalRouteCatalog.KEY_PHYSICAL_FLASHLIGHT to MENU_ITEM_PHYSICAL_FLASHLIGHT,
            InternalRouteCatalog.KEY_WATER_FLASHLIGHT to MENU_ITEM_WATER_FLASHLIGHT,
            InternalRouteCatalog.KEY_SOS to MENU_ITEM_SOS,
            InternalRouteCatalog.KEY_MIRROR to MENU_ITEM_MIRROR,
            InternalRouteCatalog.KEY_BLACK_SCREEN to MENU_ITEM_BLACK_SCREEN,
            InternalRouteCatalog.KEY_STOPWATCH to MENU_ITEM_STOPWATCH,
            InternalRouteCatalog.KEY_TOURIST_INFO to MENU_ITEM_TOURIST,
            InternalRouteCatalog.KEY_BROADCAST to MainBroadcastMenuManager.MENU_ITEM_BROADCAST,
        )

        /** The route keys the menu can draw - read by SubProgramCatalogCompletenessTest. */
        val PRESENTABLE_ROUTE_KEYS: Set<String> get() = MENU_ITEM_IDS.keys

        /**
         * The registry's programs-menu entries this build can draw and this settings state allows.
         *
         * An entry with no item id, or with no route behind it, is skipped rather than crashing the
         * menu; the completeness test is what refuses to let such an entry exist in the first place.
         */
        fun visibleSubPrograms(gate: ProgramsMenuGate): List<SubProgramEntry> =
            SubProgramCatalog.forSurface(SubProgramSurface.PROGRAMS_MENU)
                .filter { it.routeKey in MENU_ITEM_IDS && InternalRouteCatalog.byKey(it.routeKey) != null }
                .filter { gate.isSubProgramAvailable(it.routeKey) }
                // The route chain answers "streams available", which a flavor without a broadcast source also has.
                .filter { it.routeKey != InternalRouteCatalog.KEY_BROADCAST || gate.broadcast }

        /** The menu item id [routeKey] is drawn and dispatched under, or null when it is not drawn. */
        fun menuItemIdFor(routeKey: String): Int? = MENU_ITEM_IDS[routeKey]

        /** The route a menu item id belongs to, or null when the item is not a sub-program. */
        fun routeKeyForItemId(itemId: Int): String? =
            MENU_ITEM_IDS.entries.firstOrNull { it.value == itemId }?.key

        /**
         * The title and the off-switch a "Remove" tap on [itemId] acts on, or null when the item is not
         * a removable sub-program - the physical flashlight and the black screen declare no `disable`.
         */
        fun removeTargetFor(itemId: Int): RemoveTarget? {
            val routeKey = routeKeyForItemId(itemId) ?: return null
            val disable = SubProgramCatalog.byRouteKey(routeKey)?.disable
            val titleRes = InternalRouteCatalog.byKey(routeKey)?.labelRes
            return if (disable != null && titleRes != null) RemoveTarget(titleRes, disable) else null
        }

        // S2673: the three non-registry items sort outside the registry's own band, so the sequence
        // the owner sees is unchanged while every sub-program carries its own registry order.
        // A menu order carries an Android category in its high 16 bits, so a negative value makes
        // MenuBuilder.add throw. The fixed items therefore sit below the registry's own orders, which
        // MainProgramsMenuOrder shifts.
        private const val MENU_ORDER_STREAMS = 10
        private const val MENU_ORDER_VR_CINEMA = 20
        private const val MENU_ORDER_APP_LAUNCH_PANEL = 30
    }
}
