package com.sza.fastmediasorter.core.logging

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.core.debug.StrictModeHelper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Utility to package and export application logs for debugging.
 */
object LogExportHelper {

    private const val ZIP_FILE_NAME = "fastmediasorter_logs.zip"
    private const val AUTHORITY_SUFFIX = ".fileprovider"

    /**
     * Package all log files into a ZIP and share via Intent
     */
    fun exportLogs(context: Context) {
        val zipFile = StrictModeHelper.allowDiskIO {
            try {
                val logFiles = LoggingHelper.getLogFiles()
                if (logFiles.isEmpty()) return@allowDiskIO null

                val cacheZip = File(context.cacheDir, ZIP_FILE_NAME)
                if (cacheZip.exists()) cacheZip.delete()

                ZipOutputStream(FileOutputStream(cacheZip)).use { zos: ZipOutputStream ->
                    logFiles.forEach { file: File ->
                        if (file.exists()) {
                            val entry = ZipEntry(file.name)
                            zos.putNextEntry(entry)
                            FileInputStream(file).use { fis: FileInputStream ->
                                fis.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }
                cacheZip
            } catch (e: Exception) {
                null
            }
        } ?: return

        shareZipFile(context, zipFile)
    }

    private fun shareZipFile(context: Context, zipFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}$AUTHORITY_SUFFIX",
            zipFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "FastMediaSorter Debug Logs")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Export Logs via..."))
    }
}
