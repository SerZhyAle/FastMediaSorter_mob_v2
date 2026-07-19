package com.sza.fastmediasorter.ui.launcher

import android.content.res.Configuration
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.color.MaterialColors
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityLauncherHomeBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.model.launcher.LauncherResourceMode
import com.sza.fastmediasorter.ui.applaunchpanel.edit.AppPickerDialogFragment
import com.sza.fastmediasorter.ui.applaunchpanel.edit.InternalRoutePickerDialogFragment
import com.sza.fastmediasorter.ui.applaunchpanel.edit.OsShortcutPickerDialogFragment
import com.sza.fastmediasorter.ui.applaunchpanel.edit.ResourcePickerDialogFragment
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadgetHost
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadgetRegistry
import com.sza.fastmediasorter.ui.launcher.grid.LauncherCellViewBinder
import com.sza.fastmediasorter.ui.launcher.grid.LauncherGridGeometry
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherEditModeManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherTaskbarManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherTrayManager
import com.sza.fastmediasorter.ui.launcher.menu.LauncherStartMenuFragment
import com.sza.fastmediasorter.ui.launcher.picker.LauncherCellContentPickerDialogFragment
import com.sza.fastmediasorter.ui.launcher.picker.LauncherResourceModePickerDialogFragment
import com.sza.fastmediasorter.ui.launcher.picker.LauncherStreamPickerDialogFragment
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * S0404: the device home surface. Declared with a HOME intent-filter but shipped disabled - the
 * component is enabled only when the user turns launcher mode on (strategic ADR-2), so builds
 * without the mode behave exactly as before.
 *
 * Home is pressed constantly, so this screen stays cheap: it renders from a Room-backed Flow and
 * plain drawables, holds no player or image-loading target, and survives rotation by recomputing
 * the grid in place instead of being recreated.
 */
@AndroidEntryPoint
class LauncherHomeActivity : BaseActivity<ActivityLauncherHomeBinding>() {

    private val viewModel: LauncherHomeViewModel by viewModels()

    @Inject
    lateinit var gadgetRegistry: LauncherGadgetRegistry

    private val cellBinder = LauncherCellViewBinder(
        onCellClick = { viewModel.onCellTapped(it) },
        onEmptySlotClick = { row, col -> openContentPicker(row, col) },
        onRemoveClick = { viewModel.removeCell(it.cell.id) },
        // editModeManager is lateinit, but this lambda only fires on a long-press in edit mode - long
        // after setupViews() has created it.
        onCellDragStart = { view, cellUi -> editModeManager.startCellDrag(view, cellUi) },
    )

    private lateinit var taskbarManager: LauncherTaskbarManager

    private lateinit var editModeManager: LauncherEditModeManager

    /** Tracks the last orientation so the one-shot rotation hint fires only on an actual flip. */
    private var lastOrientation: LauncherOrientation = LauncherOrientation.PORTRAIT

    // The add-flow spans several dialogs that each dismiss before the next opens, and the shared panel
    // pickers carry no grid coordinate, so the target square (and, for a resource gadget, the gadget key)
    // is held here between steps. One flow runs at a time - the chooser is modal.
    private var pendingRow: Int = 0
    private var pendingCol: Int = 0
    private var pendingGadgetKey: String? = null

    /** Gadgets reach the desktop only through the one launch guard every other surface shares. */
    private val gadgetHost = object : LauncherGadgetHost {
        override fun run(command: LauncherCellCommand) = viewModel.run(command)
    }

    override fun getViewBinding(): ActivityLauncherHomeBinding =
        ActivityLauncherHomeBinding.inflate(layoutInflater)

    override fun setupViews() {
        // BaseActivity posts setupViews(), so the window's first insets dispatch has already
        // happened - applySystemBarInsetPadding registers the listener AND applies the current
        // insets immediately, which a bare listener would miss (Rule 17).
        binding.launcherRoot.applySystemBarInsetPadding()
        // A home screen has nowhere to go back to: Back must not finish the surface and expose
        // whatever sits behind it.
        onBackPressedDispatcher.addCallback(this) { }
        cellBinder.gadgetBinder = ::bindGadget
        applyGridGeometry()
        seedDesktopIfNeeded()

        LauncherTrayManager(this, binding.launcherTaskbar)
        taskbarManager = LauncherTaskbarManager(
            lifecycleOwner = this,
            binding = binding.launcherTaskbar,
            onCommand = { viewModel.run(it) },
            onStartClick = { showStartMenu() },
            onAddPin = { openPinAppPicker() },
            onRemovePin = { viewModel.removePin(it) },
        )
        editModeManager = LauncherEditModeManager(
            lifecycleOwner = this,
            desktop = binding.launcherDesktop,
            doneButton = binding.launcherEditDone,
            snackbarAnchor = binding.launcherRoot,
            viewModel = viewModel,
        )
        editModeManager.attach()
        lastOrientation = currentOrientation()
        registerAddFlowListeners()
    }

