package com.sza.fastmediasorter.data.observer

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import timber.log.Timber

/**
 * ContentObserver to monitor MediaStore changes (local media files).
 * Notifies when images, videos, or audio files are added/deleted/modified in MediaStore.
 */
class MediaStoreObserver(
    context: Context,
    private val onMediaStoreChanged: () -> Unit
) {
    private val contentResolver = context.contentResolver
    private val handler = Handler(Looper.getMainLooper())
    
    private val imageObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            Timber.d("MediaStore images changed: $uri")
            onMediaStoreChanged()
        }
    }
    
    private val videoObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            Timber.d("MediaStore videos changed: $uri")
            onMediaStoreChanged()
        }
    }
    
    private val audioObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            Timber.d("MediaStore audio changed: $uri")
            onMediaStoreChanged()
        }
    }
    
    fun startWatching() {
        // Register observers for images, videos, and audio
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            imageObserver
        )
        
        contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            videoObserver
        )
        
        contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            audioObserver
        )
        
        Timber.d("Started MediaStore observers")
    }
    
    fun stopWatching() {
        contentResolver.unregisterContentObserver(imageObserver)
        contentResolver.unregisterContentObserver(videoObserver)
        contentResolver.unregisterContentObserver(audioObserver)
        Timber.d("Stopped MediaStore observers")
    }
}
