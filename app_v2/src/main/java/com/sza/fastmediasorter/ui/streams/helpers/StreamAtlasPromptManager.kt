package com.sza.fastmediasorter.ui.streams.helpers

import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.delivery.DeliverableInventory
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.delivery.DownloadProgress
import com.sza.fastmediasorter.domain.delivery.ExtensionItem
import com.sza.fastmediasorter.domain.delivery.ExtensionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * S1154: after a stream-catalog import, offers to download the channel-preview atlas when it is not
 * already installed. The offer routes through [DeliverableInventory] so download progress + delete stay
 * the real WorkManager path (the Extensions Manager row and the tray notification), and reuses the same
 * dismissible Snackbar-with-action affordance as the catalog-refresh suggestion - no one-off dialog.
 *
 * Owns all the decision logic (install-state check, once-per-session latch); the Activity only forwards
 * the `CatalogUpdated` event and supplies the anchor view (Rule 3/5).
 */
class StreamAtlasPromptManager(
    private val inventory: DeliverableInventory,
    private val scope: CoroutineScope,
    // Invoked once the payload is on disk, so the caller can pick it up without reopening the screen.
    private val onAtlasInstalled: suspend () -> Unit = {},
) {
    // Latched while an offer is on screen (or was accepted) so a rapid re-import cannot stack Snackbars.
    private var offered = false

    /** Offer the atlas download when it is not installed / in-flight and not already offered. */
    fun maybeOffer(anchor: View) {
        if (offered) return
        val item = atlasItem() ?: return
        scope.launch {
            val status = item.statusFlow.first()
            if (status is ExtensionStatus.Installed || status is ExtensionStatus.Downloading) return@launch
            if (offered) return@launch
            offered = true
            // INDEFINITE: the offer fires together with the "catalog updated" toast, and a timed
            // Snackbar behind that toast was easy to miss entirely (owner report 2026-07-26). It now
            // waits for a decision - accept, or swipe it away.
            Snackbar.make(anchor, R.string.streams_atlas_prompt_message, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.streams_atlas_prompt_action) { startDownload(item) }
                .addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        // Only an accepted offer stays latched. A swipe/replacement dismissal must not
                        // silence the atlas for the rest of the session: every later catalog update
                        // offers it again until it is actually installed.
                        if (event != DISMISS_EVENT_ACTION) offered = false
                    }
                })
                .show()
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

    private fun atlasItem(): ExtensionItem.Module? =
        inventory.getExtensions()
            .filterIsInstance<ExtensionItem.Module>()
            .firstOrNull { it.set == DeliverableSet.CHANNEL_PREVIEW_ATLAS }
}
