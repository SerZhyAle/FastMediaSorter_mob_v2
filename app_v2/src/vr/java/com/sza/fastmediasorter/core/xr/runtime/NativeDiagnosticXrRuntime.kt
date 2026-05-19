package com.sza.fastmediasorter.core.xr.runtime

import android.content.Context
import com.sza.fastmediasorter.core.xr.assets.DiagnosticXrAssetProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * logs a single informational line and flips [nativeAvailable] to `false`; every public method
 * then short-circuits to a clean "loader unavailable" outcome without invoking the JNI entry
 * points (so no `UnsatisfiedLinkError` storm appears in logcat when the gateway later asks
 * the runtime to start a session).
 *
 * Failures are logged with [Timber]; the caller (`XrEntryGatewayImpl`) decides whether to
 * surface them to the UI.
 */
@Singleton
class NativeDiagnosticXrRuntime @Inject constructor(
    private val assetProvider: DiagnosticXrAssetProvider
) : DiagnosticXrRuntime {

    /**
     * `true` once `System.loadLibrary("fms_diagnostic_xr")` has resolved. Stays `false` on any
     * device where the native slice is absent (deliberate non-arm64 packaging) so the rest of
     * the runtime can short-circuit instead of repeatedly triggering `UnsatisfiedLinkError`.
     */
    private val nativeAvailable: Boolean

    init {
        nativeAvailable = try {
            System.loadLibrary(NATIVE_LIBRARY_NAME)
            Timber.d("NativeDiagnosticXrRuntime: loaded $NATIVE_LIBRARY_NAME")
            true
        } catch (_: UnsatisfiedLinkError) {
            // Expected on non-arm64 devices — the native slice is packaged for arm64-v8a only.
            // Single info line, no stack trace: developer does not need to act on this.
            Timber.i("NativeDiagnosticXrRuntime: $NATIVE_LIBRARY_NAME unavailable for this device ABI, XR runtime disabled")
            false
        } catch (t: SecurityException) {
            // Anomaly worth investigating — the library is present but a security policy blocked
            // it. Keep at ERROR with stack trace.
            Timber.e(t, "NativeDiagnosticXrRuntime: security policy blocked $NATIVE_LIBRARY_NAME load")
            false
        }
    }

    override suspend fun probeExtensions(): DiagnosticXrNativeResult {
        if (!nativeAvailable) return DiagnosticXrNativeResult.LoaderUnavailable
        return withContext(Dispatchers.Default) {
            val ordinal = runCatching { nativeProbeExtensions() }.getOrElse {
                Timber.e(it, "NativeDiagnosticXrRuntime.probeExtensions: native call threw")
                DiagnosticXrNativeResult.UnexpectedRuntimeError.nativeOrdinal
            }
            DiagnosticXrNativeResult.fromOrdinal(ordinal)
        }
    }

    override fun hasEquirect2Layer(): Boolean {
        if (!nativeAvailable) return false
        return runCatching { nativeHasEquirect2() }.getOrElse {
            Timber.w(it, "NativeDiagnosticXrRuntime.hasEquirect2Layer: native call threw")
            false
        }
    }

    override suspend fun startSession(context: Context): DiagnosticXrNativeResult {
        if (!nativeAvailable) return DiagnosticXrNativeResult.LoaderUnavailable
        return withContext(Dispatchers.Default) {
            val activityOrAppContext: Any = context
            val ordinal = runCatching { nativeStartSession(activityOrAppContext) }.getOrElse {
                Timber.e(it, "NativeDiagnosticXrRuntime.startSession: native call threw")
                DiagnosticXrNativeResult.UnexpectedRuntimeError.nativeOrdinal
            }
            DiagnosticXrNativeResult.fromOrdinal(ordinal)
        }
    }

    override suspend fun presentStaticImage(
        imageBytes: ByteArray,
        width: Int,
        height: Int
    ): DiagnosticXrNativeResult {
        if (!nativeAvailable) return DiagnosticXrNativeResult.LoaderUnavailable
        return withContext(Dispatchers.Default) {
            val ordinal = runCatching { nativePresentStaticImage(imageBytes, width, height) }.getOrElse {
                Timber.e(it, "NativeDiagnosticXrRuntime.presentStaticImage: native call threw")
                DiagnosticXrNativeResult.UnexpectedRuntimeError.nativeOrdinal
            }
            DiagnosticXrNativeResult.fromOrdinal(ordinal)
        }
    }

    /**
     * Convenience entry used by [com.sza.fastmediasorter.core.xr.XrEntryGatewayImpl] to push
     * the bundled diagnostic asset without bothering the caller with raw byte plumbing.
     * Returns [DiagnosticXrNativeResult.LoaderUnavailable] if the bundled resource cannot be
     * read (treated as "runtime not viable" at the gateway layer).
     */
    override suspend fun presentBundledDiagnosticImage(): DiagnosticXrNativeResult {
        if (!nativeAvailable) return DiagnosticXrNativeResult.LoaderUnavailable
        return withContext(Dispatchers.Default) {
            val asset = assetProvider.load()
            if (asset == null) {
                Timber.w("NativeDiagnosticXrRuntime.presentBundledDiagnosticImage: asset load failed")
                return@withContext DiagnosticXrNativeResult.LoaderUnavailable
            }
            presentStaticImage(asset.bytes, asset.widthPx, asset.heightPx)
        }
    }

    override fun requestExit() {
        if (!nativeAvailable) return
        runCatching { nativeRequestExit() }.onFailure {
            Timber.w(it, "NativeDiagnosticXrRuntime.requestExit: native call threw")
        }
    }

    override fun isRunning(): Boolean {
        if (!nativeAvailable) return false
        return runCatching { nativeIsRunning() }.getOrElse {
            Timber.w(it, "NativeDiagnosticXrRuntime.isRunning: native call threw")
            false
        }
    }

    /**
     * S0249 Phase 05: polls the native OpenXR action set for "any controller input" events
     * and reports whether the session should exit. Returns `true` once an exit request has
     * been latched (either via this poll or via [requestExit]). Caller is expected to drive
     * this on the render thread between [xrSyncActions] calls.
     */
    fun pollExitTriggered(): Boolean {
        if (!nativeAvailable) return false
        return runCatching { nativePollExitTriggered() }.getOrElse {
            Timber.w(it, "NativeDiagnosticXrRuntime.pollExitTriggered: native call threw")
            false
        }
    }

    private external fun nativeProbeExtensions(): Int
    private external fun nativeHasEquirect2(): Boolean
    private external fun nativeStartSession(contextOrActivity: Any): Int
    private external fun nativePresentStaticImage(imageBytes: ByteArray, width: Int, height: Int): Int
    private external fun nativeRequestExit()
    private external fun nativeIsRunning(): Boolean
    private external fun nativePollExitTriggered(): Boolean

    private companion object {
        const val NATIVE_LIBRARY_NAME = "fms_diagnostic_xr"
    }
}
