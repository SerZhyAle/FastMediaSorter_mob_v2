package com.sza.fastmediasorter.ui.launcher

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.launcher.LauncherRoleManager
import com.sza.fastmediasorter.core.panel.LauncherActionCatalog
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityLauncherHomeBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactAction
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.model.launcher.LauncherResourceMode
import com.sza.fastmediasorter.domain.model.weather.WeatherLocation
import com.sza.fastmediasorter.ui.applaunchpanel.edit.AppPickerDialogFragment
import com.sza.fastmediasorter.ui.applaunchpanel.edit.InternalRoutePickerDialogFragment
import com.sza.fastmediasorter.ui.applaunchpanel.edit.OsShortcutPickerDialogFragment
import com.sza.fastmediasorter.ui.applaunchpanel.edit.ResourcePickerDialogFragment
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadgetHost
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadgetRegistry
import com.sza.fastmediasorter.ui.launcher.grid.LauncherCellViewBinder
import com.sza.fastmediasorter.ui.launcher.grid.LauncherGridGeometry
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherAllAppsGestureManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherAppActionMenuManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherCellActionMenuManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherContactPickManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherEditModeManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherResizeManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherResourceActionManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherResourceCreateManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherResourceOperations
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherSensorPermissionManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherStatusStripManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherStreamActionManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherTaskbarManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherTrayManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherWallpaperManager
import com.sza.fastmediasorter.ui.launcher.menu.LauncherAllAppsFragment
import com.sza.fastmediasorter.ui.launcher.menu.LauncherStartMenuFragment
import com.sza.fastmediasorter.ui.launcher.picker.LauncherCellContentPickerDialogFragment
import com.sza.fastmediasorter.ui.launcher.picker.LauncherResourceModePickerDialogFragment
import com.sza.fastmediasorter.ui.launcher.picker.LauncherScheduledOpPickerDialogFragment
import com.sza.fastmediasorter.ui.launcher.picker.LauncherStreamPickerDialogFragment
import com.sza.fastmediasorter.ui.launcher.picker.LauncherWeatherLocationDialogFragment
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
        onTargetPicked = { target -> addShortcut(LauncherCellCommand.Contact(target)) },
    )

    private val cellBinder = LauncherCellViewBinder(
        onCellClick = { viewModel.onCellTapped(it) },
        onEmptySlotClick = { row, col -> openContentPicker(row, col) },
        onRemoveClick = { viewModel.removeCell(it.cell.id) },
        // editModeManager is lateinit, but this lambda only fires on a long-press in edit mode - long
        // after setupViews() has created it.
        onCellDragStart = { view, cellUi -> editModeManager.startCellDrag(view, cellUi) },
        // resizeManager is lateinit for the same reason: a resize handle only exists on a gadget cell in
        // edit mode, rendered after setupViews() has built the manager.
        onAttachResizeHandle = { handle, cellUi -> resizeManager.attachHandle(handle, cellUi) },
        onCellLongPress = { view, cellUi -> showCellActions(view, cellUi) },
        onSectionClick = { cellUi -> viewModel.sections.toggle(cellUi.cell) },
    )

    private lateinit var taskbarManager: LauncherTaskbarManager

    private lateinit var editModeManager: LauncherEditModeManager

    // S1101: created in setupViews() like the other managers; onStart/onStop forward the foreground edges.
    private lateinit var wallpaperManager: LauncherWallpaperManager

    private lateinit var resizeManager: LauncherResizeManager

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
            placeOnDesktop = { packageName -> viewModel.placeAppOnDesktop(packageName, currentColumns()) },
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

    /** Tracks the last orientation so the one-shot rotation hint fires only on an actual flip. */
    private var lastOrientation: LauncherOrientation = LauncherOrientation.PORTRAIT

    // The add-flow spans several dialogs that each dismiss before the next opens, and the shared panel
    // pickers carry no grid coordinate, so the target square (and, for a resource gadget, the gadget key)
    // is held here between steps. One flow runs at a time - the chooser is modal.
    // S1209: [NO_SLOT] in either coordinate means the flow started from the taskbar "+", where the user
    // pointed at no square and the repository picks the position.
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
        // S1087: this surface hides the status bar on request, so the resource-height fallback must not
        // reserve a band for a bar that is gone - the removal would look like nothing happened.
        binding.launcherRoot.applySystemBarInsetPadding(useStatusBarHeightFallback = false)
        // A home screen has nowhere to go back to: Back must not finish the surface and expose
        // whatever sits behind it.
        onBackPressedDispatcher.addCallback(this) { }
        cellBinder.gadgetBinder = ::bindGadget
        applyGridGeometry()
        seedDesktopIfNeeded()

        LauncherTrayManager(
            lifecycleOwner = this,
            clock = binding.launcherTaskbar.trayClock,
            indicators = binding.launcherTaskbar.trayIndicators,
            onRequestPhoneStatePermission = {
                requestPhoneStatePermission.launch(Manifest.permission.READ_PHONE_STATE)
            },
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
            onRequestPhoneStatePermission = {
                requestPhoneStatePermission.launch(Manifest.permission.READ_PHONE_STATE)
            },
        )
        collectOnLifecycle(viewModel.replaceSystemStatusArea) { applyStatusBarPolicy(it) }
        taskbarManager = LauncherTaskbarManager(
            lifecycleOwner = this,
            binding = binding.launcherTaskbar,
            onCommand = { viewModel.run(it) },
            onStartClick = { showStartMenu() },
            onAllAppsClick = { showAllApps() },
            onAddPin = { openPinAppPicker() },
            onRemovePin = { viewModel.removePin(it) },
            onRecentsCapacity = { viewModel.recentsCapacity = it },
        )
        editModeManager = LauncherEditModeManager(
            lifecycleOwner = this,
            desktop = binding.launcherDesktop,
            // S1412: the button moved onto the taskbar, so it is reached through the included layout.
            doneButton = binding.launcherTaskbar.launcherEditDone,
            addCellButton = binding.launcherTaskbar.launcherAddCell,
            snackbarAnchor = binding.launcherRoot,
            viewModel = viewModel,
            onAddCellClick = { openContentPicker(NO_SLOT, NO_SLOT) },
        )
        editModeManager.attach()
        Timber.d("S1412: edit-done button bound from the taskbar layout")
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
            openPickerForCategory(bundle)
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
        supportFragmentManager.setFragmentResultListener(
            LauncherScheduledOpPickerDialogFragment.RESULT_KEY,
            this,
        ) { _, bundle ->
            val operationId = bundle.getLong(LauncherScheduledOpPickerDialogFragment.RESULT_OPERATION_ID, -1L)
            if (operationId <= 0L) return@setFragmentResultListener
            addShortcut(LauncherCellCommand.ScheduledOp(operationId))
        }
        registerResourceListeners()
        registerWeatherLocationListener()
        // Taskbar pin flow is separate from the desktop add-flow: no grid coordinate, its own key so an
        // app pinned to the bar is never mistaken for an app dropped on a cell (both share the picker).
        supportFragmentManager.setFragmentResultListener(REQ_PIN_APP, this) { _, bundle ->
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
    private fun openPickerForCategory(bundle: android.os.Bundle) {
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
        }
    }

    override fun observeData() {
        taskbarManager.bind(
            recents = viewModel.recentIcons,
            pinned = viewModel.pinnedIcons,
            composition = viewModel.taskbarComposition,
        )
        collectOnLifecycle(viewModel.cells) { cells ->
            renderDesktop()
            // S1400: an empty desktop is the only signal this surface gets when a reset wipes the
            // cells underneath it. Which of the two empty states it is, the seed use case decides from
            // the persisted flags: a reset lowers them and the starter set comes back, while a desktop
            // the user emptied by hand keeps them raised and stays empty.
            if (cells.isEmpty()) seedDesktopIfNeeded()
        }
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
        // S1428: folding a section changes which rows are drawn and nothing that is stored, so it is a
        // render trigger like the two above rather than a desktop change.
        collectOnLifecycle(viewModel.sections.collapsed) {
            renderDesktop()
        }
        collectOnLifecycle(viewModel.events) { event ->
            when (event) {
                is LauncherHomeEvent.Message ->
                    Toast.makeText(this, event.messageResId, Toast.LENGTH_SHORT).show()
                LauncherHomeEvent.OpenStreamPicker ->
                    openPicker(LauncherStreamPickerDialogFragment.newInstance(), LauncherStreamPickerDialogFragment.TAG)
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

            LauncherActionCatalog.KEY_LAUNCHER_SETTINGS ->
                LauncherSettingsDialogFragment()
                    .show(supportFragmentManager, LauncherSettingsDialogFragment.TAG)

            LauncherActionCatalog.KEY_ALL_APPS -> showAllApps()

            LauncherActionCatalog.KEY_BLACK_SCREEN -> showBlackScreen()

            LauncherActionCatalog.KEY_EXIT_LAUNCHER_MODE -> confirmExitLauncherMode()
        }
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
     * S0427: expands an installed app's published quick actions. Only an app cell has them, and only at
     * rest - in edit mode the same gesture belongs to the drag that rearranges the desktop.
     */
    /**
     * S1424: one long-press handler that picks the action provider by command kind (strategic 5.1.1).
     *
     * Everything it does not name returns false, which leaves that cell behaving exactly as it does
     * today - including the pinned third-party shortcut, whose resource id survives only inside a
     * string convention and is deliberately out of scope (strategic 2, Non-goals).
     */
    private fun showCellActions(view: View, cellUi: LauncherCellUi): Boolean {
        if (viewModel.editMode.value) return false
        Timber.d("S1424: cell menu target=%s", cellUi.cell.target)
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

    override fun onStart() {
        super.onStart()
        // Guarded: onStart fires before BaseActivity's posted setupViews() on the very first pass, which
        // is where the manager is created - that pass starts it itself.
        if (::wallpaperManager.isInitialized) wallpaperManager.onStart()
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
    }

    override fun onDestroy() {
        // S1421: the manager holds the strip's binding, so it drops it here rather than leaving a destroyed
        // hierarchy reachable for as long as anything still references the manager.
        statusStripManager.unbind()
        super.onDestroy()
    }

    /**
     * S1087: hand the status area to the launcher, or give it back. Only `statusBars()` is touched - the
     * navigation bar is the way out of a Home surface and stays whatever the setting says. The safe-area
     * padding is re-applied on every change because the inset the desktop must respect moves with the
     * bar, and a stale one leaves either a gap or content under the cutout (Rule 17).
     */
    private fun applyStatusBarPolicy(replaceSystemStatusArea: Boolean) {
        Timber.d("S1087: status bar policy replace=%s", replaceSystemStatusArea)
        val controller = statusBarController()
        if (replaceSystemStatusArea) {
            // S1409: the default behaviour hands the bar back permanently once the user swipes it into
            // view, so the setting looked switched off from the first swipe until it was applied again.
            // Transient is what the setting actually promises: the bar is there on demand and leaves on
            // its own. Set before hide - the behaviour applies to the hidden types.
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            Timber.d("S1409: status bar hidden with transient-by-swipe behavior")
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
        // Re-apply, never re-install: applySystemBarInsetPadding captures the view's current padding as
        // its base, so calling it again would treat the already-inset padding as the base and compound
        // it on every toggle. A fresh dispatch makes the listener recompute from the original base.
        ViewCompat.requestApplyInsets(binding.launcherRoot)
    }

    private fun statusBarController() = WindowCompat.getInsetsController(window, binding.launcherRoot)

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
        val view = gadget.createView(container, gadgetHost, decoded.second)
        if (decoded.first == LauncherGadgetRegistry.KEY_WEATHER) {
            // The cell id lives here, not inside the gadget, so re-pointing a weather cell is wired at
            // the host rather than by handing every gadget its row in the database.
            view.setOnLongClickListener {
                openPicker(
                    LauncherWeatherLocationDialogFragment.newInstance(REQ_WEATHER_LOCATION, cellUi.cell.id),
                    LauncherWeatherLocationDialogFragment.TAG,
                )
                true
            }
            // S1560: a seeded weather cell carries no place, and its own tap opens a weather app - which
            // leaves the "no location" message with no visible way out. Only the unconfigured case is
            // redirected; a cell that already has a place keeps the gadget's own behaviour.
            if (WeatherLocation.decode(decoded.second) == null) {
                view.setOnClickListener {
                    openPicker(
                        LauncherWeatherLocationDialogFragment.newInstance(REQ_WEATHER_LOCATION, cellUi.cell.id),
                        LauncherWeatherLocationDialogFragment.TAG,
                    )
                }
            }
        }
        container.addView(view)
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

    /** S1401: the app list, reached from the taskbar button and from the swipe-up gesture alike. */
    private fun showAllApps() {
        // The desktop stays touchable behind the screen, so the button and the gesture must not each
        // open their own instance - the same guard the Start menu carries.
        if (supportFragmentManager.findFragmentByTag(LauncherAllAppsFragment.TAG) != null) return
        Timber.d("S1401: all-apps screen opened from the home surface")
        LauncherAllAppsFragment().show(supportFragmentManager, LauncherAllAppsFragment.TAG)
    }

    /**
     * Entry point for both add flows (edit mode only): an empty-slot tap passes that square, the taskbar
     * "+" passes [NO_SLOT] for both coordinates and lets the repository choose the position.
     */
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
     * The header goes down the ordinary placement route with its overlap check intact, and the full span
     * is what makes it reserve the whole row: the renderer widens a header to the live column count, so
     * storing it narrower would leave the rest of its row free in the database.
     */
    private fun onSectionChosen(sectionKey: String?) {
        if (sectionKey == null) {
            openPicker(
                LauncherCellContentPickerDialogFragment.newSectionInstance(pendingRow, pendingCol),
                LauncherCellContentPickerDialogFragment.TAG_SECTION,
            )
            return
        }
        Timber.d("S1428: restoring section '%s' at %d,%d", sectionKey, pendingRow, pendingCol)
        placeAtPendingSlot(
            kind = LauncherCellKind.SECTION,
            target = LauncherCellCommand.Section(sectionKey).encode(),
            spanW = LauncherGridGeometry.MAX_COLUMNS,
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
    ) {
        Timber.d("S1209: placing cell, slotless=%s", pendingRow == NO_SLOT)
        if (pendingRow == NO_SLOT) {
            viewModel.addCellInFirstFreeSlot(
                columns = currentColumns(),
                kind = kind,
                target = target,
                spanW = spanW,
                spanH = spanH,
                rememberFileListResourceId = rememberFileListResourceId,
            )
        } else {
            viewModel.addCell(
                rowIndex = pendingRow,
                colIndex = pendingCol,
                kind = kind,
                target = target,
                spanW = spanW,
                spanH = spanH,
                rememberFileListResourceId = rememberFileListResourceId,
            )
        }
    }

    /** The resource chain: pick a resource, then its mode for a shortcut, or hand it to a gadget. */
    private fun registerResourceListeners() {
        supportFragmentManager.setFragmentResultListener(REQ_RESOURCE_SHORTCUT, this) { _, bundle ->
            // S1423: "Create new.." carries no resource id - the shortcut is pinned by the creation
            // flow itself, so there is no mode to pick and no cell to place here.
            if (bundle.getBoolean(ResourcePickerDialogFragment.RESULT_CREATE_NEW, false)) {
                resourceCreateManager.startCreateResource(this)
                return@setFragmentResultListener
            }
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
    }

    /** One result key, two flows: a new cell has no id yet, an existing one is repointed in place. */
    private fun registerWeatherLocationListener() {
        supportFragmentManager.setFragmentResultListener(REQ_WEATHER_LOCATION, this) { _, bundle ->
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

    private fun addShortcut(command: LauncherCellCommand) {
        placeAtPendingSlot(
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
            currentViewportRows(),
            viewModel.sections.collapsed.value,
        )
    }

    private fun currentColumns(): Int = LauncherGridGeometry.columns(
        availableWidthDp = resources.configuration.screenWidthDp.toFloat(),
        densityFactor = viewModel.densityFactor.value,
    )

    /**
     * S1288: how many rows the visible desktop covers, so edit mode can fill it with empty slots.
     *
     * The scroll container is asked first because it is the exact answer - it already accounts for
     * insets, its own padding and whatever the taskbar leaves. Before the first layout pass it has no
     * size yet, and a rotation while editing rebinds in precisely that state, so the fallback derives
     * the same figure from the configuration - the source the column count above already trusts -
     * minus the taskbar the container stops above.
     */
    private fun currentViewportRows(): Int {
        val scroll = binding.launcherGridScroll
        val density = resources.displayMetrics.density
        val widthPx = if (scroll.width > 0) {
            scroll.width
        } else {
            (resources.configuration.screenWidthDp * density).toInt()
        }
        val heightPx = if (scroll.height > 0) {
            scroll.height
        } else {
            (resources.configuration.screenHeightDp * density).toInt() -
                resources.getDimensionPixelSize(R.dimen.launcher_taskbar_height)
        }
        val contentWidthPx = widthPx - scroll.paddingStart - scroll.paddingEnd
        return LauncherGridGeometry.rowsForViewport(
            availableHeightPx = heightPx,
            cellSizePx = LauncherGridGeometry.cellSizePx(contentWidthPx, currentColumns()),
        )
    }

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
        const val REQ_WEATHER_LOCATION = "launcher_weather_location"

        /**
         * S1209: "no square was pointed at" travelling through the picker's row/col arguments. A
         * sentinel rather than a parallel boolean field, because the picker round-trips the coordinates
         * through its result bundle and a separate flag could desync from them - a slotless flow that
         * came back carrying row 0 would silently overwrite the top-left square.
         */
        const val NO_SLOT = -1
    }
}
