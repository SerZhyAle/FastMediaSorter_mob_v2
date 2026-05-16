package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.domain.usecase.MergeDrawOverlayUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Google Keep export pipeline for the draw editor (S0192 Phase 04).
 *
 * Merges base image + overlay, stages the JPEG bytes to `cacheDir`, builds a
 * FileProvider URI, and fires `ACTION_SEND` targeted at Google Keep. Falls back
 * to the generic share chooser when Keep is not installed (ADR-8).
 *
 * Editor stays open after the intent is fired — the user continues editing or
 * closes the editor manually.
 */
class DrawKeepExportHelper @Inject constructor(
    private val mergeDrawOverlayUseCase: MergeDrawOverlayUseCase
) {

    /**
     * Composites the overlay onto the base and shares the result.
     *
     * @return [Result.success] once the share intent has been started.
     *         [Result.failure] if merge or temp-file write fails — caller
     *         decides whether to toast.
     */
    suspend fun export(
        activity: Activity,
        baseBitmap: Bitmap,
        overlayBitmap: Bitmap
    ): Result<Unit> = runCatching {
        // 1. Merge to JPEG bytes
        val bytes = mergeDrawOverlayUseCase
            .execute(baseBitmap, overlayBitmap, Bitmap.CompressFormat.JPEG, quality = 95)
            .getOrElse { throw it }

        // 2. Stage to cacheDir (Android trims this automatically — no manual cleanup)
        val cacheFile = withContext(Dispatchers.IO) {
            val file = File(activity.cacheDir, "draw_keep_export_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { it.write(bytes) }
            file
        }

        // 3. Build FileProvider URI (cache-path mapping is declared in res/xml/file_provider_paths.xml)
        val authority = "${activity.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(activity, authority, cacheFile)

        // 4. Build the share intent — addFlags + clipData together so Android 11+
        //    consistently grants read access to the receiving app (Antigravity §9.2).
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "")
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // 5. Prefer Keep directly; if absent, fall back to the generic chooser (ADR-8)
        val keepIntent = Intent(intent).setPackage("com.google.android.keep")
        if (keepIntent.resolveActivity(activity.packageManager) != null) {
            activity.startActivity(keepIntent)
        } else {
            activity.startActivity(Intent.createChooser(intent, null))
        }
    }
}
