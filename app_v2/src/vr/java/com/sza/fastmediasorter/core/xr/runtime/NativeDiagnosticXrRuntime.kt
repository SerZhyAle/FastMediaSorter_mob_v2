package com.sza.fastmediasorter.core.xr.runtime

import android.app.Activity
import android.view.Surface
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Native-backed [DiagnosticXrRuntime].
 *
 * Loads `libfms_diagnostic_xr.so` (built by `app_v2/src/vr/cpp/CMakeLists.txt`) and forwards
 * every operation to JNI methods declared in `diagnostic_xr_runtime.cpp`. The native side is
 * single-instance — concurrent diagnostic sessions are blocked by [DiagnosticXrNativeResult.AlreadyRunning].
 *
 * Library-availability semantics (S0156 ADR-8): the `noLegal` flavor ships only the arm64-v8a
 * slice of this `.so`. On x86_64 emulators or any non-arm64 device the library is intentionally
 * absent — that is *expected device-capability mismatch*, not a program error. Construction
 * logs a single informational line and flips [isNativeAvailable] to `false`; every public method
 * then short-circuits to a clean "loader unavailable" outcome without invoking the JNI entry
 * points (so no `UnsatisfiedLinkError` storm appears in logcat when the gateway later asks
 * the runtime to start a session).
 *
 * Threading contract: **all** session lifecycle calls except [requestExit] must come from the
 * SAME thread - the render thread owned by `DiagnosticXrActivity`. EGL contexts and GL objects
 * are thread-confined; OpenXR sessions inherit that confinement. The `suspend` modifier on the
 * setup methods is purely an API artefact (kept so callers can mix them with coroutine code) -
 * the methods execute synchronously on the caller's thread. Hopping to `Dispatchers.Default`
 * would create EGL on a coroutine-pool thread and then leave the render thread without a current
 * GL context, producing a featureless black composition layer (S0249 post-fix regression
 * 2026-05-21).
 */
@Singleton
class NativeDiagnosticXrRuntime @Inject constructor() : DiagnosticXrRuntime {

    override val isNativeAvailable: Boolean

    // S0290 Phase 09: idempotent guard for shutdown() so onPause + onDestroy + render-thread
    // finally-block can all call shutdown() without firing redundant nativeShutdown JNI calls.
    // Reset to false on initSession() so a fresh session re-arms the flag.
    private val _shutdownCalled = AtomicBoolean(false)

    init {
        isNativeAvailable = try {
            System.loadLibrary(NATIVE_LIBRARY_NAME)
            Timber.d("NativeDiagnosticXrRuntime: loaded $NATIVE_LIBRARY_NAME")
            true
        } catch (_: UnsatisfiedLinkError) {
            Timber.i("NativeDiagnosticXrRuntime: $NATIVE_LIBRARY_NAME unavailable for this device ABI, XR runtime disabled")
            false
        } catch (t: SecurityException) {
            Timber.e(t, "NativeDiagnosticXrRuntime: security policy blocked $NATIVE_LIBRARY_NAME load")
            false
        }
    }

    override fun isRunning(): Boolean {
        if (!isNativeAvailable) return false
        return runCatching { nativeIsRunning() }.getOrElse {
            Timber.w(it, "isRunning: native call threw"); false
        }
    }

    override suspend fun initSession(activity: Activity): DiagnosticXrNativeResult {
        if (!isNativeAvailable) return DiagnosticXrNativeResult.LoaderUnavailable
        Timber.d("S0291: diagnostic XR initSession entry")
        val initialized = runCatching { nativeIsInitialized() }.getOrElse {
            Timber.w(it, "initSession: native initialized-state probe threw")
            false
        }
        if (initialized) {
            val running = runCatching { nativeIsRunning() }.getOrElse {
                Timber.w(it, "initSession: native running-state probe threw")
                true
            }
            if (running) {
                Timber.w("NativeDiagnosticXrRuntime: session is still running; rejecting concurrent init")
                return DiagnosticXrNativeResult.AlreadyRunning
            }
            Timber.w("NativeDiagnosticXrRuntime: stale native state detected; forcing shutdown before init")
            runCatching { nativeShutdown() }.onFailure {
                Timber.w(it, "initSession: stale native shutdown threw")
            }
        }
        // Re-arm idempotent shutdown flag after stale cleanup, for the new session lifetime.
        _shutdownCalled.set(false)
        // Intentionally NO Dispatchers hop - see class KDoc threading contract.
        val ordinal = runCatching { nativeInitSession(activity) }.getOrElse {
            Timber.e(it, "initSession: native call threw")
            DiagnosticXrNativeResult.UnexpectedRuntimeError.nativeOrdinal
        }
        return DiagnosticXrNativeResult.fromOrdinal(ordinal)
    }

    override suspend fun attachSurface(surface: Surface): DiagnosticXrNativeResult {
        if (!isNativeAvailable) return DiagnosticXrNativeResult.LoaderUnavailable
        val ordinal = runCatching { nativeAttachSurface(surface) }.getOrElse {
            Timber.e(it, "attachSurface: native call threw")
            DiagnosticXrNativeResult.UnexpectedRuntimeError.nativeOrdinal
        }
        return DiagnosticXrNativeResult.fromOrdinal(ordinal)
    }

    override suspend fun startSession(): DiagnosticXrNativeResult {
        if (!isNativeAvailable) return DiagnosticXrNativeResult.LoaderUnavailable
        val ordinal = runCatching { nativeStartSession() }.getOrElse {
            Timber.e(it, "startSession: native call threw")
            DiagnosticXrNativeResult.UnexpectedRuntimeError.nativeOrdinal
        }
        return DiagnosticXrNativeResult.fromOrdinal(ordinal)
    }

