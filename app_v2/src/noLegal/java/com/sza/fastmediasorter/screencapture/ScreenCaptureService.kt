package com.sza.fastmediasorter.screencapture

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.SaveScreenshotUseCase
import com.sza.fastmediasorter.util.ScreenshotDestinationPolicy
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ScreenCaptureService : Service() {

    @Inject
    lateinit var settingsRepository: Lazy<SettingsRepository>

    @Inject
    lateinit var resourceRepository: Lazy<ResourceRepository>

    @Inject
    lateinit var saveScreenshotUseCase: Lazy<SaveScreenshotUseCase>

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureFinished = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            if (captureFinished) return
            Timber.w("ScreenCaptureService: MediaProjection stopped before save finished")
            finishWithError()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (mediaProjection != null || captureFinished) {
            return START_NOT_STICKY
        }

        startForegroundCompat()

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData = intent?.readResultData()
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            Timber.w("ScreenCaptureService: missing MediaProjection consent extras")
            finishWithError()
            return START_NOT_STICKY
        }

        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        if (projectionManager == null) {
            Timber.w("ScreenCaptureService: MediaProjectionManager unavailable")
            finishWithError()
            return START_NOT_STICKY
        }

        val projection = projectionManager.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            Timber.w("ScreenCaptureService: getMediaProjection returned null")
            finishWithError()
            return START_NOT_STICKY
        }

        mediaProjection = projection
        projection.registerCallback(projectionCallback, mainHandler)

        val spec = captureSpec()
        val reader = ImageReader.newInstance(spec.width, spec.height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        reader.setOnImageAvailableListener({ onImageAvailable() }, mainHandler)

        virtualDisplay = projection.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            spec.width,
            spec.height,
            spec.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            mainHandler
        )

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseCaptureResources()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun onImageAvailable() {
        if (captureFinished) return
        val image = imageReader?.acquireLatestImage() ?: return
        captureFinished = true
        Timber.d("S0405: screen capture frame acquired -> save")
        serviceScope.launch {
            processCapture(image)
        }
    }

    private suspend fun processCapture(image: Image) {
        try {
            val bitmap = withContext(Dispatchers.Default) { imageToBitmap(image) }
            val settings = settingsRepository.get().getSettings().first()
            val resources = resourceRepository.get().getAllResourcesSync()
            val target = ScreenshotDestinationPolicy().resolve(
                selectedResourceId = settings.screenshotDestinationResourceId,
                resources = resources
            )

            when (val result = saveScreenshotUseCase.get().invoke(bitmap, target)) {
                is SaveScreenshotUseCase.SaveResult.Success -> {
                    toast(getString(R.string.screen_capture_saved_to, result.destinationLabel, result.fileName))
                    finishSuccessfully()
                }
                is SaveScreenshotUseCase.SaveResult.Failure -> {
                    Timber.e(result.error, "ScreenCaptureService: screenshot save failed")
                    finishWithError()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "ScreenCaptureService: capture processing failed")
            finishWithError()
        } finally {
            image.close()
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes.first()
        val width = image.width
        val height = image.height
        val rowPadding = plane.rowStride - plane.pixelStride * width
        val bitmap = Bitmap.createBitmap(
            width + rowPadding / plane.pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(plane.buffer)
        val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
        if (cropped != bitmap) {
            bitmap.recycle()
        }
        return cropped
    }

    private fun captureSpec(): CaptureSpec {
        val metrics = resources.displayMetrics
        val windowManager = getSystemService(WindowManager::class.java)
        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager?.maximumWindowMetrics?.bounds
        } else {
            null
        }
        val width = bounds?.width() ?: metrics.widthPixels
        val height = bounds?.height() ?: metrics.heightPixels
        return CaptureSpec(width = width, height = height, densityDpi = metrics.densityDpi)
    }

    private fun finishSuccessfully() {
        releaseCaptureResources()
        stopForegroundCompat()
        stopSelf()
    }

    private fun finishWithError() {
        releaseCaptureResources()
        toast(getString(R.string.save_frame_error), Toast.LENGTH_LONG)
        stopForegroundCompat()
        stopSelf()
    }

    private fun releaseCaptureResources() {
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null

        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun startForegroundCompat() {
        createChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_screen_capture)
            .setContentTitle(getString(R.string.screen_capture_service_notification_title))
            .setContentText(getString(R.string.screen_capture_service_notification_text))
            .setOngoing(true)
            .setSilent(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.screen_capture_service_notification_title),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun toast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }

    private fun Intent.readResultData(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(EXTRA_RESULT_DATA)
        }

    private data class CaptureSpec(
        val width: Int,
        val height: Int,
        val densityDpi: Int
    )

    companion object {
        const val EXTRA_RESULT_CODE = "screen_capture_result_code"
        const val EXTRA_RESULT_DATA = "screen_capture_result_data"

        private const val CHANNEL_ID = "screen_capture_service"
        private const val NOTIFICATION_ID = 0x4053
        private const val VIRTUAL_DISPLAY_NAME = "screen_capture_service"

        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
