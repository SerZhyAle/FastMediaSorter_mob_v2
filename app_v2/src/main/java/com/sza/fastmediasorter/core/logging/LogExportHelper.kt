package com.sza.fastmediasorter.core.logging

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.debug.StrictModeHelper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import timber.log.Timber

/**
 * Utility to package and export application logs for debugging.
 */
object LogExportHelper {

    sealed class ExportResult {
        data object Success : ExportResult()
        data object NoLogs : ExportResult()
        data class Error(val message: String) : ExportResult()
    }

    private const val ZIP_FILE_NAME = "fastmediasorter_logs.zip"
    private const val AUTHORITY_SUFFIX = ".fileprovider"

    /**
     * Package all log files into a ZIP and share via Intent
     */
    fun exportLogs(context: Context): ExportResult {
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
                Timber.e(e, "LogExportHelper: failed to build ZIP")
                null
            }
        } ?: return ExportResult.NoLogs

        return shareZipFile(context, zipFile)
    }

    private fun shareZipFile(context: Context, zipFile: File): ExportResult {
        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}$AUTHORITY_SUFFIX",
                zipFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_logs_subject))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, context.getString(R.string.title_export_logs_chooser))
            if (context !is Activity) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            ExportResult.Success
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "LogExportHelper: no app can handle log sharing")
            ExportResult.Error(context.getString(R.string.export_logs_no_share_target))
        } catch (e: Exception) {
            Timber.e(e, "LogExportHelper: failed to share ZIP")
            ExportResult.Error(context.getString(R.string.export_logs_failed))
        }
    }
}
