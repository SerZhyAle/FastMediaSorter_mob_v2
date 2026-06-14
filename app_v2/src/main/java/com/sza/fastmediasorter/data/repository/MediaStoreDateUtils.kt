package com.sza.fastmediasorter.data.repository

import java.io.File

object MediaStoreDateUtils {

    fun resolveCreatedDate(path: String, mediaStoreSeconds: Long): Long {
        val mediaStoreMs = mediaStoreSeconds * 1000L
        if (mediaStoreMs > 0L) {
            return mediaStoreMs
        }

        val fallbackMs = File(path).takeIf { it.exists() && it.isFile }?.lastModified() ?: 0L
        return if (fallbackMs > 0L) fallbackMs else 0L
    }
}
