package com.sza.fastmediasorter.core.logging

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.debug.StrictModeHelper
import com.sza.fastmediasorter.util.queryIntentActivitiesCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
    private const val MIB = 1024L * 1024L
    private const val ARCHIVE_FILE_CEILING_BYTES = 16 * MIB
    private const val ARCHIVE_HEAD_BYTES = 1 * MIB
    private const val ARCHIVE_TAIL_BYTES = 7 * MIB

    /**
     * Package all log files into a ZIP and share via Intent. The ZIP is built on [Dispatchers.IO];
     * the chooser starts on the caller's context, so call it from Main.
     */
    suspend fun exportLogs(context: Context): ExportResult {
        val zipFile = withContext(Dispatchers.IO) { buildLogsZip(context) } ?: return ExportResult.NoLogs
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
                writeEntries(zos, logFiles)
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
    suspend fun writeZipToUri(context: Context, destUri: Uri): ExportResult = withContext(Dispatchers.IO) {
        try {
            val logFiles = LoggingHelper.getExportableLogFiles(context)
            if (logFiles.isEmpty()) return@withContext ExportResult.NoLogs

            context.contentResolver.openOutputStream(destUri)?.use { out ->
                ZipOutputStream(BufferedOutputStream(out)).use { zos -> writeEntries(zos, logFiles) }
            } ?: return@withContext ExportResult.Error(context.getString(R.string.save_logs_failed))

            ExportResult.SaveSuccess
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "LogExportHelper: failed to write ZIP to URI")
            ExportResult.Error(context.getString(R.string.save_logs_failed))
        }
    }

    private fun writeEntries(zos: ZipOutputStream, files: List<File>) {
        files.filter { it.exists() }.forEach { file ->
            zos.putNextEntry(ZipEntry(file.name))
            writeBounded(file, zos)
            zos.closeEntry()
        }
    }

    /**
     * DIAGNOSTIC-REPORT rule 4, archive size guard: a file above [ceilingBytes] is packed as its
     * head and tail around a `[Diag] LOG TRUNCATED` marker, so the startup context and the recent
     * failure both survive while the entry stays bounded.
     */
    internal fun writeBounded(
        file: File,
        out: OutputStream,
        ceilingBytes: Long = ARCHIVE_FILE_CEILING_BYTES,
        headBytes: Long = ARCHIVE_HEAD_BYTES,
        tailBytes: Long = ARCHIVE_TAIL_BYTES
    ) {
        val length = file.length()
        if (length <= ceilingBytes) {
            FileInputStream(file).use { fis -> fis.copyTo(out) }
            return
        }
        val dropped = length - headBytes - tailBytes
        RandomAccessFile(file, "r").use { raf ->
            copyRange(raf, 0L, headBytes, out)
            val marker = "\n[Diag] LOG TRUNCATED | dropped_middle_bytes=$dropped" +
                " | kept_head_bytes=$headBytes | kept_tail_bytes=$tailBytes\n"
            out.write(marker.toByteArray(Charsets.UTF_8))
            copyRange(raf, length - tailBytes, tailBytes, out)
        }
    }

    private fun copyRange(raf: RandomAccessFile, start: Long, count: Long, out: OutputStream) {
        raf.seek(start)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var remaining = count
        while (remaining > 0) {
            val read = raf.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) break
            out.write(buffer, 0, read)
            remaining -= read
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

            if (context.packageManager.queryIntentActivitiesCompat(intent, 0).isEmpty()) {
                return ExportResult.Error(context.getString(R.string.export_logs_no_share_target))
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
