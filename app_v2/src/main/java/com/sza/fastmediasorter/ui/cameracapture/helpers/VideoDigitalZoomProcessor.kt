package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import timber.log.Timber
import java.io.File

/**
 * S1066: bakes the app-level digital zoom (the soft crop beyond the CameraX native max) into a
 * recorded MP4 so the saved video matches the digitally-zoomed preview (owner Q1: keep the extended
 * zoom range AND crop the video honestly). CameraX records the full ViewPort FOV, so on its own the
 * file is wider than the preview; this re-encodes the finished file with a centred crop by the same
 * factor via a single Media3 Transformer pass after [androidx.camera.video.VideoRecordEvent.Finalize].
 *
 * Approach B (post-record re-encode) is deterministic and testable and sidesteps the real-time
 * `PREVIEW|VIDEO_CAPTURE` GLES stream-sharing device-compat minefield of a live CameraEffect; it
 * mirrors the async post-capture JPEG crop already used for digital-zoom photos.
 */
// media3's @UnstableApi carries androidx.annotation.RequiresOptIn (not kotlin's), so the opt-in must
// come from androidx.annotation.OptIn; kotlin's @OptIn is a no-op here and Lint would flag the usage.
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class VideoDigitalZoomProcessor {

    /** The in-flight pass, kept so a closing session can cancel it instead of leaking (S0767). */
    private var transformer: Transformer? = null

    /** The pass's export listener, held so [detachTransformer] can remove it symmetrically. */
    private var listener: Transformer.Listener? = null

    /**
     * Re-encodes [file] in place with a centred crop by [zoomFactor] (> 1). Must be called on a thread
     * with a Looper (the CameraX finalize callback runs on the main thread); the encode itself happens
     * on the Transformer's own worker threads. [onDone] is posted back on that same Looper thread with
     * `true` when [file] now holds the cropped video and `false` when the original was kept unchanged
     * (any failure) - so the caller can complete the capture flow either way.
     */
    fun crop(context: Context, file: File, zoomFactor: Float, onDone: (Boolean) -> Unit) {
        if (zoomFactor <= NO_ZOOM) {
            onDone(false)
            return
        }
        Timber.d("S1066: video digital-zoom re-encode - zoom=$zoomFactor file=${file.name}")
        val appContext = context.applicationContext
        val output = File(file.parentFile, file.nameWithoutExtension + TEMP_SUFFIX)
        // Keep the centred 1/f fraction of the full [-1, 1] NDC range on both axes; the symmetric crop
        // preserves the frame aspect, so Presentation can scale it back without letterboxing.
        val halfExtent = NDC_HALF / zoomFactor
        val crop = Crop(-halfExtent, halfExtent, -halfExtent, halfExtent)
        val effects = buildList<Effect> {
            add(crop)
            // createForHeight preserves the (unchanged) aspect regardless of the container rotation, so
            // the output keeps roughly the source resolution without guessing coded-vs-display width.
            readHeight(appContext, file).takeIf { it > 0 }?.let { add(Presentation.createForHeight(it)) }
        }
        val editedItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(file)))
            .setEffects(Effects(emptyList(), effects))
            .build()
        val exportListener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                detachTransformer()
                onDone(swapInto(file, output))
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                detachTransformer()
                output.delete()
                Timber.w(exportException, "VideoDigitalZoomProcessor: zoom re-encode failed, kept original")
                onDone(false)
            }
        }
        listener = exportListener
        val pass = Transformer.Builder(appContext).addListener(exportListener).build()
        transformer = pass
        runCatching { pass.start(editedItem, output.absolutePath) }
            .onFailure {
                detachTransformer()
                output.delete()
                Timber.w(it, "VideoDigitalZoomProcessor: could not start zoom re-encode, kept original")
                onDone(false)
            }
    }

    /** Cancels any in-flight re-encode so a closed session never leaks the Transformer worker threads. */
    fun release() {
        runCatching { transformer?.cancel() }
            .onFailure { Timber.w(it, "VideoDigitalZoomProcessor: cancel failed") }
        detachTransformer()
    }

    /** Detaches the export listener and drops the one-shot Transformer (symmetric release, S0767). */
    private fun detachTransformer() {
        listener?.let { active -> runCatching { transformer?.removeListener(active) } }
        transformer = null
        listener = null
    }

    /** Replaces [target] with [temp]; Linux rename overwrites atomically, with a delete + rename fallback. */
    private fun swapInto(target: File, temp: File): Boolean {
        val ok = runCatching {
            temp.renameTo(target) || (target.delete() && temp.renameTo(target))
        }.getOrDefault(false)
        if (!ok) temp.delete()
        return ok
    }

    private fun readHeight(context: Context, file: File): Int {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(context, Uri.fromFile(file))
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        }.getOrDefault(0).also {
            runCatching { retriever.release() }
        }
    }

    private companion object {
        const val NO_ZOOM = 1f
        const val NDC_HALF = 1f
        const val TEMP_SUFFIX = "_s1066zoom.mp4"
    }
}
