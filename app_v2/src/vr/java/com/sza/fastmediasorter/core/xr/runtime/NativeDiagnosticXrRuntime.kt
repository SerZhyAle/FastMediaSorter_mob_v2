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
 * Failures are logged with [Timber] at the call site; the caller (`XrEntryGatewayImpl`)
 * decides whether to surface the failure to the UI.
 */
@Singleton
class NativeDiagnosticXrRuntime @Inject constructor(
    private val assetProvider: DiagnosticXrAssetProvider
) : DiagnosticXrRuntime {

    init {
        try {
            System.loadLibrary(NATIVE_LIBRARY_NAME)
            Timber.d("NativeDiagnosticXrRuntime: loaded $NATIVE_LIBRARY_NAME")
        } catch (t: UnsatisfiedLinkError) {
            Timber.e(t, "NativeDiagnosticXrRuntime: native library $NATIVE_LIBRARY_NAME missing")
        } catch (t: SecurityException) {
            Timber.e(t, "NativeDiagnosticXrRuntime: security policy blocked $NATIVE_LIBRARY_NAME load")
        }
    }

    override suspend fun probeExtensions(): DiagnosticXrNativeResult = withContext(Dispatchers.Default) {
        val ordinal = runCatching { nativeProbeExtensions() }.getOrElse {
            Timber.e(it, "NativeDiagnosticXrRuntime.probeExtensions: native call threw")
            DiagnosticXrNativeResult.UnexpectedRuntimeError.nativeOrdinal
        }
        DiagnosticXrNativeResult.fromOrdinal(ordinal)
    }

    override fun hasEquirect2Layer(): Boolean =
        runCatching { nativeHasEquirect2() }.getOrElse {
            Timber.w(it, "NativeDiagnosticXrRuntime.hasEquirect2Layer: native call threw")
            false
        }

    override suspend fun startSession(context: Context): DiagnosticXrNativeResult = withContext(Dispatchers.Default) {
        val activityOrAppContext: Any = context
        val ordinal = runCatching { nativeStartSession(activityOrAppContext) }.getOrElse {
            Timber.e(it, "NativeDiagnosticXrRuntime.startSession: native call threw")
            DiagnosticXrNativeResult.UnexpectedRuntimeError.nativeOrdinal
        }
        DiagnosticXrNativeResult.fromOrdinal(ordinal)
    }

    override suspend fun presentStaticImage(
        imageBytes: ByteArray,
        width: Int,
        height: Int
    ): DiagnosticXrNativeResult = withContext(Dispatchers.Default) {
        val ordinal = runCatching { nativePresentStaticImage(imageBytes, width, height) }.getOrElse {
            Timber.e(it, "NativeDiagnosticXrRuntime.presentStaticImage: native call threw")
            DiagnosticXrNativeResult.UnexpectedRuntimeError.nativeOrdinal
        }
        DiagnosticXrNativeResult.fromOrdinal(ordinal)
    }

    /**
     * Convenience entry used by [com.sza.fastmediasorter.core.xr.XrEntryGatewayImpl] to push
     * the bundled diagnostic asset without bothering the caller with raw byte plumbing.
     * Returns [DiagnosticXrNativeResult.LoaderUnavailable] if the bundled resource cannot be
     * read (treated as "runtime not viable" at the gateway layer).
     */
    override suspend fun presentBundledDiagnosticImage(): DiagnosticXrNativeResult = withContext(Dispatchers.Default) {
        val asset = assetProvider.load()
        if (asset == null) {
            Timber.w("NativeDiagnosticXrRuntime.presentBundledDiagnosticImage: asset load failed")
            return@withContext DiagnosticXrNativeResult.LoaderUnavailable
        }
        presentStaticImage(asset.bytes, asset.widthPx, asset.heightPx)
    }

    override fun requestExit() {
        runCatching { nativeRequestExit() }.onFailure {
            Timber.w(it, "NativeDiagnosticXrRuntime.requestExit: native call threw")
        }
    }

    override fun isRunning(): Boolean =
        runCatching { nativeIsRunning() }.getOrElse {
            Timber.w(it, "NativeDiagnosticXrRuntime.isRunning: native call threw")
            false
        }

    private external fun nativeProbeExtensions(): Int
    private external fun nativeHasEquirect2(): Boolean
    private external fun nativeStartSession(contextOrActivity: Any): Int
    private external fun nativePresentStaticImage(imageBytes: ByteArray, width: Int, height: Int): Int
    private external fun nativeRequestExit()
    private external fun nativeIsRunning(): Boolean

    private companion object {
        const val NATIVE_LIBRARY_NAME = "fms_diagnostic_xr"
    }
}
