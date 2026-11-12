package com.sza.fastmediasorter.ui.launcher.menu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.screencapture.MenuScreenshotLauncher
import com.sza.fastmediasorter.core.screencapture.ScreenshotGestureActionDispatcher
import com.sza.fastmediasorter.databinding.FragmentLauncherAllAppsBinding
import com.sza.fastmediasorter.domain.model.LauncherAllAppsSwipeDirection
import com.sza.fastmediasorter.domain.model.launcher.AppCategoryCatalog
import com.sza.fastmediasorter.domain.model.launcher.InstalledApp
import com.sza.fastmediasorter.domain.model.launcher.InstalledAppSortOrder
import com.sza.fastmediasorter.domain.model.launcher.LauncherAllAppsPreviewGeometry
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.launcher.LauncherHomeActivity
import com.sza.fastmediasorter.ui.launcher.LauncherHomeViewModel
import com.sza.fastmediasorter.ui.launcher.grid.LauncherGridGeometry
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherAllAppsGestureManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherAllAppsSwipeActionHandler
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherAppActionMenuManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S1401: the full-screen list of every installed app - search, order picker, grid, action menu.
 *
 * A [DialogFragment] rather than an Activity so it opens over the desktop without leaving the launcher
 * task, which is what lets Back return to the desktop instead of to whatever sits behind Home.
 */
@AndroidEntryPoint
class LauncherAllAppsFragment : DialogFragment() {

    private val viewModel: LauncherAllAppsViewModel by viewModels()

    /** Shared with the desktop: launching, placing and pinning all belong to the home surface. */
    private val homeViewModel: LauncherHomeViewModel by activityViewModels()

    @Inject
    lateinit var screenshotGestureActionDispatcher: ScreenshotGestureActionDispatcher

    @Inject
    lateinit var menuScreenshotLaunchers: Set<@JvmSuppressWildcards MenuScreenshotLauncher>

    private var _binding: FragmentLauncherAllAppsBinding? = null
    private val binding get() = _binding!!

    /** S2304: null outside the view lifecycle, which is what the swipe listener is bound to. */
    private var swipeActionHandler: LauncherAllAppsSwipeActionHandler? = null

    /** Detached in onDestroyView: the RecyclerView outlives this fragment's view on a re-inflate. */
    private var swipeTouchListener: RecyclerView.OnItemTouchListener? = null
    private var scrollListener: RecyclerView.OnScrollListener? = null

