package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import timber.log.Timber

class MediaDisplayCoordinator(
    private val callback: Callback
) {
    interface Callback {
        fun displayImage(path: String)
        fun playVideo(path: String)
        fun displayText(file: MediaFile)
        fun displayPdf(file: MediaFile)
        fun displayEpub(file: MediaFile)
    }

    fun display(file: MediaFile) {
        val isGifByExtension = file.name.lowercase().endsWith(".gif")

        when {
            isGifByExtension || file.type == MediaType.IMAGE || file.type == MediaType.GIF -> {
                callback.displayImage(file.path)
            }
            file.type == MediaType.VIDEO || file.type == MediaType.AUDIO -> {
                callback.playVideo(file.path)
            }
            file.type == MediaType.TEXT -> {
                callback.displayText(file)
            }
            file.type == MediaType.PDF -> {
                Timber.d("PDF PROGRESS: MediaDisplayCoordinator.display() calling displayPdf for ${file.name}")
                callback.displayPdf(file)
            }
            file.type == MediaType.EPUB -> {
                callback.displayEpub(file)
            }
            else -> {
                val ext = file.name.lowercase().substringAfterLast('.', "")
                val imageExts = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heif", "heic", "avif")
                if (ext in imageExts) {
                    Timber.w(
                        "MediaDisplayCoordinator: Unknown type ${file.type}, but extension suggests image - using displayImage()"
                    )
                    callback.displayImage(file.path)
                } else {
                    Timber.w(
                        "MediaDisplayCoordinator: Unknown type ${file.type} for ${file.name} - attempting playVideo()"
                    )
                    callback.playVideo(file.path)
                }
            }
        }
    }
}
