package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import com.sza.fastmediasorter.data.observer.MediaStoreObserver
import com.sza.fastmediasorter.domain.model.ResourceType
import timber.log.Timber

/**
 * Manages MediaStore observation for automatic file list updates.
 * Registers/unregisters ContentObserver and triggers reload on external changes.
 * Only monitors LOCAL resources (not network or cloud).
 */
class BrowseMediaStoreObserver(
    private val context: Context,
    private val callbacks: MediaStoreCallbacks
) {
    
    interface MediaStoreCallbacks {
        fun onMediaStoreChanged()
    }
    
    private var observer: MediaStoreObserver? = null
    
    fun start(resourceType: ResourceType?) {
        // Only observe local resources
        if (resourceType != ResourceType.LOCAL) {
            Timber.d("Skipping MediaStore observer for non-local resource: $resourceType")
            return
        }
        
        try {
            observer = MediaStoreObserver(
                context = context,
                onMediaStoreChanged = {
                    Timber.d("MediaStore changed, notifying callback")
                    callbacks.onMediaStoreChanged()
                }
            )
            observer?.startWatching()
            Timber.d("Started MediaStore observer")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start MediaStore observer")
        }
    }
    
    fun stop() {
        observer?.stopWatching()
        observer = null
        Timber.d("Stopped MediaStore observer")
    }
    
    fun cleanup() {
        stop()
    }
}
