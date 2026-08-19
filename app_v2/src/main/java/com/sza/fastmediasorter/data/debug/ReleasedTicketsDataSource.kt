package com.sza.fastmediasorter.data.debug

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Reads the released-tickets listing that the build step writes into assets for debug variants
 * (S1783). The selection - which release package, and in which order - is decided by the Gradle
 * task, so this reads and formats only.
 *
 * The asset is legitimately absent from a debug build produced before that task existed, and from
 * every release build. That is an empty list, not a failure.
 */
object ReleasedTicketsDataSource {

    private const val ASSET_PATH = "released_tickets.tsv"
    private const val FIELD_COUNT = 3

    /** One ticket carried by this build. */
    data class ReleasedTicket(
        val id: String,
        val slug: String,
        val status: String,
    )

    suspend fun read(context: Context): List<ReleasedTicket> = withContext(Dispatchers.IO) {
        val raw = runCatching { context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() } }
            .getOrElse {
                Timber.d("released tickets: asset %s absent in this build", ASSET_PATH)
                return@withContext emptyList()
            }

        raw.lineSequence()
            .mapNotNull { line ->
                // The generator writes no trailing newline, but a blank or short line must still be
                // skipped rather than becoming a half-empty row in the dialog.
                val parts = line.split('\t')
                if (parts.size < FIELD_COUNT || parts[0].isBlank()) {
                    null
                } else {
                    ReleasedTicket(id = parts[0].trim(), slug = parts[1].trim(), status = parts[2].trim())
                }
            }
            .toList()
    }
}
