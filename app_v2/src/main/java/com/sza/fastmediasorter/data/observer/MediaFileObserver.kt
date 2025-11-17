package com.sza.fastmediasorter.data.observer

import android.os.FileObserver
import timber.log.Timber
import java.io.File

/**
 * FileObserver wrapper to detect external file changes (deletions, moves, creations)
 * in a specific directory.
 *
 * Notifies listener when files are added, deleted, or moved externally.
 */
class MediaFileObserver(
    private val path: String,
    private val listener: FileChangeListener
) : FileObserver(File(path), EVENTS) {

    companion object {
        // Events to monitor: delete, move, create, close_write (file modified)
        private const val EVENTS = DELETE or MOVED_FROM or MOVED_TO or CREATE or CLOSE_WRITE
    }

    interface FileChangeListener {
        fun onFileDeleted(fileName: String)
        fun onFileCreated(fileName: String)
        fun onFileMoved(fromName: String?, toName: String?)
        fun onFileModified(fileName: String)
    }

    override fun onEvent(event: Int, path: String?) {
        if (path == null) return

        when (event and ALL_EVENTS) {
            DELETE -> {
                Timber.d("FileObserver: File deleted: $path")
                listener.onFileDeleted(path)
            }
            MOVED_FROM -> {
                Timber.d("FileObserver: File moved from: $path")
                listener.onFileMoved(path, null)
            }
            MOVED_TO -> {
                Timber.d("FileObserver: File moved to: $path")
                listener.onFileMoved(null, path)
            }
            CREATE -> {
                Timber.d("FileObserver: File created: $path")
                listener.onFileCreated(path)
            }
            CLOSE_WRITE -> {
                Timber.d("FileObserver: File modified: $path")
                listener.onFileModified(path)
            }
        }
    }
}
