package com.sza.fastmediasorter.ui.calculator.helpers

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CalculatorHistoryFileWriter {

    fun writeToDownloads(context: Context, content: String): String {
        val fileName = createFileName()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeWithMediaStore(context, fileName, content)
        } else {
            writeLegacy(context, fileName, content)
        }
        return fileName
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeWithMediaStore(context: Context, fileName: String, content: String) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME_TEXT)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, contentValues)
            ?: error("Failed to create calculator history file")

        runCatching {
            resolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                writer.write(content)
                writer.newLine()
            } ?: error("Failed to open calculator history file")
        }.onFailure {
            // The pending row would otherwise stay in Downloads as an invisible, never-finished file.
            runCatching { resolver.delete(uri, null, null) }
                .onFailure { Timber.w(it, "Calculator history: pending Downloads row not removed") }
        }.getOrThrow()

        contentValues.clear()
        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)
    }

    private fun writeLegacy(context: Context, fileName: String, content: String) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .also { it.mkdirs() }
        val file = File(downloadsDir, fileName)
        file.writeText("$content\n", Charsets.UTF_8)
        MediaStoreNotifier.notifyFile(context, file.absolutePath, "calculator-history")
    }

    private fun createFileName(): String {
        val stamp = SimpleDateFormat(FILE_STAMP_PATTERN, Locale.US).format(Date())
        return "FastMediaSorter_calculator_history_$stamp.txt"
    }

    private const val MIME_TEXT = "text/plain"
    private const val FILE_STAMP_PATTERN = "yyyyMMdd_HHmmss"
}
