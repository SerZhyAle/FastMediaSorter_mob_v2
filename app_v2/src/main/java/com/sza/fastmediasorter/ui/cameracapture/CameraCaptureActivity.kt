package com.sza.fastmediasorter.ui.cameracapture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityCameraCaptureBinding
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureSessionManager
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.io.File

@AndroidEntryPoint
class CameraCaptureActivity : BaseActivity<ActivityCameraCaptureBinding>() {

    companion object {
        const val EXTRA_OUTPUT = "output"
        private const val EXTRA_OUTPUT_PATH = "output_path"

        fun createIntent(context: Context, outputUri: Uri): Intent =
            Intent(context, CameraCaptureActivity::class.java).putExtra(EXTRA_OUTPUT, outputUri)

        fun createIntent(context: Context, outputUri: Uri, outputPath: String): Intent =
            createIntent(context, outputUri).putExtra(EXTRA_OUTPUT_PATH, outputPath)
    }

    private lateinit var sessionManager: CameraCaptureSessionManager
    private var outputFile: File? = null
    private var captureInFlight = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            bindCamera()
        } else {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    override fun getViewBinding(): ActivityCameraCaptureBinding =
        ActivityCameraCaptureBinding.inflate(layoutInflater)

    override fun setupViews() {
        sessionManager = CameraCaptureSessionManager(this)
        binding.cameraTopBar.applySystemBarInsetPadding(applyBottom = false)
        binding.cameraActionBar.applySystemBarInsetPadding(applyTop = false)

        outputFile = resolveOutputFile()
        if (outputFile == null) {
            Toast.makeText(this, R.string.camera_capture_error_save_generic, Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        binding.btnCloseCamera.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
        binding.btnCapturePhoto.isEnabled = false
        binding.btnCapturePhoto.setOnClickListener { capturePhoto() }
        binding.btnCapturePhoto.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_UP) return@setOnKeyListener false
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                binding.btnCapturePhoto.performClick()
                return@setOnKeyListener true
            }
            false
        }

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            Toast.makeText(this, R.string.camera_capture_error_no_camera_app, Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        ensurePermissionAndBind()
    }

    override fun observeData() = Unit

    override fun getInitialFocusView() = binding.btnCapturePhoto

    override fun onDestroy() {
        if (::sessionManager.isInitialized) {
            sessionManager.unbind()
        }
        super.onDestroy()
    }

    private fun ensurePermissionAndBind() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            bindCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun bindCamera() {
        sessionManager.bind(
            previewView = binding.previewViewCamera,
            onReady = {
                binding.btnCapturePhoto.isEnabled = true
            },
            onError = {
                Toast.makeText(this, R.string.camera_capture_error_no_camera_app, Toast.LENGTH_LONG).show()
                setResult(RESULT_CANCELED)
                finish()
            },
        )
    }

    private fun capturePhoto() {
        val file = outputFile ?: return
        if (captureInFlight) return
        captureInFlight = true
        binding.btnCapturePhoto.isEnabled = false

        sessionManager.capture(
            previewView = binding.previewViewCamera,
            outputFile = file,
            onSaved = {
                setResult(RESULT_OK)
                finish()
            },
            onError = { error ->
                captureInFlight = false
                binding.btnCapturePhoto.isEnabled = true
                Timber.e(error, "CameraCaptureActivity: capture failed")
                Toast.makeText(this, R.string.camera_capture_error_save_generic, Toast.LENGTH_LONG).show()
            },
        )
    }

    private fun resolveOutputFile(): File? {
        // The public EXTRA_OUTPUT keeps the drop-in system-camera contract, while the private
        // path extra gives CameraX a real File target without reverse-parsing a FileProvider Uri.
        intent.getStringExtra(EXTRA_OUTPUT_PATH)?.let { path ->
            return File(path)
        }

        val outputUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_OUTPUT, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_OUTPUT) as? Uri
        }
        val outputPath = outputUri?.path ?: return null
        return outputPath.takeIf { it.isNotBlank() }?.let(::File)
    }
}
