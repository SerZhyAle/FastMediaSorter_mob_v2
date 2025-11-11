package com.sza.fastmediasorter_v2.domain.usecase

import android.content.Context
import android.os.Environment
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.MediaType
import com.sza.fastmediasorter_v2.domain.model.ResourceType
import com.sza.fastmediasorter_v2.domain.repository.ResourceRepository
import com.sza.fastmediasorter_v2.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class ScanLocalFoldersUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaScannerFactory: MediaScannerFactory,
    private val repository: ResourceRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(): Result<List<MediaResource>> = withContext(Dispatchers.IO) {
        try {
            val existingResources = repository.getAllResources().first()
            val existingPaths = existingResources.map { it.path }.toSet()
            
            // Get current settings for default values
            val settings = settingsRepository.getSettings().first()
            
            // Determine supported media types from settings
            val supportedMediaTypes = mutableSetOf<MediaType>()
            if (settings.supportImages) supportedMediaTypes.add(MediaType.IMAGE)
            if (settings.supportVideos) supportedMediaTypes.add(MediaType.VIDEO)
            if (settings.supportAudio) supportedMediaTypes.add(MediaType.AUDIO)
            if (settings.supportGifs) supportedMediaTypes.add(MediaType.GIF)
            
            val resources = mutableListOf<MediaResource>()
            val predefinedFolders = getPredefinedFolders()
            
            val mediaScanner = mediaScannerFactory.getScanner(ResourceType.LOCAL)
            
            predefinedFolders.forEach { folder ->
                if (folder.exists() && folder.absolutePath !in existingPaths) {
                    val fileCount = try {
                        mediaScanner.getFileCount(
                            folder.absolutePath,
                            supportedMediaTypes
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Error counting files in ${folder.absolutePath}")
                        0
                    }
                    
                    val isWritable = try {
                        mediaScanner.isWritable(folder.absolutePath, credentialsId = null)
                    } catch (e: Exception) {
                        Timber.e(e, "Error checking write access for ${folder.absolutePath}")
                        false
                    }
                    
                    resources.add(
                        MediaResource(
                            id = 1,
                            name = folder.name,
                            path = folder.absolutePath,
                            type = ResourceType.LOCAL,
                            supportedMediaTypes = supportedMediaTypes,
                            createdDate = System.currentTimeMillis(),
                            fileCount = fileCount,
                            isDestination = false,
                            destinationOrder = null,
                            isWritable = isWritable,
                            slideshowInterval = settings.slideshowInterval
                        )
                    )
                }
            }
            
            Result.success(resources)
        } catch (e: Exception) {
            Timber.e(e, "Error scanning local folders")
            Result.failure(e)
        }
    }
    
    private fun getPredefinedFolders(): List<File> {
        val folders = mutableListOf<File>()
        
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            // Add standard folders
            folders.addAll(
                listOf(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    File(Environment.getExternalStorageDirectory(), "Camera")
                )
            )
            
            // Recursively scan external storage for folders with media files
            // This will find Telegram, WhatsApp, and other app folders
            val externalStorage = Environment.getExternalStorageDirectory()
            Timber.d("Scanning external storage: ${externalStorage.absolutePath}")
            folders.addAll(scanForMediaFolders(externalStorage, maxDepth = 3))
            
            // Specifically scan Android/Media folder for app media (WhatsApp, Telegram, etc.)
            val androidMediaFolder = File(externalStorage, "Android/Media")
            if (androidMediaFolder.exists() && androidMediaFolder.canRead()) {
                Timber.d("Scanning Android/Media folder: ${androidMediaFolder.absolutePath}")
                folders.addAll(scanAndroidMediaFolder(androidMediaFolder))
            }
        }
        
        return folders.distinct()
    }
    
    /**
     * Scan Android/Media folder for app-specific media folders
     * Example: Android/Media/com.WhatsApp/WhatsApp/Media/WhatsApp Images
     */
    private fun scanAndroidMediaFolder(androidMediaDir: File): List<File> {
        val mediaFolders = mutableListOf<File>()
        
        try {
            val appPackages = androidMediaDir.listFiles() ?: return emptyList()
            
            for (appPackage in appPackages) {
                if (!appPackage.isDirectory || !appPackage.canRead()) continue
                
                // Recursively scan app package folder with deeper depth (up to 5 levels)
                // to find folders like com.WhatsApp/WhatsApp/Media/WhatsApp Images
                mediaFolders.addAll(scanForMediaFolders(appPackage, maxDepth = 5))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error scanning Android/Media folder")
        }
        
        return mediaFolders
    }
    
    /**
     * Recursively scan directory for folders containing media files
     * @param directory Directory to scan
     * @param maxDepth Maximum recursion depth (to avoid deep scanning)
     * @param currentDepth Current recursion depth
     * @return List of folders containing media files
     */
    private fun scanForMediaFolders(
        directory: File,
        maxDepth: Int,
        currentDepth: Int = 0
    ): List<File> {
        if (currentDepth >= maxDepth) return emptyList()
        
        val mediaFolders = mutableListOf<File>()
        
        try {
            val children = directory.listFiles() ?: return emptyList()
            
            for (child in children) {
                // Skip hidden folders and system folders
                if (child.name.startsWith(".") || 
                    child.name == "Android" ||
                    !child.isDirectory ||
                    !child.canRead()) {
                    continue
                }
                
                // Check if this folder has media files
                val hasMediaFiles = hasMediaFilesInFolder(child)
                if (hasMediaFiles) {
                    mediaFolders.add(child)
                    Timber.d("Found media folder: ${child.absolutePath}")
                }
                
                // Recursively scan subdirectories
                if (currentDepth < maxDepth - 1) {
                    mediaFolders.addAll(scanForMediaFolders(child, maxDepth, currentDepth + 1))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error scanning directory: ${directory.absolutePath}")
        }
        
        return mediaFolders
    }
    
    /**
     * Check if folder contains media files (images, videos, audio, GIF)
     * Quick check - only looks at first level files
     */
    private fun hasMediaFilesInFolder(folder: File): Boolean {
        try {
            val files = folder.listFiles() ?: return false
            
            // Check first 50 files for performance
            return files.take(50).any { file ->
                if (file.isFile) {
                    val extension = file.extension.lowercase()
                    extension in IMAGE_EXTENSIONS ||
                    extension in VIDEO_EXTENSIONS ||
                    extension in AUDIO_EXTENSIONS ||
                    extension in GIF_EXTENSIONS
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            return false
        }
    }
    
    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp")
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp")
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "wav", "flac", "ogg", "aac", "wma")
        private val GIF_EXTENSIONS = setOf("gif")
    }
}
