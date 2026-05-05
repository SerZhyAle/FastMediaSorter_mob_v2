package com.sza.fastmediasorter.vr.capture

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.os.Environment
import android.provider.MediaStore
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.util.VirtualPathUtils
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.ui.browse.managers.BrowsePassthroughCaptureProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val HEADSET_CAMERA_PERMISSION = "horizonos.permission.HEADSET_CAMERA"
private const val PERM_REQUEST_CODE = 0x5800  // S0058
private const val PERM_FRAGMENT_TAG = "vr_perm_bridge_s0058"

private val PASSTHROUGH_CAMERA_SOURCE_KEY = CameraCharacteristics.Key(
    "com.meta.extra_metadata.camera_source",
    Int::class.javaObjectType,
)

@Singleton
class VrBrowsePassthroughCaptureManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : BrowsePassthroughCaptureProvider {

    override fun isAvailable(context: Context): Boolean = try {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraManager.cameraIdList.any { id ->
            cameraManager.getCameraCharacteristics(id).get(PASSTHROUGH_CAMERA_SOURCE_KEY) == 0
        }
    } catch (_: Exception) {
        false
    }

    override fun launch(
        activity: FragmentActivity,
        resource: MediaResource,
        onFileSaved: (name: String) -> Unit,
    ) {
        if (ActivityCompat.checkSelfPermission(activity, HEADSET_CAMERA_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            activity.lifecycleScope.launch { capturePassthrough(activity, resource, onFileSaved) }
            return
        }
        val fragment = VrPermissionBridgeFragment().apply {
            onGranted = {
                activity.lifecycleScope.launch { capturePassthrough(activity, resource, onFileSaved) }
            }
            onDenied = { showRationale ->
                if (showRationale) {
                    Toast.makeText(
                        activity,
                        R.string.passthrough_capture_permission_needed,
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    Snackbar.make(
                        activity.window.decorView.rootView,
                        R.string.passthrough_capture_permission_denied,
                        Snackbar.LENGTH_LONG,
                    ).setAction(activity.getString(android.R.string.ok)) {
                        activity.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", activity.packageName, null)
                            }
                        )
                    }.show()
                }
            }
        }
        activity.supportFragmentManager.beginTransaction()
            .add(fragment, PERM_FRAGMENT_TAG)
            .commitAllowingStateLoss()
    }

    private suspend fun capturePassthrough(
        activity: FragmentActivity,
        resource: MediaResource,
        onFileSaved: (String) -> Unit,
    ) {
        val cameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(PASSTHROUGH_CAMERA_SOURCE_KEY) == 0
        }
        if (cameraId == null) {
            Timber.w("S0058: no passthrough camera ID found")
            withContext(Dispatchers.Main) {
                Toast.makeText(activity, R.string.passthrough_capture_unavailable, Toast.LENGTH_SHORT).show()
            }
            return
        }

