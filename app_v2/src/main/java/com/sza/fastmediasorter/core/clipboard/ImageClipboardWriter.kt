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

    private companion object {
        private const val CLIPBOARD_DIR = "clipboard"
        private const val CLIPBOARD_FILE = "screenshot_clip.png"
        private const val CLIP_LABEL = "Screenshot"
        private const val PNG_QUALITY = 100
    }
}
