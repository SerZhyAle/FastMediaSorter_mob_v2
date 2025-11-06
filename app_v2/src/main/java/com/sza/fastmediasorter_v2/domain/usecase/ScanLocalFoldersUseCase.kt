package com.sza.fastmediasorter_v2.domain.usecase

import android.content.Context
import android.os.Environment
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.MediaType
import com.sza.fastmediasorter_v2.domain.model.ResourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.Date
import javax.inject.Inject

class ScanLocalFoldersUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaScanner: MediaScanner
) {
    suspend operator fun invoke(): Result<List<MediaResource>> = withContext(Dispatchers.IO) {
        try {
            val resources = mutableListOf<MediaResource>()
            val predefinedFolders = getPredefinedFolders()
            
            predefinedFolders.forEach { folder ->
                if (folder.exists()) {
                    val fileCount = try {
                        mediaScanner.getFileCount(
                            folder.absolutePath,
                            MediaType.entries.toSet()
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Error counting files in ${folder.absolutePath}")
                        0
                    }
                    
                    val isWritable = try {
                        mediaScanner.isWritable(folder.absolutePath)
                    } catch (e: Exception) {
                        Timber.e(e, "Error checking write access for ${folder.absolutePath}")
                        false
                    }
                    
                    resources.add(
                        MediaResource(
                            id = 0,
                            name = folder.name,
                            path = folder.absolutePath,
                            type = ResourceType.LOCAL,
                            supportedMediaTypes = MediaType.entries.toSet(),
                            createdDate = System.currentTimeMillis(),
                            fileCount = fileCount,
                            isDestination = false,
                            destinationOrder = null,
                            isWritable = isWritable,
                            slideshowInterval = 5
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
            folders.addAll(
                listOf(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    File(Environment.getExternalStorageDirectory(), "Camera")
                )
            )
        }
        
        return folders
    }
}