        val handlerThread = HandlerThread("vr-passthrough-cam-s0058").also { it.start() }
        val handler = Handler(handlerThread.looper)
        val imageReader = ImageReader.newInstance(1280, 960, ImageFormat.JPEG, 2)
        val jpegDeferred = CompletableDeferred<ByteArray>()

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                jpegDeferred.complete(bytes)
            } finally {
                image.close()
            }
        }, handler)

        val deviceDeferred = CompletableDeferred<CameraDevice>()
        val stateCallback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) { deviceDeferred.complete(camera) }
            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                deviceDeferred.completeExceptionally(IllegalStateException("camera disconnected"))
            }
            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                deviceDeferred.completeExceptionally(IllegalStateException("camera error=$error"))
            }
        }
        try {
            cameraManager.openCamera(cameraId, stateCallback, handler)
        } catch (e: SecurityException) {
            Timber.e(e, "S0058: openCamera SecurityException")
            imageReader.close()
            handlerThread.quitSafely()
            withContext(Dispatchers.Main) {
                Toast.makeText(activity, R.string.passthrough_capture_error, Toast.LENGTH_SHORT).show()
            }
            return
        }

        val cameraDevice = try {
            deviceDeferred.await()
        } catch (e: Exception) {
            Timber.e(e, "S0058: camera open failed")
            imageReader.close()
            handlerThread.quitSafely()
            withContext(Dispatchers.Main) {
                Toast.makeText(activity, R.string.passthrough_capture_error, Toast.LENGTH_SHORT).show()
            }
            return
        }

        val sessionDeferred = CompletableDeferred<android.hardware.camera2.CameraCaptureSession>()
        cameraDevice.createCaptureSession(
            listOf(imageReader.surface),
            object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                    sessionDeferred.complete(session)
                }
                override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) {
                    sessionDeferred.completeExceptionally(IllegalStateException("session configure failed"))
                }
            },
            handler,
        )

        val session = try {
            sessionDeferred.await()
        } catch (e: Exception) {
            Timber.e(e, "S0058: capture session configure failed")
            cameraDevice.close()
            imageReader.close()
            handlerThread.quitSafely()
            withContext(Dispatchers.Main) {
                Toast.makeText(activity, R.string.passthrough_capture_error, Toast.LENGTH_SHORT).show()
            }
            return
        }

        val request = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(imageReader.surface)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }.build()
        session.capture(request, null, handler)

        @Suppress("DEPRECATION")
        val vibrator = activity.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(80L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator?.vibrate(80L)
        }

        withContext(Dispatchers.Main) {
            val root = activity.window.decorView.rootView
            val flash = android.view.View(activity).apply {
                setBackgroundColor(android.graphics.Color.WHITE)
                alpha = 0.85f
            }
            (root as? android.view.ViewGroup)?.addView(
                flash,
                android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                )
            )
            flash.animate().alpha(0f).setDuration(220L).withEndAction {
                (root as? android.view.ViewGroup)?.removeView(flash)
            }.start()
        }

        val jpegBytes = withTimeoutOrNull(3_000L) { jpegDeferred.await() }

        session.close()
        cameraDevice.close()
        imageReader.close()
        handlerThread.quitSafely()

        if (jpegBytes == null) {
            Timber.w("S0058: capture timeout")
            withContext(Dispatchers.Main) {
                Toast.makeText(activity, R.string.passthrough_capture_timeout, Toast.LENGTH_SHORT).show()
            }
            return
        }
        Timber.i("S0058: JPEG captured bytes=%d", jpegBytes.size)
        onJpegCaptured(jpegBytes, activity, resource, onFileSaved)
    }

    private suspend fun onJpegCaptured(
        bytes: ByteArray,
        activity: FragmentActivity,
        resource: MediaResource,
        onFileSaved: (String) -> Unit,
    ) {
        val fileName = "passthrough_${LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
        )}.jpg"
        try {
            if (resource.type == ResourceType.LOCAL && !VirtualPathUtils.isVirtualPath(resource.path)) {
                withContext(Dispatchers.IO) {
                    File(resource.path, fileName).writeBytes(bytes)
                }
            } else {
                withContext(Dispatchers.IO) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/FastMediaSorter")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val resolver = activity.contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: error("MediaStore insert returned null")
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("Could not open output stream for $uri")
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            }
            onFileSaved(fileName)
            withContext(Dispatchers.Main) {
                val msg = activity.getString(R.string.passthrough_capture_saved, resource.name)
                Snackbar.make(activity.window.decorView.rootView, msg, Snackbar.LENGTH_LONG).show()
            }
        } catch (e: Throwable) {
            Timber.e(e, "S0058: failed to save JPEG")
            withContext(Dispatchers.Main) {
                Toast.makeText(activity, R.string.passthrough_capture_error_save, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private class VrPermissionBridgeFragment : Fragment() {

    var onGranted: (() -> Unit)? = null
    var onDenied: ((showRationale: Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        requestPermissions(arrayOf(HEADSET_CAMERA_PERMISSION), PERM_REQUEST_CODE)
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?,
    ): android.view.View? = null

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERM_REQUEST_CODE) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            onGranted?.invoke()
        } else {
            val showRationale = shouldShowRequestPermissionRationale(HEADSET_CAMERA_PERMISSION)
            onDenied?.invoke(showRationale)
        }
        parentFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
    }
}
