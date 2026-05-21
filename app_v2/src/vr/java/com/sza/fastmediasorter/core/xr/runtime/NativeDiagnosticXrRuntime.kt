package com.sza.fastmediasorter.core.xr.runtime

import android.app.Activity
import android.view.Surface
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
        runCatching { nativeShutdown() }.onFailure {
            Timber.w(it, "shutdown: native call threw")
        }
    }

    // JNI surface. Method names must match the symbols emitted by `diagnostic_xr_runtime.cpp`.
    private external fun nativeInitSession(activity: Activity): Int
    private external fun nativeAttachSurface(surface: Surface): Int
    private external fun nativeStartSession(): Int
    private external fun nativeUploadTexture(rgba: ByteArray, width: Int, height: Int): Int
    private external fun nativeRunFrameLoop(): Int
    private external fun nativeRequestExit()
    private external fun nativeShutdown()
    private external fun nativeIsRunning(): Boolean

    private companion object {
        const val NATIVE_LIBRARY_NAME = "fms_diagnostic_xr"
    }
}
