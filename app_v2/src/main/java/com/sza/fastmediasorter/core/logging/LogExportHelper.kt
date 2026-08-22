package com.sza.fastmediasorter.core.logging

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.BufferedOutputStream
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
        data object SaveSuccess : ExportResult()
        data object NoLogs : ExportResult()
        data class Error(val message: String) : ExportResult()
    }

    private const val ZIP_FILE_NAME = "fastmediasorter_logs.zip"
    private const val AUTHORITY_SUFFIX = ".fileprovider"

    /**
     * Package all log files into a ZIP and share via Intent
     */
    fun exportLogs(context: Context): ExportResult {
        val zipFile = buildLogsZip(context) ?: return ExportResult.NoLogs
        return shareZipFile(context, zipFile)
    }

    /**
     * Package all log files into the cache ZIP and return a shareable content:// URI, or null when
     * there are no logs or packaging failed. Performs disk I/O - call off the main thread.
     */
    fun buildLogsZipUri(context: Context, extraFiles: List<File> = emptyList()): Uri? {
        val zipFile = buildLogsZip(context, extraFiles) ?: return null
        return try {
            FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", zipFile)
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "LogExportHelper: failed to resolve FileProvider URI for log ZIP")
            null
        }
    }

    private fun buildLogsZip(
        context: Context,
        extraFiles: List<File> = emptyList()
    ): File? = StrictModeHelper.allowDiskIO {
        try {
            // Extra files sit beside the phone's own set rather than replacing it, and an empty phone
            // log set no longer means an empty zip - a watch report is worth sending on its own.
            //
            // S1806: distinctBy is load-bearing, not tidiness. The set now already contains every
            // watch report in the log directory, and the notification path passes the report it just
            // wrote as an extra - the same file twice would make ZipOutputStream throw on the second
            // entry of that name, losing the whole archive rather than one file.
            val logFiles = (LoggingHelper.getExportableLogFiles(context) + extraFiles)
                .distinctBy { file -> file.absolutePath }
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
    }

    /**
     * Write all log files as a ZIP directly into a URI chosen by the user (SAF).
     */
    fun writeZipToUri(context: Context, destUri: Uri): ExportResult {
        return try {
            val logFiles = StrictModeHelper.allowDiskIO { LoggingHelper.getExportableLogFiles(context) }
            if (logFiles.isNullOrEmpty()) return ExportResult.NoLogs

            context.contentResolver.openOutputStream(destUri)?.use { out ->
                ZipOutputStream(BufferedOutputStream(out)).use { zos ->
                    for (file in logFiles) {
                        StrictModeHelper.allowDiskIO {
                            if (file.exists()) {
                                zos.putNextEntry(ZipEntry(file.name))
                                FileInputStream(file).use { fis -> fis.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                    }
                }
            } ?: return ExportResult.Error(context.getString(R.string.save_logs_failed))

            ExportResult.SaveSuccess
        } catch (e: Exception) {
            Timber.e(e, "LogExportHelper: failed to write ZIP to URI")
            ExportResult.Error(context.getString(R.string.save_logs_failed))
        }
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
