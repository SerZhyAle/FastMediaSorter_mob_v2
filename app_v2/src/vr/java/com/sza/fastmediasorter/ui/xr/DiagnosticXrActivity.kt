package com.sza.fastmediasorter.ui.xr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter.core.xr.assets.DiagnosticXrAssetProvider
import com.sza.fastmediasorter.core.xr.input.DiagnosticXrInputExitHandler
import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrNativeResult
import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrRuntime
import dagger.hilt.android.AndroidEntryPoint
import java.nio.ByteBuffer
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S0249 Phase 02 step 02.6: dedicated OpenXR session host Activity.
 *
 * Launched by `XrEntryGatewayImpl.enterDiagnosticImage` via an Intent (so HorizonOS picks up
 * the `com.oculus.intent.category.VR` intent-filter and starts us in headset mode). Lifecycle:
 *
 * - `onCreate`: load the bundled diagnostic asset, decode it on the main thread to an RGBA
 *   `ByteArray` (the texture upload happens on the render thread later). Build a SurfaceView
 *   and add the holder callback.
 * - SurfaceView callback `surfaceCreated`: spin up [DiagnosticXrRenderThread] passing the
 *   Surface, runtime, and decoded RGBA bytes. The render thread blocks inside the OpenXR
 *   frame loop until any input triggers exit.
 * - Input handling: every `onKeyDown` / `onTouchEvent` is forwarded to
 *   [DiagnosticXrInputExitHandler]; the same handler is observed and any emitted `ExitReason`
 *   triggers `renderThread.requestExit()`. The grace-period gate prevents stray events from
 *   the launching gesture immediately dismissing the session.
 * - When the render thread finishes (loop returned + shutdown done) it calls
 *   [onRenderThreadExit] which calls `finish()` on the UI thread.
 *
 * The Activity intentionally uses a plain [SurfaceView] (not GLSurfaceView): OpenXR owns the
 * frame timing (`xrWaitFrame` -> render -> `xrEndFrame`) and a GLSurfaceView's renderer thread
 * would compete with ours.
 */
@AndroidEntryPoint
class DiagnosticXrActivity : ComponentActivity(), SurfaceHolder.Callback {

    @Inject lateinit var runtime: DiagnosticXrRuntime
    @Inject lateinit var assetProvider: DiagnosticXrAssetProvider
    @Inject lateinit var exitHandler: DiagnosticXrInputExitHandler

    private var renderThread: DiagnosticXrRenderThread? = null
    private lateinit var surfaceView: SurfaceView

    // Decoded asset, owned by the Activity until handed off to the render thread.
    private var textureBytes: ByteArray = ByteArray(0)
    private var textureWidth: Int = 0
    private var textureHeight: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("S0249: DiagnosticXrActivity.onCreate - OpenXR session host launching")
        // Keep the screen on for the duration of the immersive session. Required on Quest 3
        // because the OpenXR runtime drives display refresh but the system may still trigger
        // sleep if the Activity does not request it.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (!runtime.isNativeAvailable) {
            Timber.w("DiagnosticXrActivity: native runtime unavailable, finishing")
            finish(); return
        }
        if (!decodeBundledAsset()) {
            Timber.w("DiagnosticXrActivity: bundled asset decode failed, finishing")
            finish(); return
        }

        surfaceView = SurfaceView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            holder.addCallback(this@DiagnosticXrActivity)
        }
        setContentView(surfaceView)
        surfaceView.requestFocus()

        // Safety net for the 2D fallback path: if the OpenXR session never reaches the
        // immersive composition (driver mismatch, swapchain failure, etc.) and Android keeps
        // the Activity in flat-screen mode, the system back button must still drop the user
        // out. In a real immersive session HorizonOS routes the system back to itself and this
        // callback is never invoked - that is fine.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Timber.d("DiagnosticXrActivity: onBackPressed -> requesting exit")
                renderThread?.requestExit() ?: finish()
            }
        })

        // Observe the input handler exit flow. The Activity-side input methods feed it; the
        // observer forwards exit requests to the render thread.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                exitHandler.exitRequested.collect { reason ->
                    Timber.d("exit signalled by $reason")
                    renderThread?.requestExit()
                }
            }
        }
    }

    private fun decodeBundledAsset(): Boolean {
        val asset = assetProvider.load() ?: return false
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = false
        }
        val bitmap = BitmapFactory.decodeByteArray(asset.bytes, 0, asset.bytes.size, options) ?: return false
        try {
            val w = bitmap.width
            val h = bitmap.height
            val buf = ByteBuffer.allocateDirect(w * h * 4)
            bitmap.copyPixelsToBuffer(buf)
            buf.rewind()
            val bytes = ByteArray(buf.remaining())
            buf.get(bytes)
            // Bitmap.copyPixelsToBuffer for ARGB_8888 lays out R, G, B, A — matches GL_RGBA.
            textureBytes = bytes
            textureWidth = w
            textureHeight = h
            Timber.d("decoded asset to RGBA: ${w}x${h}, bytes=${bytes.size}")
            return true
        } finally {
            bitmap.recycle()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (renderThread != null) return
        Timber.d("surfaceCreated; starting render thread")
        renderThread = DiagnosticXrRenderThread(
            activity = this,
            surface = holder.surface,
            runtime = runtime,
            textureBytes = textureBytes,
            textureWidth = textureWidth,
            textureHeight = textureHeight,
            onExitDelivered = ::onRenderThreadExit,
            onStartFailed = ::onRenderThreadStartFailed,
        ).also {
            it.start()
            // Mark the first-frame timestamp now — the grace period for input dismissal starts
            // from session bring-up, not from the actual first present (which we can't observe
            // cheaply from Kotlin). For diagnostic purposes the 400 ms gate is more than enough
            // either way.
            exitHandler.markFirstFramePresented(SystemClock.elapsedRealtime())
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // No-op: the OpenXR swapchains are fixed per-eye dimensions and the SurfaceView is just
        // a window-shell so HorizonOS does not collapse the Activity to a 2D panel.
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Timber.d("surfaceDestroyed; requesting exit")
        renderThread?.requestExit()
    }

    override fun onPause() {
        super.onPause()
        // HorizonOS may pause the Activity if the user takes off the headset or invokes the
        // system UI. Bail out cleanly so we don't leave a half-driven session in the runtime.
        renderThread?.requestExit()
    }

    override fun onDestroy() {
        renderThread?.requestExit()
        renderThread?.join(SHUTDOWN_TIMEOUT_MS)
        renderThread = null
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (exitHandler.onKeyEvent(event, SystemClock.elapsedRealtime())) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (exitHandler.onMotionEvent(event, SystemClock.elapsedRealtime())) return true
        return super.onTouchEvent(event)
    }

    private fun onRenderThreadExit() {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                Timber.d("render thread done; finishing")
                finish()
            }
        }
    }

    private fun onRenderThreadStartFailed(result: DiagnosticXrNativeResult) {
        Timber.w("render thread start failed: $result")
        runOnUiThread { if (!isFinishing && !isDestroyed) finish() }
    }

    private companion object {
        const val SHUTDOWN_TIMEOUT_MS = 3_000L
    }
}
