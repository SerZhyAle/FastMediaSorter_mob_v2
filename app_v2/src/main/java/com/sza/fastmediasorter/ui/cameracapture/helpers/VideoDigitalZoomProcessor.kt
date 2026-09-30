package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import java.util.concurrent.Executors

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

    /** Bumped by [release]; read and written on the caller's Looper thread only. */
    private var generation = 0

    /**
     * Reports the pass in flight as "original kept" when [release] cancels it. A cancelled Transformer
     * never calls its listener, so without this the caller's completion never fired and a recording
     * stopped just before the screen closed was neither cropped nor saved. Caller's Looper thread only.
     */
    private var abandon: (() -> Unit)? = null

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
        val appContext = context.applicationContext
        val caller = Handler(Looper.myLooper() ?: Looper.getMainLooper())
        val pass = generation
        abandon = { caller.post { onDone(false) } }
        // The MP4 parse behind readHeight is disk work, and the caller is the main-thread finalize
        // callback; the Transformer itself must still be built on the caller's Looper.
        runOffCaller {
            val height = readHeight(appContext, file)
            val effects = zoomEffects(zoomFactor, height)
            caller.post { if (pass == generation) startPass(appContext, file, effects, caller, onDone) }
        }
    }

    private fun zoomEffects(zoomFactor: Float, height: Int): List<Effect> {
        // Keep the centred 1/f fraction of the full [-1, 1] NDC range on both axes; the symmetric crop
        // preserves the frame aspect, so Presentation can scale it back without letterboxing.
        val halfExtent = NDC_HALF / zoomFactor
        val crop = Crop(-halfExtent, halfExtent, -halfExtent, halfExtent)
        return buildList {
            add(crop)
            // createForHeight preserves the (unchanged) aspect regardless of the container rotation, so
            // the output keeps roughly the source resolution without guessing coded-vs-display width.
            height.takeIf { it > 0 }?.let { add(Presentation.createForHeight(it)) }
        }
    }

    private fun startPass(
        appContext: Context,
        file: File,
        effects: List<Effect>,
        caller: Handler,
        onDone: (Boolean) -> Unit,
    ) {
        val output = File(file.parentFile, file.nameWithoutExtension + TEMP_SUFFIX)
        abandon = {
            runOffCaller { output.delete() }
            caller.post { onDone(false) }
        }
        val editedItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(file)))
            .setEffects(Effects(emptyList(), effects))
            .build()
        val exportListener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                detachTransformer()
                runOffCaller {
                    val swapped = swapInto(file, output)
                    caller.post { onDone(swapped) }
                }
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                detachTransformer()
                runOffCaller { output.delete() }
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
                runOffCaller { output.delete() }
                Timber.w(it, "VideoDigitalZoomProcessor: could not start zoom re-encode, kept original")
                onDone(false)
            }
    }

    /** Cancels any in-flight re-encode so a closed session never leaks the Transformer worker threads. */
    fun release() {
        // Drops a pass whose height read is still in flight, so nothing starts after the release.
        generation++
        val pending = abandon
        runCatching { transformer?.cancel() }
            .onFailure { Timber.w(it, "VideoDigitalZoomProcessor: cancel failed") }
        detachTransformer()
        pending?.invoke()
    }

    /** Detaches the export listener and drops the one-shot Transformer (symmetric release, S0767). */
    private fun detachTransformer() {
        listener?.let { active -> runCatching { transformer?.removeListener(active) } }
        transformer = null
        listener = null
        // Every end of a pass comes through here, so a later release has nothing left to report.
        abandon = null
    }

    /** Replaces [target] with [temp]; Linux rename overwrites atomically, with a delete + rename fallback. */
    private fun swapInto(target: File, temp: File): Boolean {
        val ok = runCatching {
            temp.renameTo(target) || (target.delete() && temp.renameTo(target))
        }.getOrDefault(false)
        if (!ok) temp.delete()
        return ok
    }

    // A one-shot worker shut down right after the submit, so no thread outlives the task.
    private fun runOffCaller(task: () -> Unit) {
        val worker = Executors.newSingleThreadExecutor()
        worker.execute(task)
        worker.shutdown()
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
