package com.sza.fastmediasorter.ui.applaunchpanel.edit

import android.content.res.Configuration
import android.view.View
import androidx.activity.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityEditAppLaunchPanelBinding
import com.sza.fastmediasorter.domain.model.APP_LAUNCH_PANEL_SLOT_COUNT
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTileUi
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * Standalone Edit-panel screen: the user fills the fixed 15-slot grid by adding external apps and
 * uses long-press to move or remove a tile. No business logic lives here - all mutations go through
 * [EditAppLaunchPanelViewModel] (Rule 3). Reached from the panel dialog's Edit affordance / empty slot.
 */
@AndroidEntryPoint
class EditAppLaunchPanelActivity : BaseActivity<ActivityEditAppLaunchPanelBinding>() {

    private val viewModel: EditAppLaunchPanelViewModel by viewModels()
    private lateinit var tileAdapter: EditAppLaunchPanelTileAdapter

    override fun getViewBinding(): ActivityEditAppLaunchPanelBinding =
        ActivityEditAppLaunchPanelBinding.inflate(layoutInflater)

    override fun getInitialFocusView(): View = binding.recyclerView

    override fun getMouseScrollTargetView(): View = binding.recyclerView

    override fun setupViews() {
        binding.backButton.setOnClickListener { finish() }
        binding.resetButton.setOnClickListener { showResetConfirm() }

        tileAdapter = EditAppLaunchPanelTileAdapter(
            onTileClick = ::onTileClicked,
            onTileLongClick = ::showTileActionMenu,
        )
        val isLandscape =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val spanCount = if (isLandscape) 5 else 3
        binding.recyclerView.layoutManager = GridLayoutManager(this, spanCount)
        binding.recyclerView.adapter = tileAdapter

        supportFragmentManager.setFragmentResultListener(
            AppPickerDialogFragment.RESULT_KEY, this
        ) { _, bundle ->
            val slot = bundle.getInt(AppPickerDialogFragment.RESULT_SLOT)
            val packageName = bundle.getString(AppPickerDialogFragment.RESULT_PACKAGE)
                ?: return@setFragmentResultListener
            viewModel.addAppToSlot(slot, packageName)
        }

        supportFragmentManager.setFragmentResultListener(
            InternalRoutePickerDialogFragment.RESULT_KEY, this
        ) { _, bundle ->
            val slot = bundle.getInt(InternalRoutePickerDialogFragment.RESULT_SLOT)
            val routeKey = bundle.getString(InternalRoutePickerDialogFragment.RESULT_ROUTE_KEY)
                ?: return@setFragmentResultListener
            viewModel.addInternalFeatureToSlot(slot, routeKey)
        }

        supportFragmentManager.setFragmentResultListener(
            OsShortcutPickerDialogFragment.RESULT_KEY, this
        ) { _, bundle ->
            val slot = bundle.getInt(OsShortcutPickerDialogFragment.RESULT_SLOT)
            val targetKey = bundle.getString(OsShortcutPickerDialogFragment.RESULT_TARGET_KEY)
                ?: return@setFragmentResultListener
            viewModel.addOsShortcutToSlot(slot, targetKey)
        }

        supportFragmentManager.setFragmentResultListener(
            ResourcePickerDialogFragment.RESULT_KEY, this
        ) { _, bundle ->
            val slot = bundle.getInt(ResourcePickerDialogFragment.RESULT_SLOT)
            val resourceId = bundle.getLong(ResourcePickerDialogFragment.RESULT_RESOURCE_ID)
            viewModel.addResourceToSlot(slot, resourceId)
        }
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.tiles) { tiles -> tileAdapter.submit(tiles) }
    }

    private fun onTileClicked(tile: AppLaunchPanelTileUi) {
        // Empty slot or filled tile tap both lead to the add flow (filled tile tap == replace).
        showAddPathChooser(tile.slotIndex)
    }

    /**
     * Four-path pre-step (strategic S0663 §6.4): the user picks a category, then the matching picker
     * opens for the same slot. FastMediaSorter feature and resource are distinct top-level paths, so
     * no secondary feature/resource sub-choice is needed.
     */
    private fun showAddPathChooser(slot: Int) {
        Timber.d("S0682: add-tile chooser opened for slot $slot")
        val paths = arrayOf(
            getString(R.string.app_launch_panel_path_external_app),
            getString(R.string.app_launch_panel_path_app_feature),
            getString(R.string.app_launch_panel_path_app_resource),
            getString(R.string.app_launch_panel_path_os_part),
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_launch_panel_add_chooser_title)
            .setItems(paths) { dialog, which ->
                when (which) {
                    PATH_ANDROID_APP -> openAppPicker(slot)
                    PATH_APP_FEATURE -> openInternalRoutePicker(slot)
                    PATH_APP_RESOURCE -> openResourcePicker(slot)
                    PATH_OS_PART -> openOsShortcutPicker(slot)
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openAppPicker(slot: Int) {
        if (supportFragmentManager.findFragmentByTag(AppPickerDialogFragment.TAG) != null) return
        AppPickerDialogFragment.newInstance(slot)
            .show(supportFragmentManager, AppPickerDialogFragment.TAG)
    }

    private fun openInternalRoutePicker(slot: Int) {
        if (supportFragmentManager.findFragmentByTag(InternalRoutePickerDialogFragment.TAG) != null) return
        InternalRoutePickerDialogFragment.newInstance(slot)
            .show(supportFragmentManager, InternalRoutePickerDialogFragment.TAG)
    }

    private fun openOsShortcutPicker(slot: Int) {
        if (supportFragmentManager.findFragmentByTag(OsShortcutPickerDialogFragment.TAG) != null) return
        OsShortcutPickerDialogFragment.newInstance(slot)
            .show(supportFragmentManager, OsShortcutPickerDialogFragment.TAG)
    }

    private fun openResourcePicker(slot: Int) {
        if (supportFragmentManager.findFragmentByTag(ResourcePickerDialogFragment.TAG) != null) return
        ResourcePickerDialogFragment.newInstance(slot)
            .show(supportFragmentManager, ResourcePickerDialogFragment.TAG)
    }

    private fun showTileActionMenu(tile: AppLaunchPanelTileUi) {
        val actions = arrayOf(
            getString(R.string.app_launch_panel_action_move),
            getString(R.string.app_launch_panel_action_replace),
            getString(R.string.app_launch_panel_action_remove),
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(tile.label)
            .setItems(actions) { dialog, which ->
                when (which) {
                    ACTION_MOVE -> showMoveTargetPicker(tile.slotIndex)
                    ACTION_REPLACE -> showAddPathChooser(tile.slotIndex)
                    ACTION_REMOVE -> viewModel.removeTile(tile.slotIndex)
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Confirms before restoring the panel to its install-time default set (S0663). */
    private fun showResetConfirm() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_launch_panel_reset_confirm_title)
            .setMessage(R.string.app_launch_panel_reset_confirm_message)
            .setPositiveButton(R.string.app_launch_panel_reset) { dialog, _ ->
                viewModel.resetPanel()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showMoveTargetPicker(fromSlot: Int) {
        // Locked grid: moving means reassigning to another of the 15 fixed positions. Slot labels are
        // 1-based for the user; index 0 maps to slot 1.
        val slotLabels = (1..APP_LAUNCH_PANEL_SLOT_COUNT).map { it.toString() }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_launch_panel_action_move)
            .setItems(slotLabels) { dialog, which ->
                viewModel.moveTile(fromSlot, which)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private companion object {
        private const val ACTION_MOVE = 0
        private const val ACTION_REPLACE = 1
        private const val ACTION_REMOVE = 2

        private const val PATH_ANDROID_APP = 0
        private const val PATH_APP_FEATURE = 1
        private const val PATH_APP_RESOURCE = 2
        private const val PATH_OS_PART = 3
    }
}
