package com.sza.fastmediasorter.data.transfer.strategy

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

internal class SftpLocalSourceReader(private val context: Context) {
    fun open(source: String): Result<Pair<InputStream, Long>> = runCatching {
        val uri = parseAndFixContentUri(source)
        if (uri.scheme == "content") {
            val stream = context.contentResolver.openInputStream(uri)
                ?: error("Cannot open source content URI")
            stream to contentSize(uri)
        } else {
            val file = File(uri.path ?: source)
            check(file.exists()) { "Source file not found" }
            FileInputStream(file) to file.length()
        }
    }

    private fun parseAndFixContentUri(path: String): Uri =
        Uri.parse(
            if (path.startsWith("content:") && !path.startsWith("content://")) {
                path.replaceFirst(
                    "content:/",
                    "content://"
                )
            } else {
                path
            }
        )

    private fun contentSize(uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && index >= 0) cursor.getLong(index) else 0L
        } ?: 0L
    }.getOrDefault(0L)
}
