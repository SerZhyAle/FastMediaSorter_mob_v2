package com.sza.fastmediasorter.wear.data.wear

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import com.sza.fastmediasorter.wear.domain.repository.WearScreenCapture
import com.sza.fastmediasorter.wear.domain.repository.WearScreenCaptureResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** PNG ignores the quality argument, but the API demands one. */
private const val PNG_QUALITY = 100

/** Kept out of the cache root so the sweep below can empty it without judging a neighbour's file. */
private const val SCREENSHOT_DIR = "screenshots"

private const val FILE_NAME_PATTERN = "yyyyMMdd-HHmmss"

/**
 * S3110: photographs the watch app's own foreground window with `PixelCopy`.
 *
 * Deliberately not `MediaProjection`, which is the only way to reach the rest of the display: it
 * demands a system consent dialog on the watch for every session, and a request the wearer has to
 * approve on the watch is not a request the phone can be said to have initiated (strategic ADR-1).
 */
@Singleton
class PixelCopyWearScreenCapture @Inject constructor(
    @ApplicationContext private val context: Context,
    private val windowHolder: WearForegroundWindowHolder
) : WearScreenCapture {

    override suspend fun capture(): WearScreenCaptureResult {
        val window = windowHolder.currentWindow()
        val width = window?.decorView?.width ?: 0
        val height = window?.decorView?.height ?: 0

        return when {
            window == null ->
                WearScreenCaptureResult(reason = WearScreenshotRefusalReasons.NO_FOREGROUND_SCREEN)
            width <= 0 || height <= 0 ->
                WearScreenCaptureResult(reason = WearScreenshotRefusalReasons.CAPTURE_FAILED)
            else -> captureWindow(window, width, height)
        }
    }

    private suspend fun captureWindow(window: Window, width: Int, height: Int): WearScreenCaptureResult {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // Not `finally`, and the copy is uncancellable: recycling a bitmap the platform is still
        // writing into takes the process down, so a cancelled capture leaves one bitmap to the
        // collector rather than racing PixelCopy for it.
        val copied = withContext(NonCancellable) { copyWindow(window, bitmap) }
        val result = if (copied) {
            writePng(bitmap)
        } else {
            WearScreenCaptureResult(reason = WearScreenshotRefusalReasons.CAPTURE_FAILED)
        }
        bitmap.recycle()
        return result
    }

    /**
     * The `Window` overload is deprecated from API 34 in favour of `PixelCopy.Request`, which does not
     * exist on the API 26 this module still supports; a version branch here would carry two code paths
     * for one picture, so the older call stays until the module's floor moves.
     */
    @Suppress("DEPRECATION")
    private suspend fun copyWindow(window: Window, bitmap: Bitmap): Boolean =
        suspendCancellableCoroutine { continuation ->
            val listener = PixelCopy.OnPixelCopyFinishedListener { result ->
                if (continuation.isActive) {
                    continuation.resume(result == PixelCopy.SUCCESS)
                }
            }
            // Throws rather than reporting when the window has no backing surface yet, which is a
            // refusal like any other and must not take the listener service down with it.
            runCatching {
                PixelCopy.request(window, bitmap, listener, Handler(Looper.getMainLooper()))
            }.onFailure { failure ->
                Timber.w(failure, "PixelCopy refused the watch window")
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }
        }

    private suspend fun writePng(bitmap: Bitmap): WearScreenCaptureResult = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, SCREENSHOT_DIR).apply { mkdirs() }
        val target = File(directory, "watch-screen-${stamp()}.png")

        val written = runCatching {
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        }.onFailure { failure ->
            Timber.w(failure, "Failed to write the watch screenshot to the cache")
        }.getOrDefault(false)

        // Anything else in here is the remains of an attempt that never reached the phone: the sender
        // deletes its own file, so a leftover has no owner left to want it.
        directory.listFiles()?.forEach { file ->
            if (file != target) {
                file.delete()
            }
        }

        if (written && target.length() > 0L) {
            WearScreenCaptureResult(file = target)
        } else {
            WearScreenCaptureResult(reason = WearScreenshotRefusalReasons.CAPTURE_FAILED)
        }
    }

    private fun stamp(): String =
        SimpleDateFormat(FILE_NAME_PATTERN, Locale.US).format(Date())
}
