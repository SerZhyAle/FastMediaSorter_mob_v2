package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.sza.fastmediasorter.R
import timber.log.Timber
import java.io.File

internal class LocalRenameFileOperation(
    private val context: Context,
    private val scanNewFile: (String) -> Unit
) {

    fun execute(operation: FileOperation.Rename): FileOperationResult {
        try {
            val filePath = operation.file.path

            if (filePath.startsWith("content:/")) {
                val normalizedUri = if (filePath.startsWith("content://")) filePath
                                   else filePath.replaceFirst("content:/", "content://")
                val uri = Uri.parse(normalizedUri)

                return try {
                    val newUri = DocumentsContract.renameDocument(context.contentResolver, uri, operation.newName)
                    if (newUri != null) {
                        FileOperationResult.Success(1, operation, listOf(newUri.toString()))
                    } else {
                        FileOperationResult.Failure("Failed to rename SAF document: ${operation.file.name}")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "SAF rename failed")
                    FileOperationResult.Failure("SAF rename error: ${e.message}")
                }
            }

            if (!operation.file.exists()) {
                return FileOperationResult.Failure("File not found: ${operation.file.name}")
            }

            val newFile = if (filePath.startsWith("smb://") || filePath.startsWith("sftp://") || filePath.startsWith("ftp://")) {
                val lastSlashIndex = filePath.lastIndexOf('/')
                val parentPath = filePath.substring(0, lastSlashIndex)
                val newPath = "$parentPath/${operation.newName}"
                object : File(newPath) {
                    override fun getPath(): String = newPath
                    override fun getAbsolutePath(): String = newPath
                }
            } else {
                File(operation.file.parent, operation.newName)
            }

            if (newFile.exists()) {
                return FileOperationResult.Failure(context.getString(R.string.file_already_exists, operation.newName))
            }

            if (operation.file.renameTo(newFile)) {
                scanNewFile(newFile.absolutePath)
                return FileOperationResult.Success(1, operation, listOf(newFile.absolutePath))
            } else {
                return FileOperationResult.Failure("Failed to rename ${operation.file.name}")
            }

        } catch (e: Exception) {
            return FileOperationResult.Failure("Rename error: ${e.message}")
        }
    }
}
