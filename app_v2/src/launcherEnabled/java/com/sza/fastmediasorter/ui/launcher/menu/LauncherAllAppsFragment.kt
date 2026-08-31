package com.sza.fastmediasorter.ui.launcher.menu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentLauncherAllAppsBinding
import com.sza.fastmediasorter.domain.model.launcher.InstalledApp
import com.sza.fastmediasorter.domain.model.launcher.InstalledAppSortOrder
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.ui.launcher.LauncherHomeViewModel
import com.sza.fastmediasorter.ui.launcher.grid.LauncherGridGeometry
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherAppActionMenuManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint

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

    private var _binding: FragmentLauncherAllAppsBinding? = null
    private val binding get() = _binding!!

    private val systemDialogsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getStringExtra(SYSTEM_DIALOG_REASON) == SYSTEM_DIALOG_REASON_HOME_KEY) {
                // The system can issue HOME after FragmentManager saves state; there is no UI state to retain.
                dismissAllowingStateLoss()
            }
        }
    }

    private val groupManager = LauncherAlphabeticalAppGroupManager()

    private var latestApps: List<InstalledApp> = emptyList()
    private val expandedGroupKeys = mutableSetOf<String>()

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
    )

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
                homeViewModel.placeAppOnDesktop(packageName, desktopColumns())
                dismiss()
            },
            pinToTaskbar = { packageName -> homeViewModel.pinAppToTaskbar(packageName) },
            appInfoIntent = { packageName -> homeViewModel.appInfoIntent(packageName) },
            uninstallIntent = { packageName -> homeViewModel.uninstallIntent(packageName) },
        )
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
        setUpGrid()
        binding.allAppsHome.setOnClickListener { dismiss() }
        ContextCompat.registerReceiver(
            requireContext(),
            systemDialogsReceiver,
            IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        binding.allAppsSearch.doAfterTextChanged { viewModel.setQuery(it?.toString().orEmpty()) }
        binding.allAppsSort.setOnClickListener { showOrderMenu(it) }

        collectOnLifecycle(viewModel.apps) { apps ->
            latestApps = apps
            renderGroups()
            applyEmptyState(apps.isEmpty())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // The popup anchors inside this screen and must not outlive it.
        actionMenuManager.dismiss()
        requireContext().unregisterReceiver(systemDialogsReceiver)
        _binding = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The launcher handles rotation itself, so the dialog must recalculate its grid instead of
        // waiting for a new app-list emission that may never arrive.
        renderGroups()
    }

    private fun setUpGrid() {
        val layoutManager = GridLayoutManager(requireContext(), desktopColumns())
        binding.allAppsGrid.layoutManager = layoutManager
        binding.allAppsGrid.adapter = appsAdapter
        layoutManager.spanSizeLookup = appsAdapter.getSpanSizeLookup(desktopColumns())
    }

    private fun renderGroups() {
        val spanCount = desktopColumns()
        val layoutManager = binding.allAppsGrid.layoutManager as GridLayoutManager
        layoutManager.spanCount = spanCount
        layoutManager.spanSizeLookup = appsAdapter.getSpanSizeLookup(spanCount)
        appsAdapter.submitGroups(groupManager.groupApps(latestApps, spanCount, expandedGroupKeys))
    }

    private fun toggleGroup(key: String) {
        if (!expandedGroupKeys.add(key)) expandedGroupKeys.remove(key)
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