    private val systemDialogsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getStringExtra(SYSTEM_DIALOG_REASON) == SYSTEM_DIALOG_REASON_HOME_KEY) {
                // The system can issue HOME after FragmentManager saves state; there is no UI state to retain.
                dismissAllowingStateLoss()
            }
        }
    }

    private val groupManager = LauncherAlphabeticalAppGroupManager()

    private val categoryManager = LauncherCategoryAppGroupManager()
    private val categoryLabels by lazy { AppCategoryLabelResolver(resources) }

    private var latestApps: List<InstalledApp> = emptyList()
    private var latestCatalog: AppCategoryCatalog = AppCategoryCatalog.EMPTY
    private val expandedGroupKeys = mutableSetOf<String>()

    private val stateManager by lazy {
        LauncherAllAppsStateManager(requireContext().getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE))
    }
    private val expandedCategoryKeys by lazy { stateManager.expandedKeys().toMutableSet() }
    private var catalogReady = false
    private var restorePending = true
    private var currentSections: List<LauncherAppGroupSection> = emptyList()

    /** S2736: resolved from the laid-out grid; the floor is what the block occupied before it was measured. */
    private var previewRows = LauncherAllAppsPreviewGeometry.MIN_PREVIEW_ROWS
    private var letterGroupCount = 0
    private var singleAppGroupCount = 0
    private var previewRowsObserver: ViewTreeObserver.OnGlobalLayoutListener? = null

    private val appsAdapter = LauncherAppGridAdapter(
        onAppClick = { app ->
            homeViewModel.run(LauncherCellCommand.App(app.id))
            dismiss()
        },
        onAppLongClick = { view, app ->
            actionMenuManager.show(view, app.id)
            true
        },
        onGroupToggle = ::toggleGroup,
        onGroupLongClick = ::enterCategoryReorder,
    )

    private val reorderManager = LauncherCategoryReorderManager(
        onModeChanged = ::renderGroups,
        onMove = { categoryId, offset ->
            viewModel.moveCategory(categoryId, categoryManager.displayedCategoryIds(currentSections), offset)
        },
    )

    // Built on first use and closed in onDestroyView, so no chooser outlives the view it was opened from.
    private val categoryDialog by lazy {
        LauncherAppCategoryDialogManager(
            context = requireContext(),
            labels = categoryLabels,
            onSave = viewModel::setAppCategories,
            onCreate = viewModel::createCategory,
        )
    }

    // Built lazily on the first long press: most visits never open the menu at all. The scope is the
    // fragment's, not the view's: a `by lazy` built before a rotation would otherwise keep the dead
    // view lifecycle's scope forever and the menu would silently stop opening. The window itself is
    // still closed in onDestroyView, which is what binds it to the view.
    private val actionMenuManager by lazy {
        LauncherAppActionMenuManager(
            scope = lifecycleScope,
            queryAppShortcuts = { packageName -> homeViewModel.appShortcutsOf(packageName) },
            startAppShortcut = { shortcut, bounds -> homeViewModel.launchAppShortcut(shortcut, bounds) },
            launchApp = { packageName ->
                homeViewModel.run(LauncherCellCommand.App(packageName))
                dismiss()
            },
            placeOnDesktop = { packageName ->
                // S4492: the desktop's own count - its viewport is narrower than this window beside a side taskbar.
                val columns = homeViewModel.desktopColumns.value.takeIf { it > 0 } ?: desktopColumns()
                homeViewModel.placeAppOnDesktop(packageName, columns)
                dismiss()
            },
            pinToTaskbar = { packageName -> homeViewModel.pinAppToTaskbar(packageName) },
            appInfoIntent = { packageName -> homeViewModel.appInfoIntent(packageName) },
            uninstallIntent = { packageName -> homeViewModel.uninstallIntent(packageName) },
        ).apply {
            setCategories = { packageName ->
                val label = latestApps.firstOrNull { it.packageName == packageName }?.label ?: packageName
                categoryDialog.show(packageName, label, latestCatalog)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_FastMediaSorter_Launcher_AllApps)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLauncherAllAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        restorePending = true
        setUpGrid()
        attachPreviewRowMeasurement()
        attachSwipeGestures()
        binding.allAppsHome.setOnClickListener { dismiss() }
        // The Android 12 deprecation restricts SENDING this broadcast; receiving it is how HOME closes the drawer.
        @Suppress("DEPRECATION")
        val closeSystemDialogsFilter = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        ContextCompat.registerReceiver(
            requireContext(),
            systemDialogsReceiver,
            closeSystemDialogsFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        binding.allAppsSearch.doAfterTextChanged {
            val query = it?.toString().orEmpty()
            if (viewModel.query.value.isBlank() && query.isNotBlank()) saveCategoryAnchor()
            if (query.isBlank() && viewModel.query.value.isNotBlank()) restorePending = true
            viewModel.setQuery(query)
        }
        binding.allAppsSort.setOnClickListener { showOrderMenu(it) }

        collectOnLifecycle(viewModel.apps) { apps ->
            latestApps = apps
            renderGroups()
            applyEmptyState(apps.isEmpty())
        }
        collectOnLifecycle(viewModel.catalog) { catalog ->
            if (catalog != null) {
                latestCatalog = catalog
                catalogReady = true
                renderGroups()
            }
        }
        collectOnLifecycle(viewModel.order) { renderGroups() }
        collectOnLifecycle(viewModel.categorySaveFailed) {
            Toast.makeText(requireContext(), R.string.launcher_app_category_save_failed, Toast.LENGTH_SHORT).show()
        }
        binding.allAppsReorderUp.setOnClickListener { reorderManager.moveUp() }
        binding.allAppsReorderDown.setOnClickListener { reorderManager.moveDown() }
        binding.allAppsReorderDone.setOnClickListener { reorderManager.exit() }
    }

    private fun enterCategoryReorder(key: String): Boolean = isCategoryMode() && reorderManager.enter(key)

    override fun onStart() {
        super.onStart()
        // The grid and the search field consume keys themselves; the delegate adds the Escape route
        // that closes the overlay, matching what Back already does.
        DialogKeyboardDelegate.applyToDialogFragment(dialog, onConfirm = {})
    }

    override fun onStop() {
        saveCategoryAnchor()
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // The popup anchors inside this screen and must not outlive it.
        actionMenuManager.dismiss()
        categoryDialog.dismiss()
        requireContext().unregisterReceiver(systemDialogsReceiver)
        swipeTouchListener?.let { binding.allAppsGrid.removeOnItemTouchListener(it) }
        swipeTouchListener = null
        scrollListener?.let { binding.allAppsGrid.removeOnScrollListener(it) }
        scrollListener = null
        previewRowsObserver?.let { binding.allAppsGrid.viewTreeObserver.removeOnGlobalLayoutListener(it) }
        previewRowsObserver = null
        swipeActionHandler = null
        _binding = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        saveCategoryAnchor()
        super.onConfigurationChanged(newConfig)
        restorePending = true
        // The launcher handles rotation itself, so the dialog must recalculate its grid instead of
        // waiting for a new app-list emission that may never arrive.
        renderGroups()
    }

    private fun setUpGrid() {
        val layoutManager = GridLayoutManager(requireContext(), desktopColumns())
        binding.allAppsGrid.layoutManager = layoutManager
        binding.allAppsGrid.adapter = appsAdapter
        binding.allAppsGrid.itemAnimator = null
        val listener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) saveCategoryAnchor()
            }
        }
        scrollListener = listener
        binding.allAppsGrid.addOnScrollListener(listener)
        layoutManager.spanSizeLookup = appsAdapter.getSpanSizeLookup(desktopColumns())
    }

    /**
     * S2304: the recognizer reads the grid's own event stream through an item-touch listener that never
     * claims it, so taps, long presses and scrolling keep their normal dispatch. A vertical direction
     * still fires only at the matching scroll boundary, which is what keeps an ordinary scroll from
     * reading as a swipe.
     */
    private fun attachSwipeGestures() {
        swipeActionHandler = LauncherAllAppsSwipeActionHandler(
            activity = requireActivity(),
            actionDispatcher = screenshotGestureActionDispatcher,
            screenshotLaunchers = menuScreenshotLaunchers,
            onBackToDesktop = ::dismiss,
            onExpandAllApps = ::expandFullAppList,
            onLockScreen = {
                val host = activity as? LauncherHomeActivity
                dismissAllowingStateLoss()
                host?.lockScreen()
            },
        )
        val gestureManager = LauncherAllAppsGestureManager(
            container = binding.allAppsGrid,
            viewport = binding.allAppsGrid,
            isEnabled = { true },
            // The whole grid is swipeable: on a full-screen list of cells there is almost no free space
            // to start from, and the recognizer already separates a fling from a tap by slop and velocity.
            isTouchOnInteractiveCell = { false },
            onSwipe = { direction, _ -> dispatchSwipe(direction) },
        )
        val listener = object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                gestureManager.onTouchEvent(e)
                return false
            }
        }
        swipeTouchListener = listener
        binding.allAppsGrid.addOnItemTouchListener(listener)
    }

    private fun dispatchSwipe(direction: LauncherAllAppsGestureManager.DesktopSwipeDirection) {
        val handler = swipeActionHandler ?: return
        val slot = when (direction) {
            LauncherAllAppsGestureManager.DesktopSwipeDirection.UP -> LauncherAllAppsSwipeDirection.UP
            LauncherAllAppsGestureManager.DesktopSwipeDirection.DOWN -> LauncherAllAppsSwipeDirection.DOWN
            LauncherAllAppsGestureManager.DesktopSwipeDirection.LEFT -> LauncherAllAppsSwipeDirection.LEFT
            LauncherAllAppsGestureManager.DesktopSwipeDirection.RIGHT -> LauncherAllAppsSwipeDirection.RIGHT
        }
        val settings = viewModel.appSettings.value
        viewLifecycleOwner.lifecycleScope.launch {
            handler.handle(slot.actionOf(settings), slot.payloadOf(settings))
        }
    }

    /**
     * Expands the preview section into the full app list. One-way on purpose: the section header tap
     * stays the way back to the alphabetical view, so a repeated swipe cannot flip the list under the
     * finger.
     */
    private fun expandFullAppList() {
        if (expandedGroupKeys.add(LauncherAlphabeticalAppGroupManager.KEY_PREVIEW)) {
            renderGroups()
        }
    }

    private fun renderGroups() {
        val spanCount = desktopColumns()
        val layoutManager = binding.allAppsGrid.layoutManager as GridLayoutManager
        layoutManager.spanCount = spanCount
        layoutManager.spanSizeLookup = appsAdapter.getSpanSizeLookup(spanCount)
        // The reorder mode belongs to the category view; switching the order away ends it.
        if (!isCategoryMode() && reorderManager.isActive) reorderManager.exit()
        val groups = if (isCategoryMode()) {
            categoryManager.groupApps(
                apps = latestApps,
                catalog = latestCatalog,
                expandedKeys = expandedCategoryKeys,
                reorderTargetKey = reorderManager.targetKey,
                labelOf = categoryLabels::labelOf,
                unknownLabel = categoryLabels.unknownLabel(),
            )
        } else {
            groupManager.groupApps(latestApps, spanCount, expandedGroupKeys, previewRows)
        }
        letterGroupCount = groups.count { !it.isPreview && !it.isCategory }
        singleAppGroupCount = groups.count { it.isSingleApp }
        currentSections = groups
        appsAdapter.submitGroups(groups)
        restoreCategoryAnchor(layoutManager)
        val targetTitle = groups.firstOrNull { it.isReorderTarget }?.title
        reorderManager.bind(binding.allAppsReorderBar, binding.allAppsReorderTitle, targetTitle)
    }

    private fun restoreCategoryAnchor(layoutManager: GridLayoutManager) {
        if (!restorePending || !isCategoryMode() || !catalogReady) return
        if (latestApps.isEmpty() || viewModel.query.value.isNotBlank()) return
        Timber.d("S4548: restoring category drawer")
        val anchor = stateManager.anchor()
        val position = anchor?.let { LauncherAllAppsStateManager.resolvePosition(it, appsAdapter.itemKeys()) }
        if (position != null && anchor != null) layoutManager.scrollToPositionWithOffset(position, anchor.offset)
        restorePending = false
    }

    private fun saveCategoryAnchor() {
        val grid = _binding?.allAppsGrid ?: return
        if (restorePending || !isCategoryMode() || viewModel.query.value.isNotBlank()) return
        (grid.layoutManager as? GridLayoutManager)?.let { manager ->
            val position = manager.findFirstVisibleItemPosition()
            val key = appsAdapter.itemKeys().getOrNull(position)
            val child = manager.findViewByPosition(position)
            if (key != null && child != null) {
                stateManager.saveAnchor(LauncherAllAppsStateManager.Anchor(key, position, child.top - grid.paddingTop))
            }
        }
    }

    private fun isCategoryMode(): Boolean = viewModel.order.value == InstalledAppSortOrder.CATEGORY

    /**
     * S2736: the preview block takes the height the letter tiles leave free, and that height is only
     * known once the grid has laid out - the app cell follows the font scale, so no dimension resource
     * describes it. The list is rebuilt only when the resolved count actually changes, which is what
     * stops the recalculation from feeding itself a fresh layout pass forever.
     */
    private fun attachPreviewRowMeasurement() {
        val observer = ViewTreeObserver.OnGlobalLayoutListener { recalculatePreviewRows() }
        previewRowsObserver = observer
        binding.allAppsGrid.viewTreeObserver.addOnGlobalLayoutListener(observer)
    }

    private fun recalculatePreviewRows() {
        // Category mode draws no preview block, so there is no row count to resolve.
        val grid = _binding?.allAppsGrid?.takeUnless { isCategoryMode() } ?: return
        val heights = appsAdapter.measureRowHeights(grid)
        val resolved = LauncherAllAppsPreviewGeometry.previewRows(
            viewportPx = grid.height - grid.paddingTop - grid.paddingBottom,
            previewHeaderPx = heights.previewHeaderPx,
            appRowPx = heights.appCellPx,
            letterTilePx = heights.letterTilePx,
            letterGroups = letterGroupCount,
            singleAppGroups = singleAppGroupCount,
            columns = desktopColumns(),
        )
        if (resolved == previewRows) return
        previewRows = resolved
        // Posted, not immediate: this runs inside a layout traversal, and RecyclerView refuses an
        // adapter change while it is computing one.
        grid.post { if (_binding != null) renderGroups() }
    }

    private fun toggleGroup(key: String) {
        val category = LauncherCategoryAppGroupManager.isCategoryKey(key)
        val keys = if (category) expandedCategoryKeys else expandedGroupKeys
        if (!keys.add(key)) keys.remove(key)
        if (category) {
            Timber.d("S4548: category group toggled")
            stateManager.saveExpandedKeys(keys)
        }
        renderGroups()
    }

    /**
     * Empty only counts as empty when the user asked for something: an empty list with no query means
     * the cache has not filled yet, and a "nothing found" message would be wrong about why.
     */
    private fun applyEmptyState(isEmpty: Boolean) {
        val query = binding.allAppsSearch.text?.toString().orEmpty()
        val show = isEmpty && query.isNotBlank()
        binding.allAppsEmpty.isVisible = show
        if (show) {
            binding.allAppsEmpty.text = getString(R.string.launcher_all_apps_empty, query)
        }
    }

    private fun showOrderMenu(anchor: View) {
        val popup = PopupMenu(anchor.context, anchor)
        val current = viewModel.order.value
        ORDER_LABELS.entries.forEachIndexed { index, (order, labelRes) ->
            popup.menu.add(Menu.NONE, index, index, labelRes).apply {
                isCheckable = true
                isChecked = order == current
            }
        }
        popup.menu.setGroupCheckable(Menu.NONE, true, true)
        popup.menu.add(Menu.NONE, REVERSE_ITEM_ID, ORDER_LABELS.size, R.string.launcher_all_apps_sort_reverse)
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == REVERSE_ITEM_ID) {
                viewModel.toggleDirection()
            } else {
                viewModel.setOrder(ORDER_LABELS.keys.elementAt(item.itemId))
            }
            true
        }
        popup.show()
    }

    private fun desktopColumns(): Int = LauncherGridGeometry.columns(
        availableWidthDp = resources.configuration.screenWidthDp.toFloat(),
        densityFactor = homeViewModel.densityFactor.value,
    )

    companion object {
        private const val STATE_PREFERENCES = "launcher_all_apps_state"
        const val TAG = "launcher_all_apps"

        /** Kept clear of the order ids, which are the map's indices. */
        private const val REVERSE_ITEM_ID = 100
        private const val SYSTEM_DIALOG_REASON = "reason"
        private const val SYSTEM_DIALOG_REASON_HOME_KEY = "homekey"

        private val ORDER_LABELS = linkedMapOf(
            InstalledAppSortOrder.LABEL to R.string.launcher_all_apps_sort_label,
            InstalledAppSortOrder.INSTALL_DATE to R.string.launcher_all_apps_sort_install_date,
            InstalledAppSortOrder.UPDATE_DATE to R.string.launcher_all_apps_sort_update_date,
            InstalledAppSortOrder.LAUNCH_FREQUENCY to R.string.launcher_all_apps_sort_frequency,
            InstalledAppSortOrder.CATEGORY to R.string.launcher_all_apps_sort_category,
        )
    }
}
