package com.sza.fastmediasorter.ui.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.delivery.DeliverableInventory
import com.sza.fastmediasorter.domain.delivery.ExtensionItem
import dagger.hilt.android.lifecycle.HiltViewModel
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
}
