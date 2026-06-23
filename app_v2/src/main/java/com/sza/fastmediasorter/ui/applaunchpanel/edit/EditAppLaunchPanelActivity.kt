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
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.tiles) { tiles -> tileAdapter.submit(tiles) }
    }

    private fun onTileClicked(tile: AppLaunchPanelTileUi) {
        // Empty slot or filled tile tap both lead to the picker (filled tile tap == replace).
        openAppPicker(tile.slotIndex)
    }

    private fun openAppPicker(slot: Int) {
        if (supportFragmentManager.findFragmentByTag(AppPickerDialogFragment.TAG) != null) return
        AppPickerDialogFragment.newInstance(slot)
            .show(supportFragmentManager, AppPickerDialogFragment.TAG)
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
                    ACTION_REPLACE -> openAppPicker(tile.slotIndex)
                    ACTION_REMOVE -> viewModel.removeTile(tile.slotIndex)
                }
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
    }
}
