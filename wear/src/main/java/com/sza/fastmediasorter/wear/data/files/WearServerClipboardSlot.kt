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
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
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
        private val STAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
        private val KNOWN_EXTENSIONS = mapOf(
            "image/png" to "png",
            "image/jpeg" to "jpg",
            "image/gif" to "gif",
            "image/webp" to "webp",
            "image/heic" to "heic",
            "image/bmp" to "bmp",
            "image/svg+xml" to "svg",
            "text/plain" to TEXT_EXTENSION,
            "text/html" to "html",
            "application/pdf" to "pdf",
            "application/zip" to "zip",
            "application/octet-stream" to "bin",
        )

        /**
         * The name a received value is saved under (contract 22 item CB): the base name of `name` only, with
         * characters a file system refuses replaced; a text, and a value with no name, is named as 1.11 item EL
         * says - `clipboard-<yyyyMMdd-HHmmss><ext>` from `storedAt` in UTC, else [now]. Collisions are left to the
         * store that places it.
         */
        fun safeName(value: WearServerClipboardValue, now: Instant = Instant.now()): String {
            val given = value.name
                ?.substringAfterLast('/')
                ?.substringAfterLast('\\')
                ?.replace(REFUSED_CHARACTERS, "_")
                ?.trim()
                ?.takeUnless { it.isEmpty() || it == "." || it == ".." }
            val extension = if (value.kind == WearServerClipboardKind.TEXT) {
                TEXT_EXTENSION
            } else {
                KNOWN_EXTENSIONS[value.mimeType?.lowercase()]
            }
            return given?.takeIf { value.kind != WearServerClipboardKind.TEXT }
                ?: (stemOf(value, now) + extension?.let { ".$it" }.orEmpty())
        }

        private fun stemOf(value: WearServerClipboardValue, now: Instant): String {
            val stored = value.storedAt?.let {
                try {
                    OffsetDateTime.parse(it).toInstant()
                } catch (_: DateTimeParseException) {
                    null
                }
            }
            return "$FALLBACK_STEM-${STAMP.format(stored ?: now)}"
        }
    }
}
