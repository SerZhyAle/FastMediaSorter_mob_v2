package com.sza.fastmediasorter.ui.launcher.helpers

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.launcher.LauncherSectionCatalog
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellDraft
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactAction
import com.sza.fastmediasorter.domain.model.launcher.LauncherResourceMode
import com.sza.fastmediasorter.domain.model.launcher.LauncherSectionMembership
import com.sza.fastmediasorter.ui.applaunchpanel.edit.AppPickerDialogFragment
import com.sza.fastmediasorter.ui.applaunchpanel.edit.InternalRoutePickerDialogFragment
import com.sza.fastmediasorter.ui.applaunchpanel.edit.OsShortcutPickerDialogFragment
import com.sza.fastmediasorter.ui.applaunchpanel.edit.ResourcePickerDialogFragment
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog
import com.sza.fastmediasorter.ui.launcher.LauncherHomeViewModel
import com.sza.fastmediasorter.ui.launcher.gadget.ConfigurableWidgetCatalog
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadgetRegistry
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherTimeZoneCatalog
import com.sza.fastmediasorter.ui.launcher.gadget.NetworkIndicatorGadget.Companion.PARAM_SEPARATOR
import com.sza.fastmediasorter.ui.launcher.gadget.StreamWindow
import com.sza.fastmediasorter.ui.launcher.picker.LauncherCellContentPickerDialogFragment
import com.sza.fastmediasorter.ui.launcher.picker.LauncherNetworkIndicatorDialogFragment
import com.sza.fastmediasorter.ui.launcher.picker.LauncherResourceModePickerDialogFragment
import com.sza.fastmediasorter.ui.launcher.picker.LauncherScheduledOpPickerDialogFragment
import com.sza.fastmediasorter.ui.launcher.picker.LauncherSectionNameDialogFragment
import com.sza.fastmediasorter.ui.launcher.picker.LauncherStreamPickerDialogFragment
import com.sza.fastmediasorter.ui.launcher.picker.LauncherWeatherLocationDialogFragment
import com.sza.fastmediasorter.widget.LauncherWidgetToken
import com.sza.fastmediasorter.widget.networkmonitor.NetworkMonitorIndicator
import timber.log.Timber

/**
 * S1541: the whole "put something on the desktop" chain - result-key registration, the category
 * dispatch, every picker launch, and the single write that places the chosen item.
 *
 * S2060: the target square itself lives in [LauncherHomeViewModel.pendingSlot], not on this class -
 * this manager is recreated by the Activity's `by lazy` on every process restart, so a field here
 * would lose the pointed-at square whenever the OS killed the process mid-flow (a system contact/app/
 * resource picker running between the write and the read). The chain-routing state below this KDoc
 * (which gadget, which indicator, which world-clock cell) still lives here on purpose - see §1
 * Non-goal in S2060 for why that risk is not this ticket's scope.
 */
