package com.sza.fastmediasorter.broadcast

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S2551: the surface the owner's "allow" tap lands on, and the reason it is an activity at all.
 *
 * `research/05` establishes that a camera-typed foreground service cannot be created while the app is
 * invisible, and that no exemption covers a Data Layer message. Bringing a visible activity to the
 * front is what lifts that bar, so the grant is recorded from here rather than from a broadcast
 * receiver, which would leave the app in the same background state the request arrived in.
 *
 * Its own shape follows `ScreenCaptureConsentActivity`: transparent, out of recents, no history - the
 * owner already made the decision on the notification, so this must not add a second screen to it.
 *
 * Lives in the `standard` source set because only that flavor asks by notification; `noLegal` answers
 * the same question from a standing switch and never reaches here.
 */
@AndroidEntryPoint
class CameraSessionConsentActivity : AppCompatActivity() {

    @Inject
    lateinit var consentGate: CameraSessionConsentGate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requestId = intent?.getStringExtra(EXTRA_REQUEST_ID)
        if (requestId.isNullOrBlank()) {
            // Nothing to correlate a grant with, so the waiting request must expire on its own rather
            // than be answered for a session this intent cannot name.
            Timber.w("Camera session: an allow tap arrived with no request id")
            finish()
            return
        }
        lifecycleScope.launch {
            consentGate.grant(requestId)
            finish()
        }
    }

    companion object {
        const val EXTRA_REQUEST_ID = "camera_session_request_id"
    }
}
