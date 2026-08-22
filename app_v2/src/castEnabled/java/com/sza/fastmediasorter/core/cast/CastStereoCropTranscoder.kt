package com.sza.fastmediasorter.core.cast

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.coroutines.resume

/** Result of an attempted Cast single-eye export. */
sealed interface CastStereoCropResult {
    /** A cropped copy is ready for the proxy to serve. */
    data class Cropped(val file: File) : CastStereoCropResult

    /** The source exceeded the bounded encoding budget and is cast unchanged. */
    data object SkippedLong : CastStereoCropResult

    /** Export could not run or complete, so the caller keeps the original source. */
    data object Failed : CastStereoCropResult
}

/**
 * Produces a temporary half-frame copy for Cast without changing the source file.
 *
 * [Crop] takes `(left, right, bottom, top)` in the normalized `[-1, 1]` frame. RIGHT_HALF keeps
 * `x` in `[0, 1]`; BOTTOM_HALF keeps `y` in `[-1, 0]`. This mirrors
 * `PanelStereoCropApplier.buildMatrixFor`: its view Y axis points down, so the panel's bottom half
 * maps to negative normalized Y here.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class CastStereoCropTranscoder {

    private var transformer: Transformer? = null
    private var listener: Transformer.Listener? = null

    suspend fun crop(
        context: Context,
        source: File,
        crop: CastStereoCrop,
        outputDir: File,
    ): CastStereoCropResult {
        val durationMs = withContext(Dispatchers.IO) {
            readDurationMs(context.applicationContext, source)
        }
        return when {
            durationMs > MAX_CROP_DURATION_MS -> {
                Timber.i(
                    "CastStereoCropTranscoder: skipped ${source.name}; durationMs=$durationMs exceeds " +
                        "max=$MAX_CROP_DURATION_MS",
                )
                CastStereoCropResult.SkippedLong
            }
            durationMs <= 0L || !outputDir.exists() && !outputDir.mkdirs() -> {
                CastStereoCropResult.Failed
            }
            else -> export(context.applicationContext, source, crop, outputDir)
        }
    }

    private suspend fun export(
        context: Context,
        source: File,
        crop: CastStereoCrop,
        outputDir: File,
    ): CastStereoCropResult {
        val output = File(outputDir, "${source.nameWithoutExtension}$OUTPUT_SUFFIX")
        output.delete()
        val effect = when (crop) {
            CastStereoCrop.RIGHT_HALF -> Crop(NEGATIVE_NDC_BOUND, NDC_ORIGIN, NEGATIVE_NDC_BOUND, NDC_BOUND)
            CastStereoCrop.BOTTOM_HALF -> Crop(NEGATIVE_NDC_BOUND, NDC_BOUND, NDC_ORIGIN, NDC_BOUND)
        }
        val editedItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(source)))
            .setEffects(Effects(emptyList(), listOf(effect)))
            .build()

        return suspendCancellableCoroutine { continuation ->
            lateinit var pass: Transformer
            val exportListener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    detachTransformer(pass, this)
                    if (continuation.isActive) {
                        continuation.resume(CastStereoCropResult.Cropped(output))
                    } else {
                        output.delete()
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    detachTransformer(pass, this)
                    output.delete()
                    Timber.w(exportException, "CastStereoCropTranscoder: export failed; kept original")
                    if (continuation.isActive) continuation.resume(CastStereoCropResult.Failed)
                }
            }
            listener = exportListener
            pass = Transformer.Builder(context.applicationContext).addListener(exportListener).build()
            transformer = pass
            continuation.invokeOnCancellation {
                runCatching { pass.cancel() }
                output.delete()
                detachTransformer(pass, exportListener)
            }
            runCatching { pass.start(editedItem, output.absolutePath) }
                .onFailure { error ->
                    detachTransformer(pass, exportListener)
                    output.delete()
                    Timber.w(error, "CastStereoCropTranscoder: could not start export; kept original")
                    if (continuation.isActive) continuation.resume(CastStereoCropResult.Failed)
                }
        }
    }

    /** Cancels an active export and detaches its listener before the Cast owner is released. */
    fun release() {
        val activeTransformer = transformer ?: return
        val activeListener = listener
        runCatching { activeTransformer.cancel() }
            .onFailure { Timber.w(it, "CastStereoCropTranscoder: cancel failed") }
        detachTransformer(activeTransformer, activeListener)
    }

    private fun detachTransformer(activeTransformer: Transformer, activeListener: Transformer.Listener?) {
        activeListener?.let { active -> runCatching { activeTransformer.removeListener(active) } }
        if (transformer === activeTransformer) {
            transformer = null
            listener = null
        }
    }

    private fun readDurationMs(context: Context, source: File): Long {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(context, Uri.fromFile(source))
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }.getOrDefault(0L).also {
            runCatching { retriever.release() }
        }
    }

    private companion object {
        const val MAX_CROP_DURATION_MS = 5L * 60L * 1_000L
        const val NEGATIVE_NDC_BOUND = -1f
        const val NDC_ORIGIN = 0f
        const val NDC_BOUND = 1f
        const val OUTPUT_SUFFIX = "_cast_single_eye.mp4"
    }
}
