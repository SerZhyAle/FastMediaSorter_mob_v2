package com.sza.fastmediasorter.wear.data.files

import android.content.Context
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardKind
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardSaved
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardSink
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardValue
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4227: where the watch keeps the last received clipboard value that has no media collection - a text, a PDF,
 * an archive. Such a file is listed nowhere on the watch (`WearWatchFileTarget` names three collections only), so
 * keeping every one would fill the watch with files nobody can find; one value is kept and the next replaces it
 * (strategic ADR-4). The previous value is removed only after the new one is placed, so a failed receive keeps it.
 */
@Singleton
class WearServerClipboardSlot @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val directory: File
        get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    fun freeBytes(): Long = directory.usableSpace

    /** A sink writing to a temp file in the slot; [WearServerClipboardSink.commit] names it [displayName]. */
    fun open(displayName: String): WearServerClipboardSink? = try {
        val temp = File.createTempFile(TEMP_PREFIX, TEMP_SUFFIX, directory)
        TempFileSink(temp, FileOutputStream(temp), displayName)
    } catch (e: IOException) {
        Timber.w(e, "Clipboard slot: no temp file")
        null
    }

    private inner class TempFileSink(
        private val temp: File,
        override val output: OutputStream,
        private val displayName: String,
    ) : WearServerClipboardSink {

        override fun commit(): WearServerClipboardSaved? {
            closeQuietly()
            val target = File(directory, displayName)
            val placed = (!target.exists() || target.delete()) && temp.renameTo(target)
            if (!placed) {
                Timber.w("Clipboard slot: the received value could not be placed")
                discard()
            }
            return if (placed) {
                directory.listFiles()?.filter { it != target }?.forEach { it.delete() }
                WearServerClipboardSaved(displayName, path = target.absolutePath, contentUri = null)
            } else {
                null
            }
        }

        override fun discard() {
            closeQuietly()
            if (temp.exists() && !temp.delete()) {
                Timber.w("Clipboard slot: a partial value could not be removed")
            }
        }

        private fun closeQuietly() {
            try {
                output.close()
            } catch (e: IOException) {
                Timber.w(e, "Clipboard slot: closing the temp file failed")
            }
        }
    }

    companion object {
        private const val DIRECTORY = "server-clipboard"
        private const val TEMP_PREFIX = ".receiving-"
        private const val TEMP_SUFFIX = ".part"
        private const val FALLBACK_STEM = "clipboard"
        private const val TEXT_EXTENSION = "txt"
        private val REFUSED_CHARACTERS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

        /**
         * The name a received value is saved under (contract 22 item CB): the base name of `name` only, with
         * characters a file system refuses replaced; a text is a `.txt` file, and a value with no name is named
         * after `storedAt` and its MIME type. Collisions are left to the store that places it.
         */
        fun safeName(value: WearServerClipboardValue): String {
            val given = value.name
                ?.substringAfterLast('/')
                ?.substringAfterLast('\\')
                ?.replace(REFUSED_CHARACTERS, "_")
                ?.trim()
                ?.takeUnless { it.isEmpty() || it == "." || it == ".." }
            return when {
                value.kind == WearServerClipboardKind.TEXT -> "${stemOf(value)}.$TEXT_EXTENSION"
                given != null -> given
                else -> "${stemOf(value)}.${extensionOf(value.mimeType)}"
            }
        }

        private fun stemOf(value: WearServerClipboardValue): String {
            val stamp = value.storedAt?.filter { it.isLetterOrDigit() }.orEmpty()
            return if (stamp.isEmpty()) FALLBACK_STEM else "$FALLBACK_STEM-$stamp"
        }

        private fun extensionOf(mimeType: String?): String = when (val subtype = mimeType?.substringAfter('/')) {
            null, "" -> "bin"
            "jpeg" -> "jpg"
            "plain" -> TEXT_EXTENSION
            else -> subtype.substringBefore('+').filter { it.isLetterOrDigit() }.ifEmpty { "bin" }
        }
    }
}
