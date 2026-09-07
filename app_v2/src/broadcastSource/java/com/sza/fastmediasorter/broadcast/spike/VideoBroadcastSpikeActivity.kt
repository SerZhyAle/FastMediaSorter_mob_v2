package com.sza.fastmediasorter.broadcast.spike

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.rtspserver.RtspServerCamera2
import com.pedro.rtspserver.util.RtspServerStreamClient
import timber.log.Timber

/**
 * S2662 phase 01 - throwaway spike surface. It exists to answer one question S2068 section 7 refuses
 * to take on documentation: does an RTSP server hosted on this phone serve a stream that this app's
 * own RtspMediaSource plays over forced interleaved TCP, and for how long.
 *
 * Not reachable from the interface by design. Launch it with
 * `adb shell am start -n <applicationId>/com.sza.fastmediasorter.broadcast.spike.VideoBroadcastSpikeActivity`
 * and open the printed URL on a second device as a manual stream source.
 *
 * Phase 05 deletes this file. Nothing in the shipped broadcast path may reference it.
 */
class VideoBroadcastSpikeActivity : AppCompatActivity(), ConnectChecker {

    private var server: RtspServerCamera2? = null
    private lateinit var statusView: TextView
    private lateinit var toggleButton: Button

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) {
            startServing()
        } else {
            report("Permissions denied - camera and microphone are both required")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
        toggleButton.setOnClickListener { onToggleClicked() }
        report("Idle. Tap start, then open the printed URL on the second device.")
    }

    override fun onDestroy() {
        stopServing()
        super.onDestroy()
    }

    private fun buildLayout(): ViewGroup {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(LAYOUT_PADDING_PX, LAYOUT_PADDING_PX, LAYOUT_PADDING_PX, LAYOUT_PADDING_PX)
        }
        toggleButton = Button(this).apply { text = LABEL_START }
        statusView = TextView(this)
        root.addView(toggleButton)
        root.addView(statusView)
        return root
    }

    private fun onToggleClicked() {
        val running = server?.isStreaming == true
        if (running) {
            stopServing()
        } else {
            requestPermissionsThenStart()
        }
    }

    private fun requestPermissionsThenStart() {
        val required = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startServing()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startServing() {
        Timber.d("S2662: video broadcast spike is starting the RTSP server on port %d", RTSP_PORT)
        // The Context constructor is the headless one - no OpenGlView, so no preview surface is
        // created. That is the shape the owner's 2026-09-06 no-preview ruling needs, and proving it
        // works is part of what this spike measures.
        val camera = RtspServerCamera2(this, this, RTSP_PORT)
        val prepared = camera.prepareVideo() && camera.prepareAudio()
        if (!prepared) {
            report("Encoder refused the default video/audio configuration on this device")
            return
        }
        camera.startStream()
        server = camera
        toggleButton.text = LABEL_STOP
        // The library ships Kotlin metadata newer than this project's compiler reads, so its classes
        // resolve in the Java view: properties are reachable only through their getters, and the
        // RTSP-only endpoint lives on the concrete client rather than the declared base type.
        val endpoint = (camera.streamClient as RtspServerStreamClient).getEndPointConnection()
        report("Serving at $endpoint")
    }

    private fun stopServing() {
        val camera = server ?: return
        if (camera.isStreaming) {
            camera.stopStream()
        }
        server = null
        toggleButton.text = LABEL_START
        report("Stopped")
    }

    private fun report(message: String) {
        statusView.text = message
        Timber.i("VideoBroadcastSpike: %s", message)
    }

    override fun onConnectionStarted(url: String) = report("Client connecting: $url")

    override fun onConnectionSuccess() = report("Client connected")

    override fun onConnectionFailed(reason: String) = report("Connection failed: $reason")

    override fun onDisconnect() = report("Client disconnected")

    override fun onAuthError() = report("Auth error")

    override fun onAuthSuccess() = report("Auth success")

    private companion object {
        // 8554 is the conventional alternate RTSP port and does not collide with the audio pillar's
        // 8768-8770 range, so both broadcast modes can be probed on one device without a port clash.
        const val RTSP_PORT = 8554
        const val LAYOUT_PADDING_PX = 32
        const val LABEL_START = "Start RTSP server"
        const val LABEL_STOP = "Stop RTSP server"
    }
}
