package com.sza.fastmediasorter.screencapture

import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S0774: transparent consent gate for screen video recording. Mirrors [ScreenCaptureConsentActivity]
 * but uses the continuous-recording disclosure (a distinct, more explicit notice) and starts
 * [ScreenVideoRecordingService] on consent. RECORD_AUDIO and POST_NOTIFICATIONS are requested by the
 * caller (the in-app manager) before this Activity is launched - here we only handle the disclosure
 * plus the platform MediaProjection consent.
 */
@AndroidEntryPoint
class ScreenVideoRecordingConsentActivity : AppCompatActivity() {

    @Inject
    lateinit var disclosureManager: ScreenRecordingDisclosureManager

    private var consentLaunched = false

    private val consentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenVideoRecordingService.start(this, result.resultCode, data)
        } else {
            Toast.makeText(this, R.string.msg_operation_cancelled, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consentLaunched = savedInstanceState?.getBoolean(STATE_CONSENT_LAUNCHED) ?: false
        if (consentLaunched) return
        lifecycleScope.launch { maybeStartConsentFlow() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_CONSENT_LAUNCHED, consentLaunched)
    }

    private suspend fun maybeStartConsentFlow() {
        if (disclosureManager.isAccepted()) {
            launchSystemConsent()
        } else {
            showDisclosureDialog()
        }
    }

    private fun showDisclosureDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.screen_recording_disclosure_title)
            .setMessage(R.string.screen_recording_disclosure_message)
            .setPositiveButton(R.string.screen_recording_disclosure_start) { _, _ ->
                lifecycleScope.launch { acceptDisclosureAndLaunchConsent() }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private suspend fun acceptDisclosureAndLaunchConsent() {
        disclosureManager.accept()
        launchSystemConsent()
    }

    private fun launchSystemConsent() {
        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        if (projectionManager == null) {
            Toast.makeText(this, R.string.save_frame_error, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        consentLaunched = true
        consentLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    companion object {
        private const val STATE_CONSENT_LAUNCHED = "screen_recording_consent_launched"
    }
}
