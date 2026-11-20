package com.sza.fastmediasorter.ui.browse.transfer

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.FileOperationType

/**
 * Result text of a finished transfer. The move text is a plural resource so the noun agrees with the
 * count ("1 файл", "2 файла", "5 файлов"); the plain `%1$d files` strings could not.
 */
internal object TransferDoneMessage {

    fun format(context: Context, operationType: FileOperationType, count: Int): String = when (operationType) {
        FileOperationType.MOVE -> context.resources.getQuantityString(R.plurals.moved_n_files_plural, count, count)
        FileOperationType.DELETE -> context.getString(R.string.deleted_n_files, count)
        else -> context.getString(R.string.copied_n_files, count)
    }

    fun movedTo(context: Context, count: Int, destination: String): String =
        context.resources.getQuantityString(R.plurals.msg_move_success_count_plural, count, count, destination)
}
