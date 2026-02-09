package com.sza.fastmediasorter.utils

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import timber.log.Timber
import java.io.File

object MediaStoreNotifier {
    fun notifyFile(context: Context, path: String, reason: String? = null) {
        if (path.startsWith("content://")) return
        val file = File(path)
        if (!file.exists()) return
        if (!isSharedStoragePath(context, file.absolutePath)) return

        val tag = if (reason.isNullOrBlank()) "" else " ($reason)"
        try {
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null) { _, uri ->
                Timber.d("MediaStoreNotifier: scanned ${file.absolutePath} -> $uri$tag")
            }
        } catch (e: Exception) {
            Timber.e(e, "MediaStoreNotifier: scan failed for ${file.absolutePath}$tag")
        }
    }

    private fun isSharedStoragePath(context: Context, path: String): Boolean {
        val externalRoot = Environment.getExternalStorageDirectory().absolutePath
        if (!path.startsWith(externalRoot) && !path.startsWith("/storage/emulated/0/")) return false

        val appExternal = context.getExternalFilesDir(null)?.absolutePath
        if (appExternal != null && path.startsWith(appExternal)) return false

        val appAndroidData = "/Android/data/${context.packageName}/"
        if (path.contains(appAndroidData)) return false

        return true
    }
}
