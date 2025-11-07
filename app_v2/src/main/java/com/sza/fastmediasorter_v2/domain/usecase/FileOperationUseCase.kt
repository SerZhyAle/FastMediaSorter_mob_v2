package com.sza.fastmediasorter_v2.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

class FileOperationUseCase @Inject constructor() {
    
    private var lastOperation: OperationHistory? = null
    
    suspend fun execute(operation: FileOperation): FileOperationResult = withContext(Dispatchers.IO) {
        try {
            val result = when (operation) {
                is FileOperation.Copy -> executeCopy(operation)
                is FileOperation.Move -> executeMove(operation)
                is FileOperation.Rename -> executeRename(operation)
                is FileOperation.Delete -> executeDelete(operation)
            }
            
            lastOperation = OperationHistory(operation, result)
            result
            
        } catch (e: Exception) {
            FileOperationResult.Failure(e.message ?: "Unknown error")
        }
    }
    
    private fun executeCopy(operation: FileOperation.Copy): FileOperationResult {
        val errors = mutableListOf<String>()
        val copiedPaths = mutableListOf<String>()
        var successCount = 0
        
        operation.sources.forEach { source ->
            try {
                if (!source.exists()) {
                    errors.add("File not found: ${source.name}")
                    return@forEach
                }
                
                val destFile = File(operation.destination, source.name)
                
                if (destFile.exists() && !operation.overwrite) {
                    errors.add("File already exists: ${source.name}")
                    return@forEach
                }
                
                source.copyTo(destFile, operation.overwrite)
                copiedPaths.add(destFile.absolutePath)
                successCount++
                
            } catch (e: Exception) {
                errors.add("Failed to copy ${source.name}: ${e.message}")
            }
        }
        
        return when {
            successCount == operation.sources.size -> FileOperationResult.Success(successCount, operation, copiedPaths)
            successCount > 0 -> FileOperationResult.PartialSuccess(successCount, errors.size, errors)
            else -> FileOperationResult.Failure("All copy operations failed")
        }
    }
    
    private fun executeMove(operation: FileOperation.Move): FileOperationResult {
        val errors = mutableListOf<String>()
        val movedPaths = mutableListOf<String>()
        var successCount = 0
        
        operation.sources.forEach { source ->
            try {
                if (!source.exists()) {
                    errors.add("File not found: ${source.name}")
                    return@forEach
                }
                
                val destFile = File(operation.destination, source.name)
                
                if (destFile.exists() && !operation.overwrite) {
                    errors.add("File already exists: ${source.name}")
                    return@forEach
                }
                
                if (source.renameTo(destFile)) {
                    movedPaths.add(destFile.absolutePath)
                    successCount++
                } else {
                    source.copyTo(destFile, operation.overwrite)
                    if (source.delete()) {
                        movedPaths.add(destFile.absolutePath)
                        successCount++
                    } else {
                        errors.add("Failed to delete source after copy: ${source.name}")
                    }
                }
                
            } catch (e: Exception) {
                errors.add("Failed to move ${source.name}: ${e.message}")
            }
        }
        
        return when {
            successCount == operation.sources.size -> FileOperationResult.Success(successCount, operation, movedPaths)
            successCount > 0 -> FileOperationResult.PartialSuccess(successCount, errors.size, errors)
            else -> FileOperationResult.Failure("All move operations failed")
        }
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
