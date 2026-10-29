package com.sza.fastmediasorter.wear.ui.player.helpers

import com.sza.fastmediasorter.wear.domain.repository.SelectedMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.io.File

/**
 * S4471: fetches the next network audio track while the current one plays, so the gap at a track change
 * holds no transfer.
 *
 * It holds at most one fetch, keyed by source and stream uri - the pair `playFile` builds its selection
 * from, which is what lets the load that follows recognise the copy. [take] hands the fetch over and
 * forgets it, so a later [cancel] can never cancel a deferred somebody is already awaiting.
 */
class AudioPrefetchManager(
    private val scope: CoroutineScope,
    private val fetch: suspend (SelectedMedia) -> Result<File>
) {

    private var pending: Pending? = null

    /** Starts the fetch of [selection] unless that very one is held; a fetch of another file is dropped. */
    fun prefetch(selection: SelectedMedia) {
        val key = keyOf(selection)
        if (pending?.key == key) {
            return
        }
        pending?.deferred?.cancel()
        pending = Pending(key, scope.async { fetch(selection) })
    }

    /** The fetch started for [selection], or null when none was; the caller owns it from here. */
    fun take(selection: SelectedMedia): Deferred<Result<File>>? {
        val held = pending ?: return null
        return if (held.key == keyOf(selection)) {
            pending = null
            held.deferred
        } else {
            null
        }
    }

    fun cancel() {
        pending?.deferred?.cancel()
        pending = null
    }

    private fun keyOf(selection: SelectedMedia): String = "${selection.sourceId}|${selection.streamUri}"

    private class Pending(val key: String, val deferred: Deferred<Result<File>>)
}
