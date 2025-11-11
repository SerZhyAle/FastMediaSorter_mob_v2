package com.sza.fastmediasorter_v2.domain.usecase

import com.sza.fastmediasorter_v2.data.network.SmbFileOperationHandler
import com.sza.fastmediasorter_v2.data.network.SftpFileOperationHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

sealed class FileOperation {
    data class Copy(val sources: List<File>, val destination: File, val overwrite: Boolean) : FileOperation()
    data class Move(val sources: List<File>, val destination: File, val overwrite: Boolean) : FileOperation()
    data class Rename(val file: File, val newName: String) : FileOperation()
    data class Delete(val files: List<File>) : FileOperation()
}

sealed class FileOperationResult {
    data class Success(
        val processedCount: Int, 
        val operation: FileOperation,
        val copiedFilePaths: List<String> = emptyList() // Paths of destination files for undo
    ) : FileOperationResult()
    data class PartialSuccess(val processedCount: Int, val failedCount: Int, val errors: List<String>) : FileOperationResult()
    data class Failure(val error: String) : FileOperationResult()
}

data class OperationHistory(
    val operation: FileOperation,
    val result: FileOperationResult,
    val timestamp: Long = System.currentTimeMillis()
)

class FileOperationUseCase @Inject constructor(
    private val smbFileOperationHandler: SmbFileOperationHandler,
    private val sftpFileOperationHandler: SftpFileOperationHandler
) {
    
    private var lastOperation: OperationHistory? = null
    
    suspend fun execute(operation: FileOperation): FileOperationResult = withContext(Dispatchers.IO) {
        Timber.d("FileOperation: Starting operation: ${operation.javaClass.simpleName}")
        
        try {
            // Helper to check if path is network resource (use path instead of absolutePath to avoid /prefix)
            fun File.isNetworkPath(protocol: String): Boolean {
                val pathStr = this.path
                val result = pathStr.startsWith("$protocol://") || pathStr.startsWith("/$protocol://") || pathStr.startsWith("/$protocol:/")
                Timber.d("FileOperation.isNetworkPath: path='$pathStr', protocol='$protocol', result=$result")
                return result
            }
            
            // Check if operation involves SMB or SFTP paths
            val hasSmbPath = when (operation) {
                is FileOperation.Copy -> {
                    val sourceSmbCount = operation.sources.count { it.isNetworkPath("smb") }
                    val destIsSmb = operation.destination.isNetworkPath("smb")
                    Timber.d("FileOperation.Copy: sources=$sourceSmbCount/${operation.sources.size} SMB, dest=${if (destIsSmb) "SMB" else "Local"}")
                    sourceSmbCount > 0 || destIsSmb
                }
                is FileOperation.Move -> {
                    val sourceSmbCount = operation.sources.count { it.isNetworkPath("smb") }
                    val destIsSmb = operation.destination.isNetworkPath("smb")
                    Timber.d("FileOperation.Move: sources=$sourceSmbCount/${operation.sources.size} SMB, dest=${if (destIsSmb) "SMB" else "Local"}")
                    sourceSmbCount > 0 || destIsSmb
                }
                is FileOperation.Delete -> {
                    val smbCount = operation.files.count { it.isNetworkPath("smb") }
                    Timber.d("FileOperation.Delete: $smbCount/${operation.files.size} SMB files")
                    smbCount > 0
                }
                is FileOperation.Rename -> {
                    val isSmb = operation.file.isNetworkPath("smb")
                    Timber.d("FileOperation.Rename: file=${if (isSmb) "SMB" else "Local"}")
                    isSmb
                }
            }

            val hasSftpPath = when (operation) {
                is FileOperation.Copy -> {
                    val sourceSftpCount = operation.sources.count { it.isNetworkPath("sftp") }
                    val destIsSftp = operation.destination.isNetworkPath("sftp")
                    Timber.d("FileOperation.Copy: sources=$sourceSftpCount/${operation.sources.size} SFTP, dest=${if (destIsSftp) "SFTP" else "Local"}")
                    sourceSftpCount > 0 || destIsSftp
                }
                is FileOperation.Move -> {
                    val sourceSftpCount = operation.sources.count { it.isNetworkPath("sftp") }
                    val destIsSftp = operation.destination.isNetworkPath("sftp")
                    Timber.d("FileOperation.Move: sources=$sourceSftpCount/${operation.sources.size} SFTP, dest=${if (destIsSftp) "SFTP" else "Local"}")
                    sourceSftpCount > 0 || destIsSftp
                }
                is FileOperation.Delete -> {
                    val sftpCount = operation.files.count { it.isNetworkPath("sftp") }
                    Timber.d("FileOperation.Delete: $sftpCount/${operation.files.size} SFTP files")
                    sftpCount > 0
                }
                is FileOperation.Rename -> {
                    val isSftp = operation.file.isNetworkPath("sftp")
                    Timber.d("FileOperation.Rename: file=${if (isSftp) "SFTP" else "Local"}")
                    isSftp
                }
            }

            val result = when {
                hasSmbPath -> {
                    Timber.d("FileOperation: Using SMB handler")
                    // Use SMB handler for operations involving SMB paths
                    when (operation) {
                        is FileOperation.Copy -> smbFileOperationHandler.executeCopy(operation)
                        is FileOperation.Move -> smbFileOperationHandler.executeMove(operation)
                        is FileOperation.Delete -> smbFileOperationHandler.executeDelete(operation)
                        is FileOperation.Rename -> smbFileOperationHandler.executeRename(operation)
                    }
                }
                hasSftpPath -> {
                    Timber.d("FileOperation: Using SFTP handler")
                    // Use SFTP handler for operations involving SFTP paths
                    when (operation) {
                        is FileOperation.Copy -> sftpFileOperationHandler.executeCopy(operation)
                        is FileOperation.Move -> sftpFileOperationHandler.executeMove(operation)
                        is FileOperation.Delete -> sftpFileOperationHandler.executeDelete(operation)
                        is FileOperation.Rename -> sftpFileOperationHandler.executeRename(operation)
                    }
                }
                else -> {
                    Timber.d("FileOperation: Using local file operations")
                    // Use local file operations
                    when (operation) {
                        is FileOperation.Copy -> executeCopy(operation)
                        is FileOperation.Move -> executeMove(operation)
                        is FileOperation.Rename -> executeRename(operation)
                        is FileOperation.Delete -> executeDelete(operation)
                    }
                }
            }
            
            when (result) {
                is FileOperationResult.Success -> Timber.i("FileOperation: SUCCESS - processed ${result.processedCount} files")
                is FileOperationResult.PartialSuccess -> Timber.w("FileOperation: PARTIAL SUCCESS - ${result.processedCount} ok, ${result.failedCount} failed. Errors: ${result.errors}")
                is FileOperationResult.Failure -> Timber.e("FileOperation: FAILURE - ${result.error}")
            }
            
            lastOperation = OperationHistory(operation, result)
            result
            
        } catch (e: Exception) {
            Timber.e(e, "FileOperation: EXCEPTION in execute()")
            FileOperationResult.Failure("${e.javaClass.simpleName}: ${e.message}")
        }
    }
    
    private fun executeCopy(operation: FileOperation.Copy): FileOperationResult {
        Timber.d("executeCopy: Starting local copy of ${operation.sources.size} files to ${operation.destination.absolutePath}")
        
        val errors = mutableListOf<String>()
        val copiedPaths = mutableListOf<String>()
        var successCount = 0
        
        operation.sources.forEachIndexed { index, source ->
            Timber.d("executeCopy: [${index + 1}/${operation.sources.size}] Processing ${source.name}")
            
            val destFile = File(operation.destination, source.name)
            
            try {
                if (!source.exists()) {
                    val error = buildString {
                        append("${source.name}")
                        append("\n  Source: ${source.absolutePath}")
                        append("\n  Error: File not found")
                    }
                    Timber.e("executeCopy: $error")
                    errors.add(error)
                    return@forEachIndexed
                }
                
                Timber.d("executeCopy: Target: ${destFile.absolutePath}, size=${source.length()} bytes")
                
                if (destFile.exists() && !operation.overwrite) {
                    val error = buildString {
                        append("${source.name}")
                        append("\n  From: ${source.absolutePath}")
                        append("\n  To: ${destFile.absolutePath}")
                        append("\n  Error: File already exists")
                    }
                    Timber.w("executeCopy: $error")
                    errors.add(error)
                    return@forEachIndexed
                }
                
                val startTime = System.currentTimeMillis()
                source.copyTo(destFile, operation.overwrite)
                val duration = System.currentTimeMillis() - startTime
                
                copiedPaths.add(destFile.absolutePath)
                successCount++
                Timber.i("executeCopy: SUCCESS - ${source.name} copied in ${duration}ms")
                
            } catch (e: Exception) {
                val error = buildString {
                    append("${source.name}")
                    append("\n  From: ${source.absolutePath}")
                    append("\n  To: ${destFile.absolutePath}")
                    append("\n  Error: ${e.javaClass.simpleName} - ${e.message}")
                }
                Timber.e(e, "executeCopy: ERROR - $error")
                errors.add(error)
            }
        }
        
        val result = when {
            successCount == operation.sources.size -> {
                Timber.i("executeCopy: All $successCount files copied successfully")
                FileOperationResult.Success(successCount, operation, copiedPaths)
            }
            successCount > 0 -> {
                Timber.w("executeCopy: Partial success - $successCount/${operation.sources.size} files copied. Errors: $errors")
                FileOperationResult.PartialSuccess(successCount, errors.size, errors)
            }
            else -> {
                Timber.e("executeCopy: All copy operations failed. Errors: $errors")
                FileOperationResult.Failure("All copy operations failed: ${errors.firstOrNull() ?: "Unknown error"}")
            }
        }
        
        return result
    }
    
    private fun executeMove(operation: FileOperation.Move): FileOperationResult {
        Timber.d("executeMove: Starting local move of ${operation.sources.size} files to ${operation.destination.absolutePath}")
        
        val errors = mutableListOf<String>()
        val movedPaths = mutableListOf<String>()
        var successCount = 0
        
        operation.sources.forEachIndexed { index, source ->
            Timber.d("executeMove: [${index + 1}/${operation.sources.size}] Processing ${source.name}")
            
            val destFile = File(operation.destination, source.name)
            
            try {
                if (!source.exists()) {
                    val error = buildString {
                        append("${source.name}")
                        append("\n  Source: ${source.absolutePath}")
                        append("\n  Error: File not found")
                    }
                    Timber.e("executeMove: $error")
                    errors.add(error)
                    return@forEachIndexed
                }
                
                Timber.d("executeMove: Target: ${destFile.absolutePath}, size=${source.length()} bytes")
                
                if (destFile.exists() && !operation.overwrite) {
                    val error = buildString {
                        append("${source.name}")
                        append("\n  From: ${source.absolutePath}")
                        append("\n  To: ${destFile.absolutePath}")
                        append("\n  Error: File already exists")
                    }
                    Timber.w("executeMove: $error")
                    errors.add(error)
                    return@forEachIndexed
                }
                
                val startTime = System.currentTimeMillis()
                
                // Try rename first (faster for same filesystem)
                if (source.renameTo(destFile)) {
                    val duration = System.currentTimeMillis() - startTime
                    movedPaths.add(destFile.absolutePath)
                    successCount++
                    Timber.i("executeMove: SUCCESS via rename - ${source.name} moved in ${duration}ms")
                } else {
                    Timber.d("executeMove: Rename failed, trying copy+delete for ${source.name}")
                    
                    source.copyTo(destFile, operation.overwrite)
                    val copyDuration = System.currentTimeMillis() - startTime
                    Timber.d("executeMove: Copy completed in ${copyDuration}ms, attempting delete")
                    
                    if (source.delete()) {
                        val totalDuration = System.currentTimeMillis() - startTime
                        movedPaths.add(destFile.absolutePath)
                        successCount++
                        Timber.i("executeMove: SUCCESS via copy+delete - ${source.name} moved in ${totalDuration}ms")
                    } else {
                        val error = buildString {
                            append("${source.name}")
                            append("\n  From: ${source.absolutePath}")
                            append("\n  To: ${destFile.absolutePath}")
                            append("\n  Error: Failed to delete source after copy")
                        }
                        Timber.e("executeMove: $error - copied file remains at ${destFile.absolutePath}")
                        errors.add(error)
                    }
                }
                
            } catch (e: Exception) {
                val error = buildString {
                    append("${source.name}")
                    append("\n  From: ${source.absolutePath}")
                    append("\n  To: ${File(operation.destination, source.name).absolutePath}")
                    append("\n  Error: ${e.javaClass.simpleName} - ${e.message}")
                }
                Timber.e(e, "executeMove: ERROR - $error")
                errors.add(error)
            }
        }
        
        val result = when {
            successCount == operation.sources.size -> {
                Timber.i("executeMove: All $successCount files moved successfully")
                FileOperationResult.Success(successCount, operation, movedPaths)
            }
            successCount > 0 -> {
                Timber.w("executeMove: Partial success - $successCount/${operation.sources.size} files moved. Errors: $errors")
                FileOperationResult.PartialSuccess(successCount, errors.size, errors)
            }
            else -> {
                Timber.e("executeMove: All move operations failed. Errors: $errors")
                FileOperationResult.Failure("All move operations failed: ${errors.firstOrNull() ?: "Unknown error"}")
            }
        }
        
        return result
    }
    
    private fun executeRename(operation: FileOperation.Rename): FileOperationResult {
        try {
            if (!operation.file.exists()) {
                return FileOperationResult.Failure("File not found: ${operation.file.name}")
            }
            
            val newFile = File(operation.file.parent, operation.newName)
            
            if (newFile.exists()) {
                return FileOperationResult.Failure("File with name '${operation.newName}' already exists")
            }
            
            if (operation.file.renameTo(newFile)) {
                return FileOperationResult.Success(1, operation, listOf(newFile.absolutePath))
            } else {
                return FileOperationResult.Failure("Failed to rename ${operation.file.name}")
            }
            
        } catch (e: Exception) {
            return FileOperationResult.Failure("Rename error: ${e.message}")
        }
    }
    
    private fun executeDelete(operation: FileOperation.Delete): FileOperationResult {
        val errors = mutableListOf<String>()
        val deletedPaths = mutableListOf<String>()
        var successCount = 0
        
        operation.files.forEach { file ->
            try {
                if (!file.exists()) {
                    errors.add("File not found: ${file.name}")
                    return@forEach
                }
                
                val filePath = file.absolutePath
                if (file.delete()) {
                    deletedPaths.add(filePath)
                    successCount++
                } else {
                    errors.add("Failed to delete: ${file.name}")
                }
                
            } catch (e: Exception) {
                errors.add("Delete error for ${file.name}: ${e.message}")
            }
        }
        
        return when {
            successCount == operation.files.size -> FileOperationResult.Success(successCount, operation, deletedPaths)
            successCount > 0 -> FileOperationResult.PartialSuccess(successCount, errors.size, errors)
            else -> FileOperationResult.Failure("All delete operations failed")
        }
    }
    
    fun getLastOperation(): OperationHistory? = lastOperation
    
    fun clearHistory() {
        lastOperation = null
    }
    
    suspend fun canUndo(): Boolean = withContext(Dispatchers.IO) {
        lastOperation != null
    }
    
    suspend fun undo(): FileOperationResult? = withContext(Dispatchers.IO) {
        val history = lastOperation ?: return@withContext null
        
        when (val op = history.operation) {
            is FileOperation.Copy -> {
                val filesToDelete = op.sources.map { File(op.destination, it.name) }
                execute(FileOperation.Delete(filesToDelete))
            }
            is FileOperation.Move -> {
                val filesToMoveBack = op.sources.mapNotNull { source ->
                    val parent = source.parentFile
                    if (parent != null) {
                        File(op.destination, source.name) to parent
                    } else {
                        null
                    }
                }.filter { it.first.exists() }
                
                if (filesToMoveBack.isEmpty()) return@withContext null
                
                execute(FileOperation.Move(
                    sources = filesToMoveBack.map { it.first },
                    destination = filesToMoveBack.first().second,
                    overwrite = true
                ))
            }
            is FileOperation.Delete -> null
            is FileOperation.Rename -> {
                val newFile = File(op.file.parent, op.newName)
                if (newFile.exists()) {
                    execute(FileOperation.Rename(newFile, op.file.name))
                } else {
                    null
                }
            }
        }
    }
}
