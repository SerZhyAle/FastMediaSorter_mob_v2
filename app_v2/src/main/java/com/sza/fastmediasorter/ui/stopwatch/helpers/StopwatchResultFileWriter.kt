package com.sza.fastmediasorter.ui.stopwatch.helpers

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes a rendered stopwatch result into the user's Downloads folder.
 *
 * The platform branch is the hard constraint of S1411 §3.2 rather than a preference: `legacy` still ships
 * to minSdk 23, where `MediaStore.Downloads` does not exist, and above API 29 the public directory is no
 * longer writable. Both halves follow `CalculatorHistoryFileWriter`, the path already proven in this
 * application, so the two exports behave identically for the user.
 *
 * The outcome is returned rather than logged and dropped: the caller turns it into the message the user
 * sees, and a save that failed silently would look exactly like one that worked.
 */
object StopwatchResultFileWriter {

    /** The written file's display name on success, the cause on failure. */
    fun writeToDownloads(context: Context, content: String): Result<String> = runCatching {
        val fileName = createFileName()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeWithMediaStore(context, fileName, content)
        } else {
            writeLegacy(context, fileName, content)
        }
        fileName
    }

    private fun writeWithMediaStore(context: Context, fileName: String, content: String) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME_TEXT)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            // Held pending until the bytes are on disk, so nothing else can open a half-written file.
            put(MediaStore.MediaColumns.IS_PENDING, PENDING)
        }
        val uri = resolver.insert(collection, contentValues)
            ?: error("Failed to create the stopwatch result file")

        resolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
            writer.write(content)
            writer.newLine()
        } ?: error("Failed to open the stopwatch result file")

        contentValues.clear()
        contentValues.put(MediaStore.MediaColumns.IS_PENDING, PUBLISHED)
        resolver.update(uri, contentValues, null, null)
    }

    private fun writeLegacy(context: Context, fileName: String, content: String) {
        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .also { it.mkdirs() }
        val file = File(downloadsDir, fileName)
        file.writeText("$content\n", Charsets.UTF_8)
        // Below API 29 a freshly written file is invisible to every gallery and file manager until the
        // media database is told about it, so the user would not find what the app says it saved.
        MediaStoreNotifier.notifyFile(context, file.absolutePath, "stopwatch-result")
    }

    private fun createFileName(): String {
        val stamp = SimpleDateFormat(FILE_STAMP_PATTERN, Locale.US).format(Date())
        return "FastMediaSorter_stopwatch_$stamp.txt"
    }

    private const val MIME_TEXT = "text/plain"
    private const val FILE_STAMP_PATTERN = "yyyyMMdd_HHmmss"
    private const val PENDING = 1
    private const val PUBLISHED = 0
}
