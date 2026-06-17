package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.domain.usecase.MergeDrawOverlayUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Prepares the draw editor's composited image for the unified «Send to..» menu (S0192; folded into
 * S0459).
 *
 * Merges base image + overlay, stages the JPEG bytes to `cacheDir` (Android trims this), and returns
 * a FileProvider URI. The caller hands that URI to [com.sza.fastmediasorter.ui.share.SendToMenuManager],
 * which owns receiver gating and dispatch - this helper no longer fires its own ACTION_SEND.
 */
class DrawKeepExportHelper @Inject constructor(
    private val mergeDrawOverlayUseCase: MergeDrawOverlayUseCase
) {

    /**
     * Composites the overlay onto the base and stages it for sharing.
     *
     * @return [Result.success] with the FileProvider [Uri] of the merged JPEG, or [Result.failure]
     *         if merge or temp-file write fails - the caller decides whether to toast.
     */
    suspend fun prepareMergedImageUri(
        activity: Activity,
        baseBitmap: Bitmap,
        overlayBitmap: Bitmap
    ): Result<Uri> = runCatching {
        // 1. Merge to JPEG bytes
        val bytes = mergeDrawOverlayUseCase
            .execute(baseBitmap, overlayBitmap, Bitmap.CompressFormat.JPEG, quality = 95)
            .getOrElse { throw it }

        // 2. Stage to cacheDir (Android trims this automatically - no manual cleanup)
        val cacheFile = withContext(Dispatchers.IO) {
            val file = File(activity.cacheDir, "draw_keep_export_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { it.write(bytes) }
            file
        }

        // 3. Build FileProvider URI (cache-path mapping is declared in res/xml/file_provider_paths.xml)
        val authority = "${activity.packageName}.fileprovider"
        FileProvider.getUriForFile(activity, authority, cacheFile)
    }
}
