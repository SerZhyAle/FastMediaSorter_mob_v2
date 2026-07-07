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
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ScreenCaptureConsentActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private var gestureDirection: String? = null
    private var consentLaunched = false

    private val consentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenCaptureService.start(this, result.resultCode, data, gestureDirection)
        } else {
            Toast.makeText(this, R.string.msg_operation_cancelled, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gestureDirection = intent?.getStringExtra(EXTRA_GESTURE_DIRECTION)
        consentLaunched = savedInstanceState?.getBoolean(STATE_CONSENT_LAUNCHED) ?: false
        if (consentLaunched) return
        lifecycleScope.launch {
            maybeStartConsentFlow()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_CONSENT_LAUNCHED, consentLaunched)
    }

    private suspend fun maybeStartConsentFlow() {
        val settings = settingsRepository.getSettings().first()
        if (settings.screenCaptureDisclosureAccepted) {
            launchSystemConsent()
        } else {
            showDisclosureDialog()
        }
    }

    private fun showDisclosureDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.screen_capture_disclosure_title)
            .setMessage(R.string.screen_capture_disclosure_message)
            .setPositiveButton(R.string.screen_capture_disclosure_accept) { _, _ ->
                lifecycleScope.launch {
                    acceptDisclosureAndLaunchConsent()
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private suspend fun acceptDisclosureAndLaunchConsent() {
        val current = settingsRepository.getSettings().first()
        settingsRepository.updateSettings(current.copy(screenCaptureDisclosureAccepted = true))
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
        const val EXTRA_GESTURE_DIRECTION = "gesture_direction"
        private const val STATE_CONSENT_LAUNCHED = "screen_capture_consent_launched"
    }
}
