package com.sza.fastmediasorter.ui.browse.transfer

import android.content.Context
import android.widget.Toast
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import timber.log.Timber
import java.net.URLDecoder

/**
 * Renders the files a Copy/Move left out because the destination already held a same-name file
 * with Overwrite off. The handlers count those skips into a Success result, so without this line
 * the user reads "Copied N files" and cannot tell that other files were not transferred.
 */
internal object TransferSkipSummary {

    const val MAX_LISTED_NAMES = 5

    private const val CONTENT_SCHEME = "content://"

    /** Bare file names of skipped destination paths; SAF paths are document URIs with an encoded tail. */
    fun displayNames(paths: List<String>): List<String> =
        paths.map(::displayName).filter { it.isNotBlank() }

    /** At most [MAX_LISTED_NAMES] names, then `+N` for the rest; null when nothing was skipped. */
    fun listing(skippedCount: Int, names: List<String>): String? {
        if (skippedCount <= 0) return null
        val listed = names.take(MAX_LISTED_NAMES)
        val rest = skippedCount - listed.size
        val head = listed.joinToString(", ")
        return when {
            head.isEmpty() -> "$skippedCount"
            rest > 0 -> "$head +$rest"
            else -> head
        }
    }

    fun format(context: Context, skippedCount: Int, names: List<String>): String? =
        listing(skippedCount, names)?.let { context.getString(R.string.file_already_exists_skip, it) }

    /** [base] followed by the skip line on its own line, or [base] alone when nothing was skipped. */
    fun append(context: Context, base: String, skippedCount: Int, names: List<String>): String =
        format(context, skippedCount, names)?.let { "$base\n$it" } ?: base

    fun append(context: Context, base: String, result: FileOperationResult): String =
        append(context, base, skippedCount(result), displayNames(skippedPaths(result)))

    /** A toast that carries a list of skipped names needs the long duration to be readable. */
    fun toastLength(result: FileOperationResult): Int =
        if (skippedCount(result) > 0) Toast.LENGTH_LONG else Toast.LENGTH_SHORT

    fun skippedCount(result: FileOperationResult): Int = when (result) {
        is FileOperationResult.Success -> result.skippedCount
        is FileOperationResult.PartialSuccess -> result.skippedCount
        else -> 0
    }

    private fun skippedPaths(result: FileOperationResult): List<String> = when (result) {
        is FileOperationResult.Success -> result.skippedPaths
        is FileOperationResult.PartialSuccess -> result.skippedPaths
        else -> emptyList()
    }

    // A SAF document id reads "primary:Folder/name.jpg"; a file at the volume root has no slash after the colon.
    private fun displayName(path: String): String {
        if (!path.startsWith(CONTENT_SCHEME)) return path.trimEnd('/').substringAfterLast('/')
        return decodePercent(path).trimEnd('/').substringAfterLast('/').substringAfterLast(':')
    }

    // URLDecoder treats '+' as a space, which a file name may legitimately contain as a plus sign.
    private fun decodePercent(value: String): String = try {
        URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "TransferSkipSummary: undecodable skipped path kept as is")
        value
    }
}
