package com.sza.fastmediasorter.ui.launcher.helpers

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogDeleteBinding
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi
import com.sza.fastmediasorter.domain.model.launcher.LauncherSectionMembership
import com.sza.fastmediasorter.ui.launcher.LauncherHomeViewModel
import com.sza.fastmediasorter.ui.launcher.picker.LauncherSectionNameDialogFragment
import com.sza.fastmediasorter.ui.launcher.section.LauncherSectionActionsSheet
import com.sza.fastmediasorter.util.showBoundToHost

/**
 * S2268: the long-press menu of a desktop SECTION header and the two dialogs it opens - rename, resort,
 * delete. Lifted out of `LauncherHomeActivity` unchanged; the activity kept no logic of its own here, it
 * merely hosted three private methods that only ever talk to the section model (Rule 3).
 *
 * @param currentColumns the grid width resort needs, read at the moment the user picks it rather than
 *   captured, because a rotation between opening the sheet and tapping the row changes it.
 */
class LauncherSectionActionsManager(
    private val activity: AppCompatActivity,
    private val viewModel: LauncherHomeViewModel,
    private val currentColumns: () -> Int,
) {

    fun show(cellUi: LauncherCellUi): Boolean {
        val sheet = LauncherSectionActionsSheet()
        sheet.items = listOf(
            LauncherSectionActionsSheet.ActionItem(
                action = LauncherSectionActionsSheet.Action.RENAME,
                label = activity.getString(R.string.launcher_section_action_rename),
                iconResId = R.drawable.ic_rename,
            ),
            LauncherSectionActionsSheet.ActionItem(
                action = LauncherSectionActionsSheet.Action.RESORT,
                label = activity.getString(R.string.launcher_section_action_resort),
                iconResId = R.drawable.ic_sort,
            ),
            LauncherSectionActionsSheet.ActionItem(
                action = LauncherSectionActionsSheet.Action.DELETE,
                label = activity.getString(R.string.launcher_section_action_delete),
                iconResId = R.drawable.ic_delete,
            ),
        )
        sheet.onItemClick = { action ->
            when (action) {
                LauncherSectionActionsSheet.Action.RENAME -> openRenameDialog(cellUi)
                LauncherSectionActionsSheet.Action.RESORT ->
                    viewModel.resortSection(cellUi.cell.id, currentColumns())
                LauncherSectionActionsSheet.Action.DELETE -> confirmDelete(cellUi)
            }
        }
        sheet.show(activity.supportFragmentManager, SHEET_TAG)
        return true
    }

    private fun confirmDelete(cellUi: LauncherCellUi) {
        val desktop = viewModel.cells.value.map { it.cell }
        val sections = LauncherSectionMembership.sectionsInOrder(desktop)
        val contentCount = desktop.count {
            it.id != cellUi.cell.id &&
                LauncherSectionMembership.ownerOf(it, sections)?.id == cellUi.cell.id
        }
        if (contentCount <= SINGLE_SECTION_CONTENT_CELL) {
            viewModel.deleteSection(cellUi.cell.id)
            return
        }

        val dialogBinding = DialogDeleteBinding.inflate(activity.layoutInflater)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(dialogBinding.root)
            .create()
        dialogBinding.tvDialogTitle.setText(R.string.launcher_section_delete_confirm_title)
        dialogBinding.tvMessage.text =
            activity.getString(R.string.launcher_section_delete_confirm_message, contentCount)
        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnDelete.setOnClickListener {
            viewModel.deleteSection(cellUi.cell.id)
            dialog.dismiss()
        }
        dialog.showBoundToHost(activity)
    }

    private fun openRenameDialog(cellUi: LauncherCellUi) {
        val requestKey = "rename_section_${cellUi.cell.id}"
        val initialName = cellUi.visual?.label?.toString().orEmpty()
        activity.supportFragmentManager.setFragmentResultListener(requestKey, activity) { _, bundle ->
            val newName = bundle.getString(LauncherSectionNameDialogFragment.RESULT_NAME)
            if (!newName.isNullOrBlank()) {
                viewModel.renameSection(cellUi.cell.id, newName)
            }
        }
        LauncherSectionNameDialogFragment.newInstance(requestKey, initialName)
            .show(activity.supportFragmentManager, LauncherSectionNameDialogFragment.TAG)
    }

    companion object {
        private const val SHEET_TAG = "LauncherSectionActionsSheet"

        /** A section holding one cell is deleted outright - there is nothing to warn about. */
        private const val SINGLE_SECTION_CONTENT_CELL = 1
    }
}
