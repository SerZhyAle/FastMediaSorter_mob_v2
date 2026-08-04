package com.sza.fastmediasorter.ui.browse.helpers

import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.transfer.DirectoryOperationRefusal

/**
 * S1325: one place that turns a refused folder operation into the message the user reads.
 *
 * Both entry points into a folder transfer - the destination dialog and the system folder picker -
 * resolve the text here, so the same obstacle cannot be explained two different ways.
 */
@StringRes
fun refusalMessageRes(reason: DirectoryOperationRefusal.Reason): Int = when (reason) {
    DirectoryOperationRefusal.Reason.DESTINATION_INSIDE_SOURCE -> R.string.error_folder_into_itself
    DirectoryOperationRefusal.Reason.SAME_LOCATION -> R.string.error_folder_same_location
    DirectoryOperationRefusal.Reason.DESTINATION_NOT_SUPPORTED ->
        R.string.error_folder_destination_not_supported
}
