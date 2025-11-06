package com.sza.fastmediasorter_v2.data.local

import android.content.Context
import android.webkit.MimeTypeMap
import com.sza.fastmediasorter_v2.domain.model.MediaFile
import com.sza.fastmediasorter_v2.domain.model.MediaType
import com.sza.fastmediasorter_v2.domain.usecase.MediaScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMediaScanner @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaScanner {

    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp")
        private val GIF_EXTENSIONS = setOf("gif")
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "wmv", "m4v")
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "wav", "flac", "aac", "ogg", "wma", "opus")
    }

    override suspend fun scanFolder(path: String, supportedTypes: Set<MediaType>): List<MediaFile> = 
        withContext(Dispatchers.IO) {
            try {
                val folder = File(path)
                if (!folder.exists() || !folder.isDirectory) {
                    Timber.w("Folder does not exist or is not a directory: $path")
                    return@withContext emptyList()
                }

                val files = folder.listFiles() ?: return@withContext emptyList()
                
                files.mapNotNull { file ->
                    if (file.isFile) {
                        val mediaType = getMediaType(file)
                        if (mediaType != null && supportedTypes.contains(mediaType)) {
                            MediaFile(
                                name = file.name,
                                path = file.absolutePath,
                                size = file.length(),
                                createdDate = file.lastModified(),
                                type = mediaType
                            )
                        } else null
                    } else null
                }
            } catch (e: Exception) {
                Timber.e(e, "Error scanning folder: $path")
                emptyList()
            }
        }

    override suspend fun getFileCount(path: String, supportedTypes: Set<MediaType>): Int = 
        withContext(Dispatchers.IO) {
            try {
                val folder = File(path)
                if (!folder.exists() || !folder.isDirectory) {
                    return@withContext 0
                }

                val files = folder.listFiles() ?: return@withContext 0
                
                files.count { file ->
                    if (file.isFile) {
                        val mediaType = getMediaType(file)
                        mediaType != null && supportedTypes.contains(mediaType)
                    } else false
                }
            } catch (e: Exception) {
                Timber.e(e, "Error counting files in: $path")
                0
            }
        }

    override suspend fun isWritable(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val folder = File(path)
            folder.exists() && folder.canWrite()
        } catch (e: Exception) {
            Timber.e(e, "Error checking write access for: $path")
            false
        }
    }

    private fun getMediaType(file: File): MediaType? {
        val extension = file.extension.lowercase()
        return when {
            IMAGE_EXTENSIONS.contains(extension) -> MediaType.IMAGE
            GIF_EXTENSIONS.contains(extension) -> MediaType.GIF
            VIDEO_EXTENSIONS.contains(extension) -> MediaType.VIDEO
            AUDIO_EXTENSIONS.contains(extension) -> MediaType.AUDIO
            else -> null
        }
    }
}
