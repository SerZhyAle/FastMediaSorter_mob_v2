package com.sza.fastmediasorter.ui.browse.helpers

import android.content.Context
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.formatFileSize
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
    DirectoryOperationRefusal.Reason.INSUFFICIENT_SPACE -> R.string.error_reason_disk_space
}

/**
 * S1378: the text the user reads for [refusal].
 *
 * A space refusal names the medium it happened on and the amount still missing, which no bare
 * `@StringRes` can carry - a card and built-in storage are two different problems with two
 * different remedies. A refusal that measured nothing falls back to [refusalMessageRes].
 */
fun refusalMessage(context: Context, refusal: DirectoryOperationRefusal): String {
    val label = refusal.destinationLabel
    val missingBytes = refusal.missingBytes
    return if (label != null && missingBytes != null) {
        context.getString(
            R.string.error_destination_insufficient_space,
            label,
            formatFileSize(missingBytes)
        )
    } else {
        context.getString(refusalMessageRes(refusal.reason))
    }
}