    /**
     * Wires the "put something on the desktop" chain. Each picker returns on its own key and dismisses
     * before the next opens, so the flow is one dialog at a time. Only the coordinate routing lives here;
     * the placement (and the ADR-10 rememberFileList write) is [LauncherHomeViewModel]'s job (Rule 3).
     */
    private fun registerAddFlowListeners() {
        supportFragmentManager.setFragmentResultListener(
            LauncherCellContentPickerDialogFragment.RESULT_KEY,
            this,
        ) { _, bundle ->
            pendingRow = bundle.getInt(LauncherCellContentPickerDialogFragment.RESULT_ROW)
            pendingCol = bundle.getInt(LauncherCellContentPickerDialogFragment.RESULT_COL)
            when (bundle.getString(LauncherCellContentPickerDialogFragment.RESULT_CATEGORY)) {
                LauncherCellContentPickerDialogFragment.CATEGORY_APP ->
                    openPicker(AppPickerDialogFragment.newInstance(REQ_APP), AppPickerDialogFragment.TAG)
                LauncherCellContentPickerDialogFragment.CATEGORY_FEATURE -> openPicker(
                    InternalRoutePickerDialogFragment.newInstance(REQ_FEATURE),
                    InternalRoutePickerDialogFragment.TAG,
                )
                LauncherCellContentPickerDialogFragment.CATEGORY_RESOURCE -> openPicker(
                    ResourcePickerDialogFragment.newInstance(REQ_RESOURCE_SHORTCUT),
                    ResourcePickerDialogFragment.TAG,
                )
                LauncherCellContentPickerDialogFragment.CATEGORY_STREAM ->
                    openPicker(LauncherStreamPickerDialogFragment.newInstance(), LauncherStreamPickerDialogFragment.TAG)
                LauncherCellContentPickerDialogFragment.CATEGORY_OS ->
                    openPicker(OsShortcutPickerDialogFragment.newInstance(REQ_OS), OsShortcutPickerDialogFragment.TAG)
                LauncherCellContentPickerDialogFragment.CATEGORY_GADGET ->
                    onGadgetChosen(bundle.getString(LauncherCellContentPickerDialogFragment.RESULT_GADGET_KEY))
            }
        }
        supportFragmentManager.setFragmentResultListener(REQ_APP, this) { _, bundle ->
            val pkg = bundle.getString(AppPickerDialogFragment.RESULT_PACKAGE) ?: return@setFragmentResultListener
            addShortcut(LauncherCellCommand.App(pkg))
        }
        supportFragmentManager.setFragmentResultListener(REQ_FEATURE, this) { _, bundle ->
            val routeKey = bundle.getString(InternalRoutePickerDialogFragment.RESULT_ROUTE_KEY)
                ?: return@setFragmentResultListener
            addShortcut(LauncherCellCommand.Feature(routeKey))
        }
        supportFragmentManager.setFragmentResultListener(REQ_OS, this) { _, bundle ->
            val targetKey = bundle.getString(OsShortcutPickerDialogFragment.RESULT_TARGET_KEY)
                ?: return@setFragmentResultListener
            addShortcut(LauncherCellCommand.OsShortcut(targetKey))
        }
        supportFragmentManager.setFragmentResultListener(
            LauncherStreamPickerDialogFragment.RESULT_KEY,
            this,
        ) { _, bundle ->
            val streamId = bundle.getString(LauncherStreamPickerDialogFragment.RESULT_STREAM_ID)
                ?: return@setFragmentResultListener
            addShortcut(LauncherCellCommand.Stream(streamId))
        }
        supportFragmentManager.setFragmentResultListener(REQ_RESOURCE_SHORTCUT, this) { _, bundle ->
            val resourceId = bundle.getLong(ResourcePickerDialogFragment.RESULT_RESOURCE_ID)
            openPicker(
                LauncherResourceModePickerDialogFragment.newInstance(resourceId),
                LauncherResourceModePickerDialogFragment.TAG,
            )
        }
        supportFragmentManager.setFragmentResultListener(
            LauncherResourceModePickerDialogFragment.RESULT_KEY,
            this,
        ) { _, bundle ->
            val resourceId = bundle.getLong(LauncherResourceModePickerDialogFragment.RESULT_RESOURCE_ID)
            val modeName = bundle.getString(LauncherResourceModePickerDialogFragment.RESULT_MODE)
            val mode = LauncherResourceMode.entries.firstOrNull { it.name == modeName }
                ?: return@setFragmentResultListener
            addShortcut(LauncherCellCommand.Resource(resourceId, mode))
        }
        supportFragmentManager.setFragmentResultListener(REQ_RESOURCE_GADGET, this) { _, bundle ->
            val resourceId = bundle.getLong(ResourcePickerDialogFragment.RESULT_RESOURCE_ID)
            placeGadget(pendingGadgetKey ?: return@setFragmentResultListener, resourceId)
        }
        // Taskbar pin flow is separate from the desktop add-flow: no grid coordinate, its own key so an
        // app pinned to the bar is never mistaken for an app dropped on a cell (both share the picker).
        supportFragmentManager.setFragmentResultListener(REQ_PIN_APP, this) { _, bundle ->
            val pkg = bundle.getString(AppPickerDialogFragment.RESULT_PACKAGE) ?: return@setFragmentResultListener
            viewModel.addPin(LauncherCellCommand.App(pkg))
        }
    }

