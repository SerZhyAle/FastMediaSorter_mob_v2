package com.sza.fastmediasorter.ui.streams.helpers

import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.delivery.DeliverableInventory
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
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
) {
    // Latched once an offer has been shown this session so a rapid re-import cannot stack Snackbars.
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
            Snackbar.make(anchor, R.string.streams_atlas_prompt_message, Snackbar.LENGTH_LONG)
                .setAction(R.string.streams_atlas_prompt_action) { startDownload(item) }
                .show()
        }
    }

    private fun startDownload(item: ExtensionItem.Module) {
        // Collecting drives the cold download Flow; progress itself is surfaced by the WorkManager tray
        // notification and the Extensions Manager row, so nothing extra is rendered here.
        scope.launch { inventory.download(item).collect { } }
    }

    private fun atlasItem(): ExtensionItem.Module? =
        inventory.getExtensions()
            .filterIsInstance<ExtensionItem.Module>()
            .firstOrNull { it.set == DeliverableSet.CHANNEL_PREVIEW_ATLAS }
}
