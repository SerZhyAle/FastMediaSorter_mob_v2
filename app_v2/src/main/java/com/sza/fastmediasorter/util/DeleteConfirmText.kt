package com.sza.fastmediasorter.util

import android.content.res.Resources
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.usecase.DeletePathPolicy

/**
 * True when the delete a dialog is about to confirm moves [paths] to the trash instead of erasing them.
 *
 * Mirrors the condition `BrowseDeleteManager`, `DeleteFilesUseCase` and `PlayerDeleteUndoCoordinator`
 * apply when the delete runs, so the confirmation text cannot disagree with the action.
 */
fun deleteGoesToTrash(useTrash: Boolean, paths: List<String>): Boolean =
    useTrash && paths.isNotEmpty() && DeletePathPolicy.canUseSoftDelete(paths)

fun Resources.deleteFilesConfirmText(count: Int, toTrash: Boolean): String {
    val pluralId = if (toTrash) {
        R.plurals.delete_n_files_trash_confirm_plural
    } else {
        R.plurals.delete_n_files_confirm_plural
    }
    return getQuantityString(pluralId, count, count)
}