    override fun observeData() {
        taskbarManager.bind(
            recents = viewModel.recentIcons,
            pinned = viewModel.pinnedIcons,
            composition = viewModel.taskbarComposition,
        )
        collectOnLifecycle(viewModel.cells) { renderDesktop() }
        // Entering/leaving edit mode adds or drops the empty slots and remove badges on the desktop and
        // the unpin "X" / trailing "+" on the taskbar, so re-render both.
        collectOnLifecycle(viewModel.editMode) { editMode ->
            renderDesktop()
            taskbarManager.setEditMode(editMode)
        }
        // The density factor changes the column count, so re-derive the grid when it lands.
        collectOnLifecycle(viewModel.densityFactor) {
            applyGridGeometry()
        }
        collectOnLifecycle(viewModel.events) { event ->
            when (event) {
                is LauncherHomeEvent.Message ->
                    Toast.makeText(this, event.messageResId, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResumeWithViews() {
        viewModel.onHomeResumed()
    }

    override fun onLayoutConfigurationChanged(newConfig: Configuration) {
        applyGridGeometry()
        val now = currentOrientation()
        if (now != lastOrientation) {
            lastOrientation = now
            editModeManager.onOrientationChanged()
        }
    }

    /**
     * A GADGET cell's `target` is a registry key, not a command, so a key we do not know is the only
     * "broken gadget" signal there is: [LauncherCellUi.visual] is null for every gadget by contract,
     * so the shortcut's unavailable path cannot double as this one.
     */
    private fun bindGadget(cellUi: LauncherCellUi, container: FrameLayout) {
        val decoded = gadgetRegistry.decodeTarget(cellUi.cell.target)
        val gadget = decoded?.first?.let { gadgetRegistry.byKey(it) }
        if (gadget == null) {
            container.addView(unavailableGadgetView(container))
            return
        }
        container.addView(gadget.createView(container, gadgetHost, decoded.second))
    }

    /**
     * A plain TextView, not the shortcut item: that one is a MaterialCardView, and the gadget cell is
     * already a MaterialCardView - nesting them draws two concentric outlines and doubles the insets.
     *
     * Focusable even though it does nothing: it is the only child of its cell, and a cell with no
     * focusable child is unreachable by D-pad - which would leave a TV user unable to select a broken
     * gadget in order to remove it (Phase 07).
     */
    private fun unavailableGadgetView(container: FrameLayout): View =
        TextView(container.context).apply {
            setText(R.string.launcher_home_cell_unavailable)
            gravity = Gravity.CENTER
            alpha = LauncherCellViewBinder.UNAVAILABLE_ALPHA
            isFocusable = true
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
            setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_launcher_mode, 0, 0)
            foreground = ContextCompat.getDrawable(context, R.drawable.focus_button_background)
        }

    private fun showStartMenu() {
        // The sheet is modal; a second tap while it is up must not stack another one.
        if (supportFragmentManager.findFragmentByTag(LauncherStartMenuFragment.TAG) != null) return
        LauncherStartMenuFragment().show(supportFragmentManager, LauncherStartMenuFragment.TAG)
    }

    /** Entry point from an empty-slot tap (edit mode only): pick what goes in this square. */
    private fun openContentPicker(row: Int, col: Int) {
        openPicker(
            LauncherCellContentPickerDialogFragment.newInstance(row, col),
            LauncherCellContentPickerDialogFragment.TAG,
        )
    }

    /** Edit-mode taskbar "+": pick an app to pin. Routed on its own key so it is not an add-cell pick. */
    private fun openPinAppPicker() {
        openPicker(AppPickerDialogFragment.newInstance(REQ_PIN_APP), AppPickerDialogFragment.TAG)
    }

    private fun openPicker(fragment: DialogFragment, tag: String) {
        // A dialog left up on a rebind must not be duplicated by a second tap.
        if (supportFragmentManager.findFragmentByTag(tag) != null) return
        fragment.show(supportFragmentManager, tag)
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
        if (gadget.requiresResourceParam) {
            pendingGadgetKey = gadgetKey
            val filter = if (gadgetKey == LauncherGadgetRegistry.KEY_PLAYLIST) MediaType.AUDIO else null
            openPicker(
                ResourcePickerDialogFragment.newInstance(REQ_RESOURCE_GADGET, filter),
                ResourcePickerDialogFragment.TAG,
            )
        } else {
            placeGadget(gadgetKey, resourceId = null)
        }
    }

    private fun placeGadget(gadgetKey: String, resourceId: Long?) {
        val gadget = gadgetRegistry.byKey(gadgetKey) ?: return
        viewModel.addCell(
            rowIndex = pendingRow,
            colIndex = pendingCol,
            kind = LauncherCellKind.GADGET,
            target = gadgetRegistry.encodeTarget(gadgetKey, resourceId?.toString()),
            spanW = gadget.defaultSpanW,
            spanH = gadget.defaultSpanH,
            rememberFileListResourceId = resourceId,
        )
    }

    private fun addShortcut(command: LauncherCellCommand) {
        viewModel.addCell(
            rowIndex = pendingRow,
            colIndex = pendingCol,
            kind = LauncherCellKind.SHORTCUT,
            target = command.encode(),
            spanW = 1,
            spanH = 1,
        )
    }

    /**
     * Rotation and density changes re-resolve the column count and re-place every cell. Rebinding IS
     * the re-layout here, so there is no bound state that can go stale behind a changed column count -
     * the trap the RecyclerView renderer had, where requestLayout() never re-ran a bind (ADR-9).
     */
    private fun applyGridGeometry() {
        val orientation = currentOrientation()
        val columns = currentColumns()
        viewModel.setOrientation(orientation)
        renderDesktop()
        viewModel.persistColumns(orientation, columns)
    }

    /**
     * Single desktop render path. All three triggers - cells changing, edit mode toggling, and a grid
     * re-derive on rotation/density - funnel here so edit mode is never dropped by one of them binding
     * without it. The binder's own (cells, columns, editMode) guard makes redundant calls free.
     */
    private fun renderDesktop() {
        cellBinder.bind(
            binding.launcherDesktop,
            viewModel.cells.value,
            currentColumns(),
            viewModel.editMode.value,
        )
    }

    private fun currentColumns(): Int = LauncherGridGeometry.columns(
        availableWidthDp = resources.configuration.screenWidthDp.toFloat(),
        densityFactor = viewModel.densityFactor.value,
    )

    private fun currentOrientation(): LauncherOrientation =
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            LauncherOrientation.LANDSCAPE
        } else {
            LauncherOrientation.PORTRAIT
        }

    /**
     * S0404: seed the starter desktop once, on first entry. The Activity owns the display metrics
     * (current axis = screenWidthDp, other = screenHeightDp); the ViewModel converts them to per-
     * orientation column counts INSIDE its coroutine, using the real persisted density rather than the
     * StateFlow default that may still be in place synchronously here (audit 2026-07-17, P1). The
     * ViewModel and the repository both guard against re-seeding a live desktop.
     */
    private fun seedDesktopIfNeeded() {
        val config = resources.configuration
        viewModel.seedDesktopIfNeeded(
            widthDp = config.screenWidthDp.toFloat(),
            heightDp = config.screenHeightDp.toFloat(),
            startedPortrait = currentOrientation() == LauncherOrientation.PORTRAIT,
        )
    }

    private companion object {
        // Distinct result keys so the launcher's add-flow never collides with the panel editor's pickers
        // on the same FragmentManager, and so a resource pick for a shortcut is told apart from one for a
        // gadget (the former chains a mode picker; the latter completes the gadget).
        const val REQ_APP = "launcher_add_app"
        const val REQ_FEATURE = "launcher_add_feature"
        const val REQ_OS = "launcher_add_os"
        const val REQ_RESOURCE_SHORTCUT = "launcher_add_resource_shortcut"
        const val REQ_RESOURCE_GADGET = "launcher_add_resource_gadget"
        const val REQ_PIN_APP = "launcher_pin_app"
    }
}