    override suspend fun uploadTexture(rgba: ByteArray, width: Int, height: Int): DiagnosticXrNativeResult {
        if (!isNativeAvailable) return DiagnosticXrNativeResult.LoaderUnavailable
        val ordinal = runCatching { nativeUploadTexture(rgba, width, height) }.getOrElse {
            Timber.e(it, "uploadTexture: native call threw")
            DiagnosticXrNativeResult.UnexpectedRuntimeError.nativeOrdinal
        }
        return DiagnosticXrNativeResult.fromOrdinal(ordinal)
    }

    override fun runFrameLoop(): DiagnosticXrNativeResult {
        if (!isNativeAvailable) return DiagnosticXrNativeResult.LoaderUnavailable
        val ordinal = runCatching { nativeRunFrameLoop() }.getOrElse {
            Timber.e(it, "runFrameLoop: native call threw")
            DiagnosticXrNativeResult.UnexpectedRuntimeError.nativeOrdinal
        }
        return DiagnosticXrNativeResult.fromOrdinal(ordinal)
    }

    override fun requestExit() {
        if (!isNativeAvailable) return
        runCatching { nativeRequestExit() }.onFailure {
            Timber.w(it, "requestExit: native call threw")
        }
    }

    override fun shutdown() {
        if (!isNativeAvailable) return
        // S0290 Phase 09: idempotent - first caller wins, subsequent calls no-op.
        if (!_shutdownCalled.compareAndSet(false, true)) {
            Timber.d("NativeDiagnosticXrRuntime: shutdown already called for this session, ignoring")
            return
        }
        runCatching { nativeShutdown() }.onFailure {
            Timber.w(it, "shutdown: native call threw")
        }
    }

    override fun queueFrame(rgba: ByteArray, width: Int, height: Int) {
        if (!isNativeAvailable) return
        runCatching { nativeQueueFrame(rgba, width, height) }.onFailure {
            Timber.w(it, "queueFrame: native call threw")
        }
    }

    override fun getVideoSurface(): Surface? {
        if (!isNativeAvailable) return null
        return runCatching { nativeGetVideoSurface() }.getOrElse {
            Timber.w(it, "getVideoSurface: native call threw"); null
        }
    }

    override fun setVideoSurfaceEnabled(enabled: Boolean) {
        if (!isNativeAvailable) return
        runCatching { nativeSetVideoSurfaceEnabled(enabled) }.onFailure {
            Timber.w(it, "setVideoSurfaceEnabled: native call threw")
        }
    }

    override fun setRenderConfig(projection: Int, layout: Int) {
        if (!isNativeAvailable) return
        runCatching { nativeSetRenderConfig(projection, layout) }.onFailure {
            Timber.w(it, "setRenderConfig: native call threw")
        }
    }

    override fun setParallaxShift(value: Float) {
        if (!isNativeAvailable) return
        runCatching { nativeSetParallaxShift(value) }.onFailure {
            Timber.w(it, "setParallaxShift: native call threw")
        }
    }

    override fun queueHud(rgba: ByteArray, width: Int, height: Int) {
        if (!isNativeAvailable) return
        runCatching { nativeQueueHud(rgba, width, height) }.onFailure {
            Timber.w(it, "queueHud: native call threw")
        }
    }

    override fun onNativeRayInteraction(uvX: Float, uvY: Float, isHover: Boolean, isClick: Boolean) {
        // Validation placeholder for phase 03 JNI pathway streaming
    }

    override fun applyHaptic(hand: Int, durationSeconds: Float, frequency: Float, amplitude: Float) {
        if (!isNativeAvailable) return
        runCatching { nativeApplyHaptic(hand, durationSeconds, frequency, amplitude) }.onFailure {
            Timber.w(it, "applyHaptic: native call threw")
        }
    }

    override fun getCurrentFps(): Float {
        if (!isNativeAvailable) return 0.0f
        return runCatching { nativeGetCurrentFps() }.getOrElse {
            Timber.w(it, "getCurrentFps: native call threw"); 0.0f
        }
    }

    // JNI surface. Method names must match the symbols emitted by `diagnostic_xr_runtime.cpp`.
    private external fun nativeInitSession(activity: Activity): Int
    private external fun nativeAttachSurface(surface: Surface): Int
    private external fun nativeStartSession(): Int
    private external fun nativeUploadTexture(rgba: ByteArray, width: Int, height: Int): Int
    private external fun nativeQueueFrame(rgba: ByteArray, width: Int, height: Int)
    private external fun nativeGetVideoSurface(): Surface?
    private external fun nativeSetVideoSurfaceEnabled(enabled: Boolean)
    private external fun nativeSetRenderConfig(projection: Int, layout: Int)
    private external fun nativeSetParallaxShift(value: Float)
    private external fun nativeQueueHud(rgba: ByteArray, width: Int, height: Int)
    private external fun nativeApplyHaptic(hand: Int, durationSeconds: Float, frequency: Float, amplitude: Float)
    private external fun nativeGetCurrentFps(): Float
    private external fun nativeRunFrameLoop(): Int
    private external fun nativeRequestExit()
    private external fun nativeShutdown()
    private external fun nativeIsRunning(): Boolean
    private external fun nativeIsInitialized(): Boolean

    private companion object {
        const val NATIVE_LIBRARY_NAME = "fms_diagnostic_xr"
    }
}
