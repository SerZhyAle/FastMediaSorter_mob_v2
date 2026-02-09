package com.sza.fastmediasorter.data.transfer

/**
 * Exception thrown when a file operation fails because the destination file already exists
 * and overwrite is disabled.
 * 
 * @param fileName The name of the file that already exists
 * @param destinationPath The full path to the destination where file exists
 * @param isMove True if this was a move operation, false if copy
 */
class FileExistsException(
    val fileName: String,
    val destinationPath: String,
    val isMove: Boolean = false
) : Exception("File already exists: $fileName at $destinationPath")
