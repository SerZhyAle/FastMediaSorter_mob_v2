package com.sza.fastmediasorter.screencapture

import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.sza.fastmediasorter.R

class ScreenCaptureConsentActivity : AppCompatActivity() {

    private val consentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenCaptureService.start(this, result.resultCode, data)
        } else {
            Toast.makeText(this, R.string.msg_operation_cancelled, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        if (projectionManager == null) {
            Toast.makeText(this, R.string.save_frame_error, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        consentLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
}
