package com.sza.fastmediasorter.core.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Places an image on the system clipboard as an `image/png` content URI so it can be pasted into
 * other apps. The source is a dedicated app-cache copy exposed via FileProvider, independent of any
 * save destination - see S0468 research 03.
 */
class ImageClipboardWriter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Compresses [bitmap] to a cache PNG and sets it as the primary clip. Does not recycle [bitmap] -
     * the caller may still need it for a parallel save. Returns false (and logs) on any failure.
     */
    suspend fun copyBitmap(bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, CLIPBOARD_DIR).apply { mkdirs() }
            val file = File(dir, CLIPBOARD_FILE)
            FileOutputStream(file).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)) {
                    Timber.w("ImageClipboardWriter: PNG compression failed for clipboard copy")
                    return@withContext false
                }
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard == null) {
                Timber.w("ImageClipboardWriter: ClipboardManager unavailable")
                return@withContext false
            }
            clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, CLIP_LABEL, uri))
            true
        } catch (e: Exception) {
            Timber.w(e, "ImageClipboardWriter: failed to copy image to clipboard")
            false
        }
    }

    /**
     * Copies an already-encoded image [source] file onto the clipboard verbatim - no decode/re-encode,
     * so the pasted picture matches the saved capture exactly (S0469). The bytes are placed in a
     * dedicated app-cache file exposed via FileProvider; the receiver derives the MIME (e.g.
     * image/jpeg) from the file extension and must accept image content to paste the picture itself.
     * Returns false (and logs) on any failure; never throws.
     */
    suspend fun copyImageFile(source: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!source.exists() || source.length() == 0L) {
                Timber.w("ImageClipboardWriter: source image missing/empty for clipboard copy")
                return@withContext false
            }
            val dir = File(context.cacheDir, CLIPBOARD_DIR).apply { mkdirs() }
            val ext = source.extension.lowercase().ifBlank { "jpg" }
            val file = File(dir, "$CAPTURE_CLIP_BASENAME.$ext")
            source.inputStream().use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard == null) {
                Timber.w("ImageClipboardWriter: ClipboardManager unavailable")
                return@withContext false
            }
            clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, CAPTURE_CLIP_LABEL, uri))
            true
        } catch (e: Exception) {
            Timber.w(e, "ImageClipboardWriter: failed to copy image file to clipboard")
            false
        }
    }

    private companion object {
        private const val CLIPBOARD_DIR = "clipboard"
        private const val CLIPBOARD_FILE = "screenshot_clip.png"
        private const val CAPTURE_CLIP_BASENAME = "capture_clip"
        private const val CLIP_LABEL = "Screenshot"
        private const val CAPTURE_CLIP_LABEL = "Image"
        private const val PNG_QUALITY = 100
    }
}
