package com.sza.fastmediasorter.ui.launcher.helpers

import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
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
import com.sza.fastmediasorter.ui.launcher.LauncherHomeViewModel
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadgetRegistry
import com.sza.fastmediasorter.ui.launcher.picker.LauncherCellContentPickerDialogFragment
import com.sza.fastmediasorter.ui.launcher.picker.LauncherResourceModePickerDialogFragment
import com.sza.fastmediasorter.ui.launcher.picker.LauncherScheduledOpPickerDialogFragment
import com.sza.fastmediasorter.ui.launcher.picker.LauncherSectionNameDialogFragment
import com.sza.fastmediasorter.ui.launcher.picker.LauncherStreamPickerDialogFragment
import com.sza.fastmediasorter.ui.launcher.picker.LauncherWeatherLocationDialogFragment
import timber.log.Timber

/**
 * S1541: the whole "put something on the desktop" chain - result-key registration, the category
 * dispatch, every picker launch, and the single write that places the chosen item.
 *
 * The target square lives here because it is written when a picker opens and read when its result
 * comes back: splitting the two halves across classes is what would let a returning picker land on
 * the wrong cell.
 */
class LauncherAddFlowManager(
    private val fragmentManager: FragmentManager,
    private val lifecycleOwner: LifecycleOwner,
    private val viewModel: LauncherHomeViewModel,
    private val gadgetRegistry: LauncherGadgetRegistry,
    private val contactPickManager: LauncherContactPickManager,
    private val sensorPermissionManager: LauncherSensorPermissionManager,
    private val currentColumns: () -> Int,
    private val onCreateResource: () -> Unit,
) {

    // S1209: [NO_SLOT] in either coordinate means the flow started from the taskbar "+", where the user
    // pointed at no square and the repository picks the position.
    private var pendingRow: Int = 0
    private var pendingCol: Int = 0
    private var pendingGadgetKey: String? = null

    /**
     * Wires the "put something on the desktop" chain. Each picker returns on its own key and dismisses
     * before the next opens, so the flow is one dialog at a time. Only the coordinate routing lives here;
     * the placement (and the ADR-10 rememberFileList write) is [LauncherHomeViewModel]'s job (Rule 3).
     */
    fun registerAddFlowListeners() {
        Timber.d("S1541: add-flow manager registering result listeners")
        fragmentManager.setFragmentResultListener(
            LauncherCellContentPickerDialogFragment.RESULT_KEY,
            lifecycleOwner,
        ) { _, bundle ->
            pendingRow = bundle.getInt(LauncherCellContentPickerDialogFragment.RESULT_ROW)
            pendingCol = bundle.getInt(LauncherCellContentPickerDialogFragment.RESULT_COL)
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
            // still pendingRow/pendingCol, like every other kind.
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
            openPicker(
                LauncherCellContentPickerDialogFragment.newActionInstance(pendingRow, pendingCol),
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
            openPicker(
                LauncherCellContentPickerDialogFragment.newSectionInstance(pendingRow, pendingCol),
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
            openPicker(
                LauncherCellContentPickerDialogFragment.newGadgetInstance(pendingRow, pendingCol),
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

            gadget.requiresResourceParam -> {
                pendingGadgetKey = gadgetKey
                val filter = if (gadgetKey == LauncherGadgetRegistry.KEY_PLAYLIST) MediaType.AUDIO else null
                openPicker(
                    ResourcePickerDialogFragment.newInstance(REQ_RESOURCE_GADGET, filter),
                    ResourcePickerDialogFragment.TAG,
                )
            }

            else -> placeGadget(gadgetKey, resourceId = null)
        }
    }

    private fun placeGadget(gadgetKey: String, resourceId: Long?) {
        sensorPermissionManager.placeAfterAsking(gadgetKey) { placeGadgetNow(gadgetKey, resourceId) }
    }

    private fun placeGadgetNow(gadgetKey: String, resourceId: Long?) {
        val gadget = gadgetRegistry.byKey(gadgetKey) ?: return
        placeAtPendingSlot(
            kind = LauncherCellKind.GADGET,
            target = gadgetRegistry.encodeTarget(gadgetKey, resourceId?.toString()),
            spanW = gadget.defaultSpanW,
            spanH = gadget.defaultSpanH,
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
        if (pendingRow == NO_SLOT) {
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
                rowIndex = pendingRow,
                colIndex = pendingCol,
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
                onCreateResource()
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
            placeGadget(pendingGadgetKey ?: return@setFragmentResultListener, resourceId)
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

        /**
         * S1209: "no square was pointed at" travelling through the picker's row/col arguments. A
         * sentinel rather than a parallel boolean field, because the picker round-trips the coordinates
         * through its result bundle and a separate flag could desync from them - a slotless flow that
         * came back carrying row 0 would silently overwrite the top-left square.
         */
        const val NO_SLOT = -1
    }
}
