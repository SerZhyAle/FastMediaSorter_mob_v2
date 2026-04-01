package com.sza.fastmediasorter.data.transfer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.sza.fastmediasorter.domain.transfer.FileInfo
import com.sza.fastmediasorter.domain.transfer.FileTransferProvider
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import com.sza.fastmediasorter.utils.SafHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local file system implementation of FileTransferProvider.
 * Handles operations on device storage using File API (legacy) and SAF (Android 11+).
 */
@Singleton
class LocalTransferProvider @Inject constructor(
    @param:ApplicationContext private val context: Context
) : FileTransferProvider {
    
    override val protocolName = "Local"
    
    override suspend fun downloadFile(
        sourcePath: String,
        destinationFile: File,
        onProgress: ((Long, Long) -> Unit)?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Check if source is SAF
            val sourceInputStream = if (sourcePath.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(sourcePath))
                    ?: return@withContext Result.failure(Exception("Cannot open stream for SAF URI: $sourcePath"))
            } else {
                val sourceFile = File(sourcePath)
                if (!sourceFile.exists()) {
                    return@withContext Result.failure(Exception("Source file does not exist: $sourcePath"))
                }
                FileInputStream(sourceFile)
            }
            
            // Check if destination is SAF (wrapped in File object via absolutePath)
            val destPath = destinationFile.absolutePath
            val destOutputStream = if (destPath.startsWith("content://")) {
                 context.contentResolver.openOutputStream(Uri.parse(destPath))
                    ?: return@withContext Result.failure(Exception("Cannot open stream for destination SAF URI: $destPath"))
            } else {
                // Legacy File API
                destinationFile.parentFile?.mkdirs()
                FileOutputStream(destinationFile)
            }
            
            sourceInputStream.use { input ->
                destOutputStream.use { output ->
                    val buffer = ByteArray(65536)
                    var read: Int
                    var transferred = 0L
                    // Estimation of total size if possible
                    val totalSize = if (sourcePath.startsWith("content://")) {
                         // Try to get size
                         getFileInfo(sourcePath).getOrNull()?.size ?: 0L
                    } else {
                        File(sourcePath).length()
                    }
                    
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        transferred += read
                        if (totalSize > 0) onProgress?.invoke(transferred, totalSize)
                    }
                }
            }
            
            Timber.d("Downloaded local file: $sourcePath -> ${destinationFile.absolutePath}")
            MediaStoreNotifier.notifyFile(context, destinationFile.absolutePath, "local-transfer-download")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to download local file: $sourcePath")
            Result.failure(e)
        }
    }
    
    override suspend fun uploadFile(
        sourceFile: File,
        destinationPath: String,
        onProgress: ((Long, Long) -> Unit)?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Check source
            val sourcePath = sourceFile.absolutePath
            val sourceInputStream = if (sourcePath.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(sourcePath))
                    ?: return@withContext Result.failure(Exception("Cannot open stream for SAF URI: $sourcePath"))
            } else {
                if (!sourceFile.exists()) {
                    return@withContext Result.failure(Exception("Source file does not exist: ${sourceFile.absolutePath}"))
                }
                FileInputStream(sourceFile)
            }
            
            // Check destination
            val destOutputStream = if (destinationPath.startsWith("content://")) {
                context.contentResolver.openOutputStream(Uri.parse(destinationPath))
                    ?: return@withContext Result.failure(Exception("Cannot open stream for destination SAF URI: $destinationPath"))
            } else {
                val destFile = File(destinationPath)
                destFile.parentFile?.mkdirs()
                FileOutputStream(destFile)
            }
             
            sourceInputStream.use { input ->
                destOutputStream.use { output ->
                     val buffer = ByteArray(65536)
                    var read: Int
                    var transferred = 0L
                    val totalSize = sourceFile.length() // File.length() returns 0 for SAF paths usually, handled by getFileInfo if needed but sourceFile is File object
                    
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        transferred += read
                        onProgress?.invoke(transferred, totalSize)
                    }
                }
            }
            
            Timber.d("Uploaded to local path: ${sourceFile.name} -> $destinationPath")
            MediaStoreNotifier.notifyFile(context, destinationPath, "local-transfer-upload")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload to local path: $destinationPath")
            Result.failure(e)
        }
    }
    
    override suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (path.startsWith("content://")) {
                 // Use SafHelper which handles tree document URIs
                 val deleted = com.sza.fastmediasorter.utils.SafHelper.deleteContentUri(
                     context, path, "LocalTransferProvider.deleteFile"
                 )
                 
                 if (deleted) {
                     Timber.d("Deleted SAF file: $path")
                     Result.success(Unit)
                 } else {
                     Result.failure(Exception("Failed to delete SAF file: $path"))
                 }
            } else {
                val file = File(path)
                if (!file.exists()) return@withContext Result.failure(Exception("File does not exist: $path"))
                
                val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
                
                if (deleted) {
                    Timber.d("Deleted local file: $path")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to delete file: $path"))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error deleting local file: $path")
            Result.failure(e)
        }
    }
    
    override suspend fun renameFile(
        oldPath: String,
        newPath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (oldPath.startsWith("content://")) {
                 // Rename for SAF is only supported via DocumentFile.renameTo(displayName)
                 // We can't move it to a different folder via renameTo usually.
                 val uri = Uri.parse(oldPath)
                 val docFile = SafHelper.getDocumentFileFromUri(context, uri)
                 if (docFile == null || !docFile.exists()) {
                     return@withContext Result.failure(Exception("Source SAF file not found: $oldPath"))
                 }
                 
                 // Extract new name from newPath
                 // If newPath is full URI, we assume we just want to rename the file at oldPath to the name in newPath?
                 // Usually renameFile(old, new) implies moving if new is different path.
                 // SAF renameTo only changes display name.
                 // If newPath is just a name or different path?
                 
                 // Assumption: newPath might be "content://.../NewName.ext" or just "/storage/.../NewName.ext"
                 // If specific logic is needed, we assume we extract the filename.
                 val newName = if (newPath.contains("/")) newPath.substringAfterLast('/') else newPath
                 
                 if (docFile.renameTo(newName)) {
                     // The URI usually remains valid even if name changes, or it might change.
                     // DocumentFile doesn't automatically update URI if it relies on ID, which is good.
                     Timber.d("Renamed SAF file: $oldPath -> $newName")
                     Result.success(oldPath) // Return old URI as it's likely still valid identifier
                 } else {
                     Result.failure(Exception("Failed to rename SAF file"))
                 }
            } else {
                val oldFile = File(oldPath)
                val newFile = File(newPath)
                
                if (!oldFile.exists()) return@withContext Result.failure(Exception("Source file does not exist: $oldPath"))
                if (newFile.exists()) return@withContext Result.failure(Exception("Destination already exists: $newPath"))
                
                newFile.parentFile?.mkdirs()
                
                if (oldFile.renameTo(newFile)) {
                    Timber.d("Renamed local file: $oldPath -> $newPath")
                    MediaStoreNotifier.notifyFile(context, newFile.absolutePath, "local-transfer-rename")
                    Result.success(newFile.absolutePath)
                } else {
                    Result.failure(Exception("Failed to rename file"))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error renaming local file: $oldPath -> $newPath")
            Result.failure(e)
        }
    }
    
    override suspend fun moveFile(
        sourcePath: String,
        destinationPath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // If both are SAF and in same tree, we might be able to move?
            // Usually move = copy + delete for reliability across providers
            
            // Try native rename if both are File
            if (!sourcePath.startsWith("content://") && !destinationPath.startsWith("content://")) {
                 val sourceFile = File(sourcePath)
                 val destFile = File(destinationPath)
                 destFile.parentFile?.mkdirs()
                 if (sourceFile.renameTo(destFile)) {
                     MediaStoreNotifier.notifyFile(context, destFile.absolutePath, "local-transfer-move")
                     return@withContext Result.success(destFile.absolutePath)
                 }
            }
            
            // Fallback: Copy + Delete
            // We reuse downloadFile or uploadFile logic?
            // We need manual copy stream to stream logic here to handle all permutations (SAF->SAF, SAF->File, File->SAF)
            
            val sourceInputStream = if (sourcePath.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(sourcePath))
            } else {
                FileInputStream(File(sourcePath))
            } ?: return@withContext Result.failure(Exception("Cannot open source stream: $sourcePath"))
            
            val destOutputStream = if (destinationPath.startsWith("content://")) {
                context.contentResolver.openOutputStream(Uri.parse(destinationPath))
            } else {
                val f = File(destinationPath)
                f.parentFile?.mkdirs()
                FileOutputStream(f)
            } ?: return@withContext Result.failure(Exception("Cannot open destination stream: $destinationPath"))
            
            sourceInputStream.use { input ->
                destOutputStream.use { output ->
                    input.copyTo(output)
                }
            }
            
            // Delete source
            deleteFile(sourcePath)
            
            Timber.d("Moved file: $sourcePath -> $destinationPath")
            MediaStoreNotifier.notifyFile(context, destinationPath, "local-transfer-move")
            Result.success(destinationPath)
            
        } catch (e: Exception) {
            Timber.e(e, "Error moving local file: $sourcePath -> $destinationPath")
            Result.failure(e)
        }
    }
    
    override suspend fun exists(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (path.startsWith("content://")) {
                val doc = DocumentFile.fromSingleUri(context, Uri.parse(path))
                Result.success(doc?.exists() == true)
            } else {
                Result.success(File(path).exists())
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking existence: $path")
            Result.failure(e)
        }
    }
    
    override suspend fun getFileInfo(path: String): Result<FileInfo> = withContext(Dispatchers.IO) {
        try {
            if (path.startsWith("content://")) {
                 val uri = Uri.parse(path)
                 val doc = SafHelper.getDocumentFileFromUri(context, uri) 
                     ?: DocumentFile.fromSingleUri(context, uri) // Fallback
                 
                 if (doc != null && doc.exists()) {
                     Result.success(FileInfo(
                         path = path,
                         name = doc.name ?: "unknown",
                         size = doc.length(),
                         lastModified = doc.lastModified(),
                         isDirectory = doc.isDirectory
                     ))
                 } else {
                     Result.failure(Exception("SAF file not found: $path"))
                 }
            } else {
                val file = File(path)
                if (!file.exists()) return@withContext Result.failure(Exception("File does not exist: $path"))
                Result.success(FileInfo(
                    path = file.absolutePath,
                    name = file.name,
                    size = file.length(),
                    lastModified = file.lastModified(),
                    isDirectory = file.isDirectory
                ))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting file info: $path")
            Result.failure(e)
        }
    }
    
    override suspend fun createDirectory(path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
             if (path.startsWith("content://")) {
                 val uri = Uri.parse(path)
                 val parentDoc = SafHelper.getDocumentFileFromUri(context, uri)
                     ?: return@withContext Result.failure(Exception("Parent SAF directory not found: $path"))
                 
                 // If the folder name is already in the path, extract name and use parent.
                 // Assuming path is where we want the directory.
                 // This method usually expects the path TO BE CREATED.
                 
                 // If createDirectory(path) means "ensure path exists", we find parent and create child.
                 val parentUri = uri.toString().substringBeforeLast('/')
                 val folderName = uri.toString().substringAfterLast('/')
                 
                 val parentFile = DocumentFile.fromTreeUri(context, Uri.parse(parentUri))
                 val newDir = parentFile?.createDirectory(folderName)
                 
                 if (newDir != null) {
                     Timber.d("Created SAF directory: ${newDir.uri}")
                     Result.success(newDir.uri.toString())
                 } else {
                     Result.failure(Exception("Failed to create SAF directory: $folderName in $parentUri"))
                 }
            } else {
                val dir = File(path)
                if (dir.exists()) {
                    Result.success(dir.absolutePath)
                } else if (dir.mkdirs()) {
                    Timber.d("Created local directory: $path")
                    MediaStoreNotifier.notifyFile(context, path, "createDirectory")
                    Result.success(dir.absolutePath)
                } else {
                    Result.failure(Exception("Failed to create directory: $path"))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error creating directory: $path")
            Result.failure(e)
        }
    }
    
    override suspend fun isFile(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (path.startsWith("content://")) {
                val doc = DocumentFile.fromSingleUri(context, Uri.parse(path))
                Result.success(doc?.exists() == true && doc.isFile)
            } else {
                val file = File(path)
                Result.success(file.exists() && file.isFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
