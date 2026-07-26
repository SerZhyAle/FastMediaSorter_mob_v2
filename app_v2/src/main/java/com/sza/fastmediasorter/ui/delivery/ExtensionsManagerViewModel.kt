package com.sza.fastmediasorter.ui.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.delivery.DeliverableInventory
import com.sza.fastmediasorter.domain.delivery.ExtensionItem
import com.sza.fastmediasorter.domain.delivery.ExtensionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel driving the Extensions Manager screen (S0386 Phase 08).
 */
@HiltViewModel
class ExtensionsManagerViewModel @Inject constructor(
    private val inventory: DeliverableInventory
) : ViewModel() {

    val extensions: List<ExtensionItem> = inventory.getExtensions()

    fun download(item: ExtensionItem) {
        viewModelScope.launch {
            inventory.download(item).collect {
                // Progress is reactively piped via statusFlow of each item
            }
        }
    }

    fun uninstall(item: ExtensionItem) {
        viewModelScope.launch {
            inventory.uninstall(item)
        }
    }

    // Bulk download every extension that is not already installed or in flight; each download runs in
    // its own child coroutine so the rows progress concurrently, mirroring per-row download().
    fun installAll() {
        viewModelScope.launch {
            extensions.forEach { item ->
                val status = item.statusFlow.first()
                // S1200: a stale row is worth (re)downloading in a bulk install - it is the case where
                // the user has an old copy and asked for everything to be brought up to date.
                if (status is ExtensionStatus.NotInstalled ||
                    status is ExtensionStatus.Failed ||
                    status is ExtensionStatus.UpdateAvailable
                ) {
                    launch { inventory.download(item).collect { } }
                }
            }
        }
    }

    // Bulk uninstall every currently installed extension; sequential since uninstall is cheap and
    // avoids racing the shared capability repository.
    fun uninstallAll() {
        viewModelScope.launch {
            extensions.forEach { item ->
                // S1200: a stale payload is still on disk, so "uninstall everything" must free it too.
                val status = item.statusFlow.first()
                if (status is ExtensionStatus.Installed || status is ExtensionStatus.UpdateAvailable) {
                    inventory.uninstall(item)
                }
            }
        }
    }
}
