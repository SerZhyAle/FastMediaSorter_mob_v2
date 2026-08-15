package com.sza.fastmediasorter.ui.streams.helpers

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.delivery.ArtworkManifestSource
import com.sza.fastmediasorter.domain.delivery.DeliverableInventory
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.delivery.DownloadProgress
import com.sza.fastmediasorter.domain.delivery.ExtensionItem
import com.sza.fastmediasorter.domain.delivery.ExtensionStatus
import com.sza.fastmediasorter.util.showBoundToHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S1154: after a stream-catalog import, offers to download an artwork atlas when it is not already
 * installed. The offer routes through [DeliverableInventory] so download progress + delete stay the real
 * WorkManager path (the Extensions Manager row and the tray notification).
 *
 * S1201: one instance per payload ([set] + [messageRes]) rather than one hard-coded to the preview
 * atlas. The two atlases are downloaded and refused independently, so their offers must be too.
 *
 * S1481: the offer is a modal dialog, not a Snackbar. The Snackbar shipped in S1154 and was raised
 * twice as missed - it appears at the bottom edge, at the same moment as the "catalog updated" toast,
 * and asking for pictures after a refresh is not an incidental notice the user can be expected to
 * catch out of the corner of their eye. Both payloads are now offered on every catalog update: the
 * second dialog follows the first one's dismissal, so they queue instead of overlapping.
 *
 * Owns all the decision logic (install-state check, per-session latch); the Activity only forwards the
 * `CatalogUpdated` event and supplies the context (Rule 3/5).
 */
class StreamAtlasPromptManager(
    private val inventory: DeliverableInventory,
    private val scope: CoroutineScope,
    private val set: DeliverableSet,
    @androidx.annotation.StringRes private val messageRes: Int,
    // S1483: supplies the payload size shown in the offer; already fetched for the staleness verdict.
    private val manifest: ArtworkManifestSource,
    // Invoked once the payload is on disk, so the caller can pick it up without reopening the screen.
    private val onAtlasInstalled: suspend () -> Unit = {},
) {
    // Latched while an offer is on screen (or was accepted) so a rapid re-import cannot stack Snackbars.
    private var offered = false

    /**
     * Offer the atlas download when it is not installed / in-flight and not already offered.
     *
     * [onOfferSettled] runs once this payload can no longer produce a dialog: it had nothing to ask
     * about (already installed, a download is running, or this build has no such row), or its dialog
     * was answered. It is the hand-off point for the next payload's offer, so the two dialogs queue
     * rather than stack on top of each other.
     */
    fun maybeOffer(context: Context, onOfferSettled: (() -> Unit)? = null) {
        if (offered) {
            onOfferSettled?.invoke()
            return
        }
        val item = atlasItem() ?: run {
            onOfferSettled?.invoke()
            return
        }
        scope.launch {
            val status = item.statusFlow.first()
            if (status is ExtensionStatus.Installed || status is ExtensionStatus.Downloading) {
                onOfferSettled?.invoke()
                return@launch
            }
            if (offered) {
                onOfferSettled?.invoke()
                return@launch
            }
            offered = true
            // S1200: an installed-but-stale payload is the case the owner hit - "already installed"
            // used to silence the offer, so a rebuilt atlas reached nobody. Same offer, different
            // sentence: the user is not missing pictures, they are missing newer ones.
            val message = if (status is ExtensionStatus.UpdateAvailable) {
                R.string.streams_atlas_update_prompt_message
            } else {
                messageRes
            }
            // S1483: name the size. These payloads are 8-11 MB and used to start downloading with no
            // indication of that; the manifest already carries the number, so asking costs nothing.
            val megabytes = manifest.sizeOf(set)?.let { (it + BYTES_PER_MB - 1) / BYTES_PER_MB }
            val body = context.getString(message).let { text ->
                if (megabytes == null || megabytes <= 0L) {
                    text
                } else {
                    text + "\n\n" + context.getString(R.string.streams_atlas_prompt_size, megabytes.toInt())
                }
            }
            var accepted = false
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.streams_atlas_prompt_title)
                .setMessage(body)
                .setPositiveButton(R.string.streams_atlas_prompt_action) { _, _ ->
                    accepted = true
                    startDownload(item)
                }
                .setNegativeButton(R.string.later, null)
                .setOnDismissListener {
                    // Anything other than accepting - "Later", back, a tap outside - releases the latch,
                    // so the next catalog update asks again. Only an accepted offer stays latched, and
                    // by then the payload is downloading anyway. This is what "offer after every
                    // refresh" has to mean, or a single stray dismissal mutes it for the session.
                    if (!accepted) offered = false
                    onOfferSettled?.invoke()
                }
                .showBoundToHost(context)
        }
    }

    private fun startDownload(item: ExtensionItem.Module) {
        // Collecting drives the cold download Flow; progress itself is surfaced by the WorkManager tray
        // notification and the Extensions Manager row, so nothing extra is rendered here.
        scope.launch {
            inventory.download(item).collect { progress ->
                // The grid caches the url->index map from screen setup, so an atlas that lands AFTER
                // that read stayed unused until the screen was reopened (emulator run 2026-07-26).
                if (progress == DownloadProgress.Installed) onAtlasInstalled()
            }
        }
    }

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024L
    }

    private fun atlasItem(): ExtensionItem.Module? =
        inventory.getExtensions()
            .filterIsInstance<ExtensionItem.Module>()
            .firstOrNull { it.set == set }
}