class LauncherAddFlowManager(
    // S1906: the shared searchable picker takes its title as a String, so whoever opens it supplies the
    // resolved text - every other picker here is a fragment that reads its own strings.
    private val context: Context,
    private val fragmentManager: FragmentManager,
    private val lifecycleOwner: LifecycleOwner,
    private val viewModel: LauncherHomeViewModel,
    private val gadgetRegistry: LauncherGadgetRegistry,
    private val contactPickManager: LauncherContactPickManager,
    private val sensorPermissionManager: LauncherSensorPermissionManager,
    private val currentColumns: () -> Int,
    private val hostActions: LauncherAddFlowHostActions,
) {

    // S1209: [NO_SLOT] in either coordinate means the flow started from the taskbar "+", where the user
    // pointed at no square and the repository picks the position.
    // S2060: the coordinate pair itself lives in viewModel.pendingSlot, not here - this manager is
    // recreated by the Activity's `by lazy` on every process restart, so a plain field here would lose
    // the pointed-at square exactly like it used to.
    private var pendingGadgetKey: String? = null

    // S1440: held only while the resource picker is up - reachability is the one indicator whose param
    // takes two dialogs to answer.
    private var pendingIndicatorKey: String? = null

    // S1906: which world-clock cell is being repointed, or null when the pick will place a new one. The
    // shared searchable picker returns only the chosen option and carries nothing alongside it, so the
    // other half of a repoint is remembered here (strategic ADR-3).
    private var pendingZoneCellId: Long? = null

    /**
     * Wires the "put something on the desktop" chain. Each picker returns on its own key and dismisses
     * before the next opens, so the flow is one dialog at a time. Only the coordinate routing lives here;
     * the placement (and the ADR-10 rememberFileList write) is [LauncherHomeViewModel]'s job (Rule 3).
     */
    fun registerAddFlowListeners() {
        fragmentManager.setFragmentResultListener(
            LauncherCellContentPickerDialogFragment.RESULT_KEY,
            lifecycleOwner,
        ) { _, bundle ->
            viewModel.pendingSlot = bundle.getInt(LauncherCellContentPickerDialogFragment.RESULT_ROW) to
                bundle.getInt(LauncherCellContentPickerDialogFragment.RESULT_COL)
            openPickerForCategory(bundle)
        }
        fragmentManager.setFragmentResultListener(REQ_APP, lifecycleOwner) { _, bundle ->
            val pkg = bundle.getString(AppPickerDialogFragment.RESULT_PACKAGE) ?: return@setFragmentResultListener
            addShortcut(LauncherCellCommand.App(pkg))
        }
        fragmentManager.setFragmentResultListener(REQ_FEATURE, lifecycleOwner) { _, bundle ->
            val routeKey = bundle.getString(InternalRoutePickerDialogFragment.RESULT_ROUTE_KEY)
                ?: return@setFragmentResultListener
            addShortcut(LauncherCellCommand.Feature(routeKey))
        }
        fragmentManager.setFragmentResultListener(REQ_OS, lifecycleOwner) { _, bundle ->
            val targetKey = bundle.getString(OsShortcutPickerDialogFragment.RESULT_TARGET_KEY)
                ?: return@setFragmentResultListener
            addShortcut(LauncherCellCommand.OsShortcut(targetKey))
        }
        fragmentManager.setFragmentResultListener(
            LauncherStreamPickerDialogFragment.RESULT_KEY,
            lifecycleOwner,
        ) { _, bundle ->
            val identityKey = bundle.getString(LauncherStreamPickerDialogFragment.RESULT_STREAM_IDENTITY)
                ?: return@setFragmentResultListener
            addShortcut(LauncherCellCommand.Stream(identityKey))
        }
        // S2031: the same picker on a second key - this answer binds a window cell to a channel, and an
        // answer that arrived on the shortcut key above would place a 1x1 shortcut instead. The kind
        // rides back with the identity, so the footprint is decided here without a second catalog read.
        fragmentManager.setFragmentResultListener(REQ_STREAM_WINDOW, lifecycleOwner) { _, bundle ->
            val identityKey = bundle.getString(LauncherStreamPickerDialogFragment.RESULT_STREAM_IDENTITY)
                ?: return@setFragmentResultListener
            val mediaKind = bundle.getString(LauncherStreamPickerDialogFragment.RESULT_STREAM_MEDIA_KIND)
                .orEmpty()
            val (spanW, spanH) = StreamWindow.spanFor(mediaKind)
            Timber.d("S2031: place stream window kind=$mediaKind span=${spanW}x$spanH")
            placeGadget(
                gadgetKey = LauncherGadgetRegistry.KEY_STREAM_WINDOW,
                param = identityKey,
                resourceId = null,
                spanW = spanW,
                spanH = spanH,
            )
        }
        fragmentManager.setFragmentResultListener(
            LauncherScheduledOpPickerDialogFragment.RESULT_KEY,
            lifecycleOwner,
        ) { _, bundle ->
            val operationId = bundle.getLong(LauncherScheduledOpPickerDialogFragment.RESULT_OPERATION_ID, -1L)
            if (operationId <= 0L) return@setFragmentResultListener
            addShortcut(LauncherCellCommand.ScheduledOp(operationId))
        }
        registerResourceListeners()
        registerWeatherLocationListener()
        registerWorldClockZoneListener()
        registerNetworkIndicatorListeners()
        registerSectionNameListener()
        // Taskbar pin flow is separate from the desktop add-flow: no grid coordinate, its own key so an
        // app pinned to the bar is never mistaken for an app dropped on a cell (both share the picker).
        fragmentManager.setFragmentResultListener(REQ_PIN_APP, lifecycleOwner) { _, bundle ->
            val pkg = bundle.getString(AppPickerDialogFragment.RESULT_PACKAGE) ?: return@setFragmentResultListener
            viewModel.addPin(LauncherCellCommand.App(pkg))
        }
    }

    /**
     * Routes the chosen content category to the picker that resolves it - the thirteen-way branch that
     * used to sit inside [registerAddFlowListeners] and carried that whole function past detekt's
     * complexity ceiling on its own. Splitting it also keeps the registration function readable as what
     * it is: a list of result keys.
     */
    private fun openPickerForCategory(bundle: Bundle) {
        when (bundle.getString(LauncherCellContentPickerDialogFragment.RESULT_CATEGORY)) {
            LauncherCellContentPickerDialogFragment.CATEGORY_APP ->
                openPicker(AppPickerDialogFragment.newInstance(REQ_APP), AppPickerDialogFragment.TAG)
            LauncherCellContentPickerDialogFragment.CATEGORY_FEATURE -> openPicker(
                InternalRoutePickerDialogFragment.newInstance(REQ_FEATURE),
                InternalRoutePickerDialogFragment.TAG,
            )
            LauncherCellContentPickerDialogFragment.CATEGORY_RESOURCE -> openPicker(
                // S1423: only this picker offers "Create new.." - it is the placement step, so a
                // resource created here is exactly what the user wanted on the desktop.
                ResourcePickerDialogFragment.newInstance(REQ_RESOURCE_SHORTCUT, allowCreateNew = true),
                ResourcePickerDialogFragment.TAG,
            )
            LauncherCellContentPickerDialogFragment.CATEGORY_STREAM ->
                viewModel.requestStreamCell()
            LauncherCellContentPickerDialogFragment.CATEGORY_OS ->
                openPicker(OsShortcutPickerDialogFragment.newInstance(REQ_OS), OsShortcutPickerDialogFragment.TAG)
            LauncherCellContentPickerDialogFragment.CATEGORY_SCHEDULED_OP -> openPicker(
                LauncherScheduledOpPickerDialogFragment.newInstance(),
                LauncherScheduledOpPickerDialogFragment.TAG,
            )
            // The manager owns the pick-contact / choose-channel chain; the cell it lands on is
            // still viewModel.pendingSlot, like every other kind.
            LauncherCellContentPickerDialogFragment.CATEGORY_CONTACT_PROFILE ->
                contactPickManager.start(LauncherContactAction.PROFILE)
            LauncherCellContentPickerDialogFragment.CATEGORY_CONTACT_DIAL ->
                contactPickManager.start(LauncherContactAction.DIAL)
            LauncherCellContentPickerDialogFragment.CATEGORY_CONTACT_SMS ->
                contactPickManager.start(LauncherContactAction.SMS)
            LauncherCellContentPickerDialogFragment.CATEGORY_CONTACT_MESSAGE ->
                contactPickManager.start(LauncherContactAction.MESSAGE)
            LauncherCellContentPickerDialogFragment.CATEGORY_GADGET ->
                onGadgetChosen(bundle.getString(LauncherCellContentPickerDialogFragment.RESULT_GADGET_KEY))
            LauncherCellContentPickerDialogFragment.CATEGORY_ACTION ->
                onLauncherActionChosen(bundle.getString(LauncherCellContentPickerDialogFragment.RESULT_ACTION_KEY))
            LauncherCellContentPickerDialogFragment.CATEGORY_SECTION ->
                onSectionChosen(bundle.getString(LauncherCellContentPickerDialogFragment.RESULT_SECTION_KEY))
            // S1742: not a section key - the user asked to make one, so the name comes first.
            LauncherCellContentPickerDialogFragment.SECTION_CREATE_ID -> askSectionName()
        }
    }

    /**
     * Entry point for both add flows (edit mode only): an empty-slot tap passes that square, the taskbar
     * "+" passes [NO_SLOT] for both coordinates and lets the repository choose the position.
     */
    fun openContentPicker(row: Int, col: Int) {
        openPicker(
            LauncherCellContentPickerDialogFragment.newInstance(row, col),
            LauncherCellContentPickerDialogFragment.TAG,
        )
    }

    /** Edit-mode taskbar "+": pick an app to pin. Routed on its own key so it is not an add-cell pick. */
    fun openPinAppPicker() {
        openPicker(AppPickerDialogFragment.newInstance(REQ_PIN_APP), AppPickerDialogFragment.TAG)
    }

    /** The stream list is fetched asynchronously, so the view model asks for this picker by event. */
    fun openStreamPicker() {
        openPicker(LauncherStreamPickerDialogFragment.newInstance(), LauncherStreamPickerDialogFragment.TAG)
    }

    /** Re-points an existing weather cell; the gadget renderer has no picker chain of its own. */
    fun openWeatherLocationPicker(cellId: Long) {
        openPicker(
            LauncherWeatherLocationDialogFragment.newInstance(REQ_WEATHER_LOCATION, cellId),
            LauncherWeatherLocationDialogFragment.TAG,
        )
    }

    /** S1906: re-points an existing world-clock cell, for the same reason the weather one is here. */
    fun openWorldClockZonePicker(cellId: Long) {
        pendingZoneCellId = cellId
        openZonePicker()
    }

    private fun openZonePicker() {
        openPicker(
            SearchableOptionPickerDialog.newInstance(
                title = context.getString(R.string.launcher_world_clock_zone_title),
                options = LauncherTimeZoneCatalog.options(),
                selectedId = null,
                includeResetRow = false,
                requestKey = REQ_WORLD_CLOCK_ZONE,
            ),
            SearchableOptionPickerDialog.TAG,
        )
    }

    private fun openPicker(fragment: DialogFragment, tag: String) {
        // A dialog left up on a rebind must not be duplicated by a second tap.
        if (fragmentManager.findFragmentByTag(tag) != null) return
        fragment.show(fragmentManager, tag)
    }

    /**
     * S1402: two levels, exactly like the gadget flow - the category row opens the list of actions, and
     * the second pass carries the chosen key.
     */
    private fun onLauncherActionChosen(actionKey: String?) {
        if (actionKey == null) {
            val (row, col) = viewModel.pendingSlot
            openPicker(
                LauncherCellContentPickerDialogFragment.newActionInstance(row, col),
                LauncherCellContentPickerDialogFragment.TAG_ACTION,
            )
            return
        }
        addShortcut(LauncherCellCommand.LauncherAction(actionKey))
    }

    /**
     * S1428: two levels again, because two preset sections exist - the second pass says which one.
     *
     * The header goes down the ordinary placement route with its overlap check intact, at the one span it
     * is stored and drawn at (S1642) - the repository pins both that span and column 0 anyway, and passing
     * the same constant here keeps the request and the stored result describing the same rectangle.
     */
    /**
     * S1742: asks for the name before anything is written.
     *
     * A section the user creates has no preset label to fall back on - its name IS its identity on the
     * desktop (research 01 item 1) - so placing first and naming afterwards would put an unreadable
     * header on the desktop for as long as the user hesitated.
     */
    private fun askSectionName() {
        openPicker(
            LauncherSectionNameDialogFragment.newInstance(REQ_SECTION_NAME),
            LauncherSectionNameDialogFragment.TAG,
        )
    }

    /**
     * Mints the key, then places the header down the same route every other cell takes.
     *
     * The name is written as the cell's own caption rather than into the key: the key is a persistence
     * token that must never change, and renaming later (Phase 03) rewrites only the caption.
     */
    private fun onSectionNamed(name: String) {
        val key = LauncherSectionCatalog.mintUserKey(System.currentTimeMillis())
        placeAtPendingSlot(
            kind = LauncherCellKind.SECTION,
            target = LauncherCellCommand.Section(key).encode(),
            spanW = LauncherSectionMembership.HEADER_SPAN_W,
            spanH = 1,
            labelOverride = name,
        )
    }

    private fun onSectionChosen(sectionKey: String?) {
        if (sectionKey == null) {
            val (row, col) = viewModel.pendingSlot
            openPicker(
                LauncherCellContentPickerDialogFragment.newSectionInstance(row, col),
                LauncherCellContentPickerDialogFragment.TAG_SECTION,
            )
            return
        }
        placeAtPendingSlot(
            kind = LauncherCellKind.SECTION,
            target = LauncherCellCommand.Section(sectionKey).encode(),
            spanW = LauncherSectionMembership.HEADER_SPAN_W,
            spanH = 1,
        )
    }

    /**
     * "Gadget" was chosen. With no key yet, re-open the chooser on its gadget list (a distinct tag, so the
     * just-dismissed level-one dialog is not mistaken for it). With a key, place it - a resource gadget
     * first picks its resource (filtered to what it can hold), the rest go straight down.
     */
    private fun onGadgetChosen(gadgetKey: String?) {
        if (gadgetKey == null) {
            val (row, col) = viewModel.pendingSlot
            openPicker(
                LauncherCellContentPickerDialogFragment.newGadgetInstance(row, col),
                LauncherCellContentPickerDialogFragment.TAG_GADGET,
            )
            return
        }
        val gadget = gadgetRegistry.byKey(gadgetKey) ?: return
        when {
            // The weather gadget's param is a place, not a registered resource, so it has its own picker.
            gadgetKey == LauncherGadgetRegistry.KEY_WEATHER -> openPicker(
                LauncherWeatherLocationDialogFragment.newInstance(REQ_WEATHER_LOCATION),
                LauncherWeatherLocationDialogFragment.TAG,
            )

            // S2031: same shape once more - the stream window's param is a channel identity, so it asks
            // the channel picker on its own key and is placed when the kind has decided its footprint.
            gadgetKey == LauncherGadgetRegistry.KEY_STREAM_WINDOW -> openPicker(
                LauncherStreamPickerDialogFragment.newInstance(REQ_STREAM_WINDOW),
                LauncherStreamPickerDialogFragment.TAG,
            )

            // S1906: same shape once more - the world clock's param is a time zone, so it asks the zone
            // list on its own key. No pending cell id: this pick places a new cell.
            gadgetKey == LauncherGadgetRegistry.KEY_WORLD_CLOCK -> {
                pendingZoneCellId = null
                openZonePicker()
            }

            // S1440: same shape - the network cell's param is an indicator, not a registered resource.
            gadgetKey == LauncherGadgetRegistry.KEY_NETWORK_INDICATOR -> openPicker(
                LauncherNetworkIndicatorDialogFragment.newInstance(REQ_NETWORK_INDICATOR),
                LauncherNetworkIndicatorDialogFragment.TAG,
            )

            // S1930: same shape as the four above - the param is an instance token, not a registered
            // resource - but the question is asked by the widget's own configuration Activity rather
            // than by a dialog of ours, and the cell is placed only once that Activity says yes.
            ConfigurableWidgetCatalog.isConfigurable(gadgetKey) -> configureThenPlace(gadgetKey)

            gadget.requiresResourceParam -> {
                pendingGadgetKey = gadgetKey
                val filter = if (gadgetKey == LauncherGadgetRegistry.KEY_PLAYLIST) MediaType.AUDIO else null
                openPicker(
                    ResourcePickerDialogFragment.newInstance(REQ_RESOURCE_GADGET, filter),
                    ResourcePickerDialogFragment.TAG,
                )
            }

            else -> placeGadget(gadgetKey, param = null, resourceId = null)
        }
    }

    /**
     * S1930: mints the cell's instance, hands it to the widget's own configuration screen, and stops.
     * Nothing is placed yet - [onWidgetConfigured] finishes the flow, because a cell placed first and
     * configured second is a cell the user can abandon half-made.
     */
    private fun configureThenPlace(gadgetKey: String) {
        val token = LauncherWidgetToken.mint(context)
        Timber.d("S1930: configure %s with token %d", gadgetKey, token)
        val intent = ConfigurableWidgetCatalog.configIntent(context, gadgetKey, token) ?: return
        // In saved state, not a field here: the configuration screen is a separate Activity, which is
        // exactly when the OS may kill this one (S2060, S2099).
        viewModel.pendingConfiguredWidget = gadgetKey to token
        hostActions.startWidgetConfiguration(intent)
    }

    /**
     * S1930: the configuration screen returned. [configured] is its `RESULT_OK`, which every widget
     * config Activity here sets only after writing its instance.
     *
     * A cancelled configuration clears the instance rather than leaving it: the screens set
     * `RESULT_CANCELED` first and can still have written a partial one before the user backed out, and
     * with no cell placed nothing would ever point at it again.
     */
    fun onWidgetConfigured(configured: Boolean) {
        val (gadgetKey, token) = viewModel.pendingConfiguredWidget ?: return
        Timber.d("S1930: configured=%b for %s token %d", configured, gadgetKey, token)
        viewModel.pendingConfiguredWidget = null
        if (!configured) {
            ConfigurableWidgetCatalog.clearInstance(context, gadgetKey, token)
            return
        }
        placeGadget(gadgetKey, param = token.toString(), resourceId = null)
    }

    /**
     * S1440: [param] is whatever the gadget stores in its `target` - a resource id for most of them, an
     * indicator key for the network cell. [resourceId] stays separate because only a resource-backed
     * gadget has a file list to remember (ADR-10).
     *
     * S2031: [spanW] / [spanH] override the gadget's own footprint. One picker entry can produce cells of
     * two different sizes - a stream window is a square for a radio channel and a wide rectangle for a
     * video one - and which it is only becomes known after the second question is answered, long after
     * the gadget object was resolved.
     */
    private fun placeGadget(
        gadgetKey: String,
        param: String?,
        resourceId: Long?,
        spanW: Int? = null,
        spanH: Int? = null,
    ) {
        sensorPermissionManager.placeAfterAsking(gadgetKey) {
            placeGadgetNow(gadgetKey, param, resourceId, spanW, spanH)
        }
    }

    private fun placeGadgetNow(
        gadgetKey: String,
        param: String?,
        resourceId: Long?,
        spanW: Int? = null,
        spanH: Int? = null,
    ) {
        val gadget = gadgetRegistry.byKey(gadgetKey) ?: return
        placeAtPendingSlot(
            kind = LauncherCellKind.GADGET,
            target = gadgetRegistry.encodeTarget(gadgetKey, param),
            spanW = spanW ?: gadget.defaultSpanW,
            spanH = spanH ?: gadget.defaultSpanH,
            rememberFileListResourceId = resourceId,
        )
    }

    /**
     * S1209: the single write for every add flow, so the two entry points cannot drift apart. Which
     * repository operation runs is decided by whether a square was pointed at, and the column count goes
     * with the slotless one because the grid width belongs to the screen rendering the desktop, not to
     * the stored desktop (see `LauncherDesktopRepository.addCellInFirstFreeSlot`).
     */
    private fun placeAtPendingSlot(
        kind: LauncherCellKind,
        target: String,
        spanW: Int,
        spanH: Int,
        rememberFileListResourceId: Long? = null,
        labelOverride: String? = null,
    ) {
        val (row, col) = viewModel.pendingSlot
        if (row == NO_SLOT) {
            viewModel.addCellInFirstFreeSlot(
                columns = currentColumns(),
                kind = kind,
                target = target,
                spanW = spanW,
                spanH = spanH,
                rememberFileListResourceId = rememberFileListResourceId,
                labelOverride = labelOverride,
            )
        } else {
            viewModel.addCell(
                rowIndex = row,
                colIndex = col,
                draft = LauncherCellDraft(
                    kind = kind,
                    target = target,
                    spanW = spanW,
                    spanH = spanH,
                    rememberFileListResourceId = rememberFileListResourceId,
                    labelOverride = labelOverride,
                ),
                // S1772: the pointed-at path needs the grid width too - it is what decides whether a
                // footprint can ever be seated, and the width belongs to the screen, not to the desktop.
                columns = currentColumns(),
            )
        }
    }

    private fun registerSectionNameListener() {
        fragmentManager.setFragmentResultListener(REQ_SECTION_NAME, lifecycleOwner) { _, bundle ->
            bundle.getString(LauncherSectionNameDialogFragment.RESULT_NAME)
                ?.takeIf { it.isNotBlank() }
                ?.let { onSectionNamed(it) }
        }
    }

    /** The resource chain: pick a resource, then its mode for a shortcut, or hand it to a gadget. */
    private fun registerResourceListeners() {
        fragmentManager.setFragmentResultListener(REQ_RESOURCE_SHORTCUT, lifecycleOwner) { _, bundle ->
            // S1423: "Create new.." carries no resource id - the shortcut is pinned by the creation
            // flow itself, so there is no mode to pick and no cell to place here.
            if (bundle.getBoolean(ResourcePickerDialogFragment.RESULT_CREATE_NEW, false)) {
                hostActions.createResource()
                return@setFragmentResultListener
            }
            val resourceId = bundle.getLong(ResourcePickerDialogFragment.RESULT_RESOURCE_ID)
            openPicker(
                LauncherResourceModePickerDialogFragment.newInstance(resourceId),
                LauncherResourceModePickerDialogFragment.TAG,
            )
        }
        fragmentManager.setFragmentResultListener(
            LauncherResourceModePickerDialogFragment.RESULT_KEY,
            lifecycleOwner,
        ) { _, bundle ->
            val resourceId = bundle.getLong(LauncherResourceModePickerDialogFragment.RESULT_RESOURCE_ID)
            val modeName = bundle.getString(LauncherResourceModePickerDialogFragment.RESULT_MODE)
            val mode = LauncherResourceMode.entries.firstOrNull { it.name == modeName }
                ?: return@setFragmentResultListener
            addShortcut(LauncherCellCommand.Resource(resourceId, mode))
        }
        fragmentManager.setFragmentResultListener(REQ_RESOURCE_GADGET, lifecycleOwner) { _, bundle ->
            val resourceId = bundle.getLong(ResourcePickerDialogFragment.RESULT_RESOURCE_ID)
            val gadgetKey = pendingGadgetKey ?: return@setFragmentResultListener
            placeGadget(gadgetKey, resourceId.toString(), resourceId)
        }
    }

    /** One result key, two flows: a new cell has no id yet, an existing one is repointed in place. */
    private fun registerWeatherLocationListener() {
        fragmentManager.setFragmentResultListener(REQ_WEATHER_LOCATION, lifecycleOwner) { _, bundle ->
            val encoded = bundle.getString(LauncherWeatherLocationDialogFragment.RESULT_LOCATION)
                ?: return@setFragmentResultListener
            val cellId = bundle.getLong(
                LauncherWeatherLocationDialogFragment.RESULT_CELL_ID,
                LauncherWeatherLocationDialogFragment.NO_CELL_ID,
            )
            // S2213: above the branch on purpose - a first placement and a repoint are both the user
            // confirming a place, and one call covers both.
            viewModel.rememberWeatherLocation(encoded)
            if (cellId == LauncherWeatherLocationDialogFragment.NO_CELL_ID) {
                placeWeatherGadget(encoded)
            } else {
                viewModel.updateCellTarget(
                    cellId,
                    gadgetRegistry.encodeTarget(LauncherGadgetRegistry.KEY_WEATHER, encoded),
                )
            }
        }
    }

    /**
     * S1906: one result key, two flows, told apart by [pendingZoneCellId] - a new cell has no id yet, an
     * existing one is repointed in place.
     */
    private fun registerWorldClockZoneListener() {
        fragmentManager.setFragmentResultListener(REQ_WORLD_CLOCK_ZONE, lifecycleOwner) { _, bundle ->
            val zoneId = bundle.getString(SearchableOptionPickerDialog.RESULT_OPTION_ID)
            // Read and cleared together: a cell id left behind would swallow the next placement into
            // whichever cell was repointed last.
            val cellId = pendingZoneCellId
            pendingZoneCellId = null
            when {
                zoneId == null -> Unit
                cellId == null -> placeGadget(LauncherGadgetRegistry.KEY_WORLD_CLOCK, zoneId, null)
                else -> viewModel.updateCellTarget(
                    cellId,
                    gadgetRegistry.encodeTarget(LauncherGadgetRegistry.KEY_WORLD_CLOCK, zoneId),
                )
            }
        }
    }

    /**
     * S1440: reachability needs a resource on top of the indicator, so that one pick chains the shared
     * picker on its own request key; every other indicator places the cell outright. No remembered file
     * list either way - a reachability probe reads a resource's address, it never opens it.
     */
    private fun registerNetworkIndicatorListeners() {
        fragmentManager.setFragmentResultListener(REQ_NETWORK_INDICATOR, lifecycleOwner) { _, bundle ->
            val key = bundle.getString(LauncherNetworkIndicatorDialogFragment.RESULT_INDICATOR_KEY)
                ?: return@setFragmentResultListener
            if (key != NetworkMonitorIndicator.RESOURCE_REACHABILITY.key) {
                placeGadget(LauncherGadgetRegistry.KEY_NETWORK_INDICATOR, key, resourceId = null)
                return@setFragmentResultListener
            }
            pendingIndicatorKey = key
            val picker = ResourcePickerDialogFragment.newInstance(REQ_RESOURCE_INDICATOR)
            openPicker(picker, ResourcePickerDialogFragment.TAG)
        }
        fragmentManager.setFragmentResultListener(REQ_RESOURCE_INDICATOR, lifecycleOwner) { _, bundle ->
            val key = pendingIndicatorKey ?: return@setFragmentResultListener
            pendingIndicatorKey = null
            val id = bundle.getLong(ResourcePickerDialogFragment.RESULT_RESOURCE_ID)
            placeGadget(LauncherGadgetRegistry.KEY_NETWORK_INDICATOR, "$key$PARAM_SEPARATOR$id", null)
        }
    }

    /** The weather gadget carries its place in the target, so it bypasses [placeGadget]'s resource id. */
    private fun placeWeatherGadget(encodedLocation: String) {
        val gadget = gadgetRegistry.byKey(LauncherGadgetRegistry.KEY_WEATHER) ?: return
        placeAtPendingSlot(
            kind = LauncherCellKind.GADGET,
            target = gadgetRegistry.encodeTarget(LauncherGadgetRegistry.KEY_WEATHER, encodedLocation),
            spanW = gadget.defaultSpanW,
            spanH = gadget.defaultSpanH,
        )
    }

    /**
     * Public because the contact chain finishes outside this class: [LauncherContactPickManager] picks
     * the person and the channel, then hands back a command that lands on the same pending slot as
     * every other kind.
     */
    fun addShortcut(command: LauncherCellCommand) {
        // S2107: the far end of the contact chain. The slot is logged with it because pendingSlot
        // defaults to (0, 0) rather than to NO_SLOT, so a lost coordinate places the cell top-left
        // instead of nowhere - and from the tapped square that is indistinguishable from no cell at all.
        Timber.d("S2107: addShortcut kind=${command::class.simpleName} slot=${viewModel.pendingSlot}")
        placeAtPendingSlot(
            kind = LauncherCellKind.SHORTCUT,
            target = command.encode(),
            spanW = 1,
            spanH = 1,
        )
    }

    companion object {
        // Distinct result keys so the launcher's add-flow never collides with the panel editor's pickers
        // on the same FragmentManager, and so a resource pick for a shortcut is told apart from one for a
        // gadget (the former chains a mode picker; the latter completes the gadget).
        const val REQ_APP = "launcher_add_app"
        const val REQ_FEATURE = "launcher_add_feature"
        const val REQ_OS = "launcher_add_os"
        const val REQ_RESOURCE_SHORTCUT = "launcher_add_resource_shortcut"
        const val REQ_RESOURCE_GADGET = "launcher_add_resource_gadget"
        const val REQ_PIN_APP = "launcher_pin_app"
        const val REQ_WEATHER_LOCATION = "launcher_weather_location"
        const val REQ_SECTION_NAME = "launcher_section_name"

        // S1440: two keys - the network cell's second question reuses the shared resource picker, and a
        // pick answered on REQ_RESOURCE_GADGET would complete some other gadget instead.
        // S2031: the channel picker's second caller - a window cell bound to one channel, not a shortcut.
        const val REQ_STREAM_WINDOW = "launcher_add_stream_window"

        const val REQ_NETWORK_INDICATOR = "launcher_network_indicator"
        const val REQ_RESOURCE_INDICATOR = "launcher_add_resource_indicator"

        // S1906: its own key, because the shared searchable picker serves several hosts on one
        // FragmentManager and a zone answered on another host's key would complete the wrong cell.
        const val REQ_WORLD_CLOCK_ZONE = "launcher_world_clock_zone"

        /**
         * S1209: "no square was pointed at" travelling through the picker's row/col arguments. A
         * sentinel rather than a parallel boolean field, because the picker round-trips the coordinates
         * through its result bundle and a separate flag could desync from them - a slotless flow that
         * came back carrying row 0 would silently overwrite the top-left square.
         */
        const val NO_SLOT = -1
    }
}
