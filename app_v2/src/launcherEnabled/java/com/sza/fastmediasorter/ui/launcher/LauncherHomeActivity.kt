package com.sza.fastmediasorter.ui.launcher

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.launcher.LauncherRoleManager
import com.sza.fastmediasorter.core.panel.LauncherActionCatalog
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityLauncherHomeBinding
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadgetHost
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadgetRegistry
import com.sza.fastmediasorter.ui.launcher.grid.LauncherCellViewBinder
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherAddFlowManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherAllAppsGestureManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherAppActionMenuManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherCellActionMenuManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherContactPickManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherDesktopActions
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherDesktopGeometryManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherEditModeManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherGadgetRenderManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherResizeManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherResourceActionManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherResourceCreateManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherResourceOperations
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherScreenBlackoutManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherScrollThumbManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherSensorPermissionManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherStatusStripManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherStreamActionManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherTaskbarManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherTaskbarPlacementManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherTrayManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherWallpaperManager
import com.sza.fastmediasorter.ui.launcher.menu.LauncherAllAppsFragment
import com.sza.fastmediasorter.ui.launcher.menu.LauncherStartMenuFragment
import com.sza.fastmediasorter.ui.launcher.picker.LauncherSectionNameDialogFragment
import com.sza.fastmediasorter.ui.launcher.section.LauncherSectionActionsSheet
import com.sza.fastmediasorter.ui.launcher.tray.LauncherTrayCallbacks
import com.sza.fastmediasorter.ui.main.helpers.ResourceVrCinemaLaunchManager
import com.sza.fastmediasorter.ui.player.helpers.BlackScreenOverlayManager
import com.sza.fastmediasorter.ui.player.helpers.SystemBarsManager
import com.sza.fastmediasorter.ui.settings.LauncherSettingsDialogFragment
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.util.showBoundToHost
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.widget.ResourceShortcutPinManager
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.lang.ref.WeakReference
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

    // S1421: the one node that owns the freed status band (ADR-2). Injected rather than built here because
    // it needs the singleton signal registry; the activity only hands it the view and the lifecycle.
    @Inject
    lateinit var statusStripManager: LauncherStatusStripManager

    // S1402: the desktop can now carry the "leave launcher mode" action, and handing the home role back
    // is this manager's job - the same one the Start menu row uses.
    @Inject
    lateinit var roleManager: LauncherRoleManager

    // S1423: the one shared launch every home-screen entry point uses; this host never builds the
    // Add Resource intent itself.
    @Inject
    lateinit var resourceCreateManager: LauncherResourceCreateManager

    // S1424: the same pin request the main window's "Add to home screen" makes, so the desktop's copy
    // of that row produces an identical shortcut rather than a lookalike.
    @Inject
    lateinit var resourceShortcutPinManager: ResourceShortcutPinManager

    // S1424: activity-scoped, and it watches XR availability from its own init block - so it must be
    // injected rather than built, or the desktop would read a state nobody is collecting.
    @Inject
    lateinit var resourceVrCinema: ResourceVrCinemaLaunchManager

    // S1415: registered here for the same reason as contactPickManager below - a contract registered after
    // the Activity is STARTED throws. The activity only owns the launcher; whether and when to ask is the
    // tray manager's decision (Rule 3).
    private val requestPhoneStatePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // S1179: a field initialiser for the same reason as the two above - it registers a permission
    // contract. Which gadget needs which permission, and whether to ask at all, is the manager's
    // decision (Rule 3); the activity only owns where the cell goes.
    private val sensorPermissionManager = LauncherSensorPermissionManager(this)

    // A field initialiser, not setupViews(): BaseActivity posts that call, so it runs after the Activity
    // is STARTED and an activity-result contract registered there would throw. The operations are passed
    // as functions so nothing is dereferenced at this point in construction.
    private val contactPickManager = LauncherContactPickManager(
        activity = this,
        pickIntent = { action -> viewModel.contactPickIntent(action) },
        resolvePick = { action, picked -> viewModel.resolveContactPick(action, picked) },
        onTargetPicked = { target -> addFlowManager.addShortcut(LauncherCellCommand.Contact(target)) },
    )

    private val cellBinder = LauncherCellViewBinder(
        onCellClick = { viewModel.onCellTapped(it) },
        onEmptySlotClick = { row, col -> addFlowManager.openContentPicker(row, col) },
        onRemoveClick = { viewModel.removeCell(it.cell.id) },
        // editModeManager is lateinit, but this lambda only fires on a long-press in edit mode - long
        // after setupViews() has created it.
        onCellDragStart = { view, cellUi -> editModeManager.startCellDrag(view, cellUi) },
        // resizeManager is lateinit for the same reason: a resize handle only exists on a gadget cell in
        // edit mode, rendered after setupViews() has built the manager.
        onAttachResizeHandle = { handle, cellUi -> resizeManager.attachHandle(handle, cellUi) },
        onCellLongPress = { view, cellUi -> showCellActions(view, cellUi) },
        onSectionClick = { cellUi -> viewModel.sections.toggle(cellUi.cell) },
        onSectionLongClick = { _, cellUi -> showSectionActions(cellUi) },
    )

    private lateinit var taskbarManager: LauncherTaskbarManager

    private lateinit var placementManager: LauncherTaskbarPlacementManager

    private lateinit var editModeManager: LauncherEditModeManager

    // S1101: created in setupViews() like the other managers; onStart/onStop forward the foreground edges.
    private lateinit var wallpaperManager: LauncherWallpaperManager

    private lateinit var resizeManager: LauncherResizeManager

    // S1741: manages the app-private screen blackout overlay and inactivity timeout.
    private lateinit var blackoutManager: LauncherScreenBlackoutManager

    // S1560: one overlay per Activity, built on the first black-screen tap - a fresh manager per tap
    // would leak the previous overlay view on the decor view instead of reusing its hide path.
    private val blackScreenOverlayManager by lazy {
        BlackScreenOverlayManager(WeakReference(this), SystemBarsManager(this))
    }

    // Built lazily on the first long press: most Home visits never open the popup at all.
    private val shortcutMenuManager by lazy {
        LauncherAppActionMenuManager(
            scope = lifecycleScope,
            queryAppShortcuts = { packageName -> viewModel.appShortcutsOf(packageName) },
            startAppShortcut = { shortcut, bounds -> viewModel.launchAppShortcut(shortcut, bounds) },
            launchApp = { packageName -> viewModel.run(LauncherCellCommand.App(packageName)) },
            // The column count belongs to the screen drawing the desktop, not to the stored desktop,
            // so it is read at the moment of the tap rather than captured when the menu was built.
            placeOnDesktop = { packageName ->
                viewModel.placeAppOnDesktop(packageName, geometryManager.currentColumns())
            },
            pinToTaskbar = { packageName -> viewModel.pinAppToTaskbar(packageName) },
            appInfoIntent = { packageName -> viewModel.appInfoIntent(packageName) },
            uninstallIntent = { packageName -> viewModel.uninstallIntent(packageName) },
        )
    }

    // S1424: lazy for the same reason as shortcutMenuManager above - a Home visit that never long
    // presses a resource cell builds neither of these.
    private val resourceActionManager by lazy {
        LauncherResourceActionManager(
            activity = this,
            scope = lifecycleScope,
            loadResource = { resourceId -> viewModel.resourceById(resourceId) },
            runCommand = { command -> viewModel.run(command) },
            deleteResource = { resourceId -> viewModel.deleteResource(resourceId) },
            shortcutPinManager = resourceShortcutPinManager,
            vrCinema = resourceVrCinema,
            operations = LauncherResourceOperations(
                scan = { resource -> viewModel.scanResource(resource) },
                export = { resourceId, target -> viewModel.exportResource(resourceId, target) },
                exportCompanionConfig = { resource, includePassword ->
                    viewModel.exportCompanionConfig(resource, includePassword)
                },
                companionQrPayload = { resource, includePassword ->
                    viewModel.companionQrPayload(resource, includePassword)
                },
            ),
        )
    }

    private val streamActionManager by lazy {
        LauncherStreamActionManager(
            activity = this,
            loadStream = { streamId -> viewModel.streamById(streamId) },
            loadPinnedStreams = { viewModel.pinnedStreams() },
            togglePin = { source -> viewModel.toggleStreamPin(source) },
            removeStream = { source -> viewModel.removeStream(source) },
            streamEdit = viewModel.streamEditDependencies,
        )
    }

    private val cellActionMenuManager by lazy {
        LauncherCellActionMenuManager(
            scope = lifecycleScope,
            resourceRows = { resourceId -> resourceActionManager.rowsFor(resourceId) },
            streamRows = { streamId -> streamActionManager.rowsFor(streamId) },
        )
    }

    // Lazy, like the managers below it: the contact manager's result callback points back at this one,
    // so neither can be built in a field initialiser without the other existing first.
    private val addFlowManager: LauncherAddFlowManager by lazy {
        LauncherAddFlowManager(
            fragmentManager = supportFragmentManager,
            lifecycleOwner = this,
            viewModel = viewModel,
            gadgetRegistry = gadgetRegistry,
            contactPickManager = contactPickManager,
            sensorPermissionManager = sensorPermissionManager,
            currentColumns = { geometryManager.currentColumns() },
            onCreateResource = { resourceCreateManager.startCreateResource(this) },
        )
    }

    private val gadgetRenderManager: LauncherGadgetRenderManager by lazy {
        LauncherGadgetRenderManager(
            gadgetRegistry = gadgetRegistry,
            gadgetHost = gadgetHost,
            onWeatherReconfigure = { cellId -> addFlowManager.openWeatherLocationPicker(cellId) },
        )
    }

    // Lazy for the same reason the overlay managers below are: it needs the inflated binding, and its
    // first read happens inside setupViews(), after BaseActivity has inflated it.
    private val geometryManager: LauncherDesktopGeometryManager by lazy {
        LauncherDesktopGeometryManager(
            resources = resources,
            desktop = binding.launcherDesktop,
            viewport = binding.launcherGridScroll,
            cellBinder = cellBinder,
            viewModel = viewModel,
        )
    }

    /** Gadgets reach the desktop only through the one launch guard every other surface shares. */
    private val gadgetHost = object : LauncherGadgetHost {
        override fun run(command: LauncherCellCommand) = viewModel.run(command)
    }

    override fun getViewBinding(): ActivityLauncherHomeBinding =
        ActivityLauncherHomeBinding.inflate(layoutInflater)

    override fun setupViews() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // BaseActivity posts setupViews(), so the window's first insets dispatch has already
        // happened - applySystemBarInsetPadding registers the listener AND applies the current
        // insets immediately, which a bare listener would miss (Rule 17).
        // S1087: this surface hides the status bar on request, so the resource-height fallback must not
        // reserve a band for a bar that is gone - the removal would look like nothing happened.
        binding.launcherRoot.applySystemBarInsetPadding(useStatusBarHeightFallback = false)
        // A home screen has nowhere to go back to: Back must not finish the surface and expose
        // whatever sits behind it.
        onBackPressedDispatcher.addCallback(this) { }
        cellBinder.gadgetBinder = gadgetRenderManager::bindGadget
        geometryManager.applyGridGeometry()
        geometryManager.seedDesktopIfNeeded()

        LauncherTrayManager(
            lifecycleOwner = this,
            clock = binding.launcherTaskbar.trayClock,
            indicators = binding.launcherTaskbar.trayIndicators,
            callbacks = trayCallbacks(),
            // S1431: gated on the taskbar being the placement in use, not merely on the launcher owning the
            // status area - otherwise this renderer and the strip's would subscribe to the same sources at
            // once while the mode is on.
        ).bind(viewModel.taskbarTrayContentVisible, viewModel.trayComposition)
        statusStripManager.bind(
            binding = binding.launcherStatusStrip,
            lifecycleOwner = this,
            fragmentManager = supportFragmentManager,
            replaceSystemStatusArea = viewModel.replaceSystemStatusArea,
            topStatusStripMode = viewModel.topStatusStripMode,
            trayComposition = viewModel.trayComposition,
            callbacks = trayCallbacks(),
        )
        collectOnLifecycle(viewModel.replaceSystemStatusArea) { applyStatusBarPolicy(it) }
        taskbarManager = LauncherTaskbarManager(
            lifecycleOwner = this,
            binding = binding.launcherTaskbar,
            onCommand = { viewModel.run(it) },
            onStartClick = { showStartMenu() },
            onAllAppsClick = { showAllApps() },
            onAddPin = { addFlowManager.openPinAppPicker() },
            onRemovePin = { viewModel.removePin(it) },
            onRecentsCapacity = { viewModel.recentsCapacity = it },
        )
        placementManager = LauncherTaskbarPlacementManager(
            lifecycleOwner = this,
            root = binding.launcherRoot,
        )
        attachScrollThumb()
        attachEditMode()
        wallpaperManager = LauncherWallpaperManager(
            lifecycleOwner = this,
            imageLayer = binding.launcherWallpaperImage,
            wavesLayer = binding.launcherWallpaperWaves,
            viewModel = viewModel,
        )
        wallpaperManager.attach()
        // setupViews() is posted by BaseActivity, so onStart() has already run for this instance and the
        // manager would otherwise sit paused until the next foreground edge.
        wallpaperManager.onStart()
        resizeManager = LauncherResizeManager(
            container = binding.launcherDesktop,
            viewport = binding.launcherGridScroll,
            gadgetRegistry = gadgetRegistry,
            viewModel = viewModel,
        )
        LauncherAllAppsGestureManager(
            container = binding.launcherDesktop,
            viewport = binding.launcherGridScroll,
            // Edit mode is for arranging the desktop; a swipe there belongs to the drag, not to us.
            isEnabled = { !viewModel.editMode.value },
            onOpen = { showAllApps() },
        ).attach()
        geometryManager.syncOrientation()
        addFlowManager.registerAddFlowListeners()
        blackoutManager = LauncherScreenBlackoutManager(WeakReference(this))
        blackoutManager.onStart()
    }

    /**
     * The desktop's gestures and the four entry points they can reach. Kept out of `setupViews` for the
     * same reason [attachScrollThumb] is - the actions bundle is where this screen says which surface each
     * quick-menu item opens, and it reads better as one block than as a tail of that function.
     */
    private fun attachEditMode() {
        editModeManager = LauncherEditModeManager(
            lifecycleOwner = this,
            desktop = binding.launcherDesktop,
            // S1412: the button moved onto the taskbar, so it is reached through the included layout.
            doneButton = binding.launcherTaskbar.launcherEditDone,
            addCellButton = binding.launcherTaskbar.launcherAddCell,
            snackbarAnchor = binding.launcherRoot,
            viewModel = viewModel,
            actions = LauncherDesktopActions(
                addItem = {
                    addFlowManager.openContentPicker(
                        LauncherAddFlowManager.NO_SLOT,
                        LauncherAddFlowManager.NO_SLOT,
                    )
                },
                // S1466: the same picker, told which square the user pointed at (owner's ruling 2026-08-17).
                addItemAtSlot = { row, col -> addFlowManager.openContentPicker(row, col) },
                wallpaper = { showLauncherSettings(LauncherSettingsDialogFragment.SECTION_DESKTOP) },
                launcherSettings = { showLauncherSettings() },
            ),
        )
        editModeManager.attach()
    }

    /** S1430: the desktop's own scroll thumb - the system bar it replaces could not be dragged. */
    private fun attachScrollThumb() {
        LauncherScrollThumbManager(
            lifecycleOwner = this,
            scrollView = binding.launcherGridScroll,
            thumb = binding.launcherScrollThumb,
        ).attach()
    }

    override fun observeData() {
        taskbarManager.bind(
            recents = viewModel.recentIcons,
            pinned = viewModel.pinnedIcons,
            composition = viewModel.taskbarComposition,
        )
        placementManager.bind(viewModel.taskbarAtTop)
        collectOnLifecycle(viewModel.cells) { cells ->
            geometryManager.renderDesktop()
            // S1400: an empty desktop is the only signal this surface gets when a reset wipes the
            // cells underneath it. Which of the two empty states it is, the seed use case decides from
            // the persisted flags: a reset lowers them and the starter set comes back, while a desktop
            // the user emptied by hand keeps them raised and stays empty.
            if (cells.isEmpty()) geometryManager.seedDesktopIfNeeded()
        }
        // Entering/leaving edit mode adds or drops the empty slots and remove badges on the desktop and
        // the unpin "X" / trailing "+" on the taskbar, so re-render both.
        collectOnLifecycle(viewModel.editMode) { editMode ->
            geometryManager.renderDesktop()
            taskbarManager.setEditMode(editMode)
        }
        collectOnLifecycle(viewModel.screenBlackoutTimeoutSeconds) { timeout ->
            blackoutManager.updateTimeout(timeout)
        }
        // The density factor changes the column count, so re-derive the grid when it lands.
        collectOnLifecycle(viewModel.densityFactor) {
            geometryManager.applyGridGeometry()
        }
        // S1428: folding a section changes which rows are drawn and nothing that is stored, so it is a
        // render trigger like the two above rather than a desktop change.
        collectOnLifecycle(viewModel.sections.collapsed) {
            geometryManager.renderDesktop()
        }
        collectOnLifecycle(viewModel.events) { event ->
            when (event) {
                is LauncherHomeEvent.Message ->
                    Toast.makeText(this, event.messageResId, Toast.LENGTH_SHORT).show()
                LauncherHomeEvent.OpenStreamPicker ->
                    addFlowManager.openStreamPicker()
                LauncherHomeEvent.OpenStreamsSettings -> {
                    startActivity(SettingsActivity.openStreamsSectionIntent(this))
                    Toast.makeText(this, R.string.launcher_edit_streams_enable_first, Toast.LENGTH_LONG).show()
                }
                is LauncherHomeEvent.PerformLauncherAction -> performLauncherAction(event.actionKey)
                is LauncherHomeEvent.ConfirmScheduledOp -> {
                    // Buttons are theme-styled (S0538 confirm/cancel pair via materialAlertDialogTheme),
                    // not per-call, matching the launcher exit-confirm dialog.
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.launcher_scheduled_op_confirm_title)
                        .setMessage(R.string.launcher_scheduled_op_confirm_message)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            viewModel.executeScheduledOp(event.operationId)
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .showBoundToHost(this@LauncherHomeActivity)
                }
            }
        }
    }

    override fun onResumeWithViews() {
        viewModel.onHomeResumed()
    }

    /**
     * S1402: a launcher-action cell does exactly what its namesake row in the Start menu does - same
     * screen, same dialog, same confirmation before the device stops using us as its home screen.
     */
    private fun performLauncherAction(actionKey: String) {
        when (actionKey) {
            LauncherActionCatalog.KEY_APP_SETTINGS ->
                startActivity(Intent(this, SettingsActivity::class.java))

            LauncherActionCatalog.KEY_LAUNCHER_SETTINGS -> showLauncherSettings()

            LauncherActionCatalog.KEY_ALL_APPS -> showAllApps()

            LauncherActionCatalog.KEY_BLACK_SCREEN -> showBlackScreen()

            LauncherActionCatalog.KEY_EXIT_LAUNCHER_MODE -> confirmExitLauncherMode()
        }
    }

    /**
     * The one place this screen opens the launcher settings dialog - the Start-menu row, the desktop cell
     * and the quick menu all come through here, so the three cannot drift into three different dialogs.
     */
    private fun showLauncherSettings(expandSection: String? = null) {
        LauncherSettingsDialogFragment.newInstance(expandSection)
            .show(supportFragmentManager, LauncherSettingsDialogFragment.TAG)
    }

    /** The overlay dismisses itself on touch, so the cell only ever has to raise it. */
    private fun showBlackScreen() {
        blackScreenOverlayManager.show()
    }

    private fun confirmExitLauncherMode() {
        // Buttons are theme-styled (S0538 confirm/cancel pair via materialAlertDialogTheme), matching
        // the Start menu's own exit dialog rather than restyling per call.
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.launcher_menu_exit_confirm_title)
            .setMessage(R.string.launcher_menu_exit_confirm_message)
            .setPositiveButton(R.string.launcher_menu_exit_confirm_action) { _, _ ->
                roleManager.disableMode()
                roleManager.openHomeChooser(this)
            }
            .setNegativeButton(R.string.cancel, null)
            .showBoundToHost(this@LauncherHomeActivity)
    }

    /**
     * S1424: one long-press handler that picks the action provider by command kind (strategic 5.1.1).
     *
     * Everything it does not name returns false, which leaves that cell behaving exactly as it does
     * today - including the pinned third-party shortcut, whose resource id survives only inside a
     * string convention and is deliberately out of scope (strategic 2, Non-goals).
     */
    private fun showCellActions(view: View, cellUi: LauncherCellUi): Boolean {
        if (viewModel.editMode.value) return false
        return when (val command = LauncherCellCommand.decode(cellUi.cell.target)) {
            is LauncherCellCommand.App -> {
                shortcutMenuManager.show(view, command.packageName)
                true
            }

            is LauncherCellCommand.Resource -> {
                cellActionMenuManager.showForResource(view, command.resourceId)
                true
            }

            is LauncherCellCommand.Stream -> {
                cellActionMenuManager.showForStream(view, command.streamId)
                true
            }

            else -> false
        }
    }

    private fun showSectionActions(cellUi: LauncherCellUi): Boolean {
        val sheet = LauncherSectionActionsSheet()
        sheet.items = listOf(
            LauncherSectionActionsSheet.ActionItem(
                action = LauncherSectionActionsSheet.Action.RENAME,
                label = getString(R.string.launcher_section_action_rename),
                iconResId = R.drawable.ic_rename,
            ),
        )
        sheet.onItemClick = { action ->
            when (action) {
                LauncherSectionActionsSheet.Action.RENAME -> {
                    openRenameSectionDialog(cellUi)
                }
            }
        }
        sheet.show(supportFragmentManager, "LauncherSectionActionsSheet")
        return true
    }

    private fun openRenameSectionDialog(cellUi: LauncherCellUi) {
        val requestKey = "rename_section_${cellUi.cell.id}"
        val initialName = cellUi.visual?.label?.toString().orEmpty()
        supportFragmentManager.setFragmentResultListener(requestKey, this) { _, bundle ->
            val newName = bundle.getString(LauncherSectionNameDialogFragment.RESULT_NAME)
            if (!newName.isNullOrBlank()) {
                viewModel.renameSection(cellUi.cell.id, newName)
            }
        }
        LauncherSectionNameDialogFragment.newInstance(requestKey, initialName)
            .show(supportFragmentManager, LauncherSectionNameDialogFragment.TAG)
    }

    override fun onStart() {
        super.onStart()
        // Guarded: onStart fires before BaseActivity's posted setupViews() on the very first pass, which
        // is where the manager is created - that pass starts it itself.
        if (::wallpaperManager.isInitialized) wallpaperManager.onStart()
        if (::blackoutManager.isInitialized) blackoutManager.onStart()
    }

    override fun onStop() {
        super.onStop()
        // S1087: symmetric with the policy applied while started - the status bar is hidden only for as
        // long as the launcher is the surface on screen, never for whatever the user opens next.
        statusBarController().show(WindowInsetsCompat.Type.statusBars())
        // Symmetric with the long press that opened it: a popup must not survive the surface leaving
        // the foreground, or it reappears over whatever the user opened next.
        shortcutMenuManager.dismiss()
        // S1424: same edge, same reason - the resource menu is anchored to a cell of this surface.
        cellActionMenuManager.dismiss()
        // S1101: symmetric with onStart - an animated wallpaper must not keep drawing off-screen.
        if (::wallpaperManager.isInitialized) wallpaperManager.onStop()
        if (::blackoutManager.isInitialized) blackoutManager.onStop()
    }

    /**
     * S1767: both tray placements ask the host for the same two things, so they are built in one place -
     * a callback wired for one placement and forgotten for the other is the bug this shape prevents.
     */
    private fun trayCallbacks(): LauncherTrayCallbacks = LauncherTrayCallbacks(
        onRequestPhoneStatePermission = {
            requestPhoneStatePermission.launch(Manifest.permission.READ_PHONE_STATE)
        },
        // Screen-only: an indicator reports on a section and opens it, never toggling the radio the
        // shared OsShortcut path would try first (ticket ADR-1).
        onOpenSystemScreen = { key ->
            viewModel.run(LauncherCellCommand.OsShortcut(key), screenOnly = true)
        },
    )

    override fun onDestroy() {
        // S1421: the manager holds the strip's binding, so it drops it here rather than leaving a destroyed
        // hierarchy reachable for as long as anything still references the manager.
        statusStripManager.unbind()
        if (::blackoutManager.isInitialized) blackoutManager.onDestroy()
        super.onDestroy()
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        if (ev != null && ::blackoutManager.isInitialized && blackoutManager.onDispatchTouchEvent(ev)) {
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        if (::blackoutManager.isInitialized && blackoutManager.onDispatchGenericMotionEvent(event)) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (::blackoutManager.isInitialized && blackoutManager.onDispatchKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * S1087 / S1766: hand the status area to the launcher, or give it back. Only `statusBars()` is touched - the
     * navigation bar is the way out of a Home surface and stays whatever the setting says. The safe-area
     * padding is re-applied on every change because the inset the desktop must respect moves with the
     * bar, and a stale one leaves either a gap or content under the cutout (Rule 17).
     */
    private fun applyStatusBarPolicy(replaceSystemStatusArea: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = statusBarController()
        if (replaceSystemStatusArea) {
            // S1409 / S1766: transient bars by swipe preserve top notification shade gesture. When replacing
            // the system status area, top inset padding on launcherRoot is disabled (applyTop = false) so the
            // custom launcher status strip occupies y = 0 without top gap, while cutout bounds handle signal alignment.
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
            binding.launcherRoot.applySystemBarInsetPadding(
                applyTop = false,
                useStatusBarHeightFallback = false,
            )
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
            binding.launcherRoot.applySystemBarInsetPadding(
                applyTop = true,
                useStatusBarHeightFallback = false,
            )
        }
        ViewCompat.requestApplyInsets(binding.launcherRoot)
    }

    private fun statusBarController() = WindowCompat.getInsetsController(window, binding.launcherRoot)

    override fun onLayoutConfigurationChanged(newConfig: Configuration) {
        Timber.d("S1549: LauncherHomeActivity onLayoutConfigurationChanged - grid padding re-read on rotation")
        // S1549: this screen absorbs the configuration change to keep an unfinished widget placement, so the
        // grid padding has to be re-read by hand - resources already resolve to the new orientation here.
        val gridPadding = resources.getDimensionPixelSize(R.dimen.launcher_grid_side_padding)
        binding.launcherGridScroll.setPaddingRelative(
            gridPadding,
            binding.launcherGridScroll.paddingTop,
            gridPadding,
            binding.launcherGridScroll.paddingBottom,
        )
        geometryManager.applyGridGeometry()
        if (geometryManager.consumeOrientationChange()) {
            editModeManager.onOrientationChanged()
        }
    }

    private fun showStartMenu() {
        // The sheet is modal; a second tap while it is up must not stack another one.
        if (supportFragmentManager.findFragmentByTag(LauncherStartMenuFragment.TAG) != null) return
        LauncherStartMenuFragment().show(supportFragmentManager, LauncherStartMenuFragment.TAG)
    }

    /** S1401: the app list, reached from the taskbar button and from the swipe-up gesture alike. */
    private fun showAllApps() {
        // The desktop stays touchable behind the screen, so the button and the gesture must not each
        // open their own instance - the same guard the Start menu carries.
        if (supportFragmentManager.findFragmentByTag(LauncherAllAppsFragment.TAG) != null) return
        LauncherAllAppsFragment().show(supportFragmentManager, LauncherAllAppsFragment.TAG)
    }
}
