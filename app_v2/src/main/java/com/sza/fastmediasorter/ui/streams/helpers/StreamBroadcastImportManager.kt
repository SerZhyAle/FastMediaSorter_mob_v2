package com.sza.fastmediasorter.ui.streams.helpers

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.util.showBoundTo
import timber.log.Timber
import java.io.ByteArrayOutputStream

/**
 * Owns the "Import Broadcast" entry on the streams toolbar: asks whether the descriptor arrives by
 * QR or as a file, and turns a picked file into the raw payload string the import use case takes.
 *
 * The two launchers stay in the Activity because `registerForActivityResult` has to run before the
 * host reaches STARTED; this manager only decides which of them to fire and how to read the result.
 */
class StreamBroadcastImportManager(
    private val activity: AppCompatActivity,
    private val onScanQrRequested: () -> Unit,
    private val onPickFileRequested: () -> Unit,
) {

    fun showImportChoice() {
        val options = arrayOf(
            activity.getString(R.string.broadcast_import_option_qr),
            activity.getString(R.string.broadcast_import_option_file),
        )
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.broadcast_import_dialog_title)
            .setItems(options) { _, which ->
                if (which == OPTION_QR) onScanQrRequested() else onPickFileRequested()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showBoundTo(activity)
    }

    /**
     * Returns null when the document cannot be read or is far larger than any descriptor, so the
     * caller reports a malformed payload instead of pushing an arbitrary file through the parser.
     */
    @Suppress("TooGenericExceptionCaught")
    fun readDescriptorFile(uri: Uri): String? {
        return try {
            activity.contentResolver.openInputStream(uri)?.use { stream ->
                // Bounded manually rather than through readNBytes, which is a Java 9 API this
                // module's minSdk cannot rely on.
                val sink = ByteArrayOutputStream()
                val chunk = ByteArray(READ_CHUNK_BYTES)
                var overflowed = false
                while (!overflowed) {
                    val read = stream.read(chunk)
                    if (read <= 0) break
                    sink.write(chunk, 0, read)
                    overflowed = sink.size() > MAX_DESCRIPTOR_BYTES
                }
                if (overflowed) {
                    Timber.d("StreamBroadcastImportManager: descriptor file exceeds the size cap")
                    null
                } else {
                    String(sink.toByteArray(), Charsets.UTF_8)
                }
            }
        } catch (e: Exception) {
            Timber.d(e, "StreamBroadcastImportManager: failed to read the descriptor file")
            null
        }
    }

    private companion object {
        const val OPTION_QR = 0

        /** A descriptor is a short JSON blob or its gzipped Base64 form; 64 KB is already generous. */
        const val MAX_DESCRIPTOR_BYTES = 64 * 1024
        const val READ_CHUNK_BYTES = 4096
    }
}
