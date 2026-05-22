package com.sza.fastmediasorter.core.xr.assets

import android.content.Context
import com.sza.fastmediasorter.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Bundled-asset provider for the VR diagnostic 360° fallback image.
 *
 * Reads the monoscopic equirectangular JPEG from `vr` flavor resources and exposes its raw bytes
 * + layout metadata to [com.sza.fastmediasorter.core.xr.runtime.NativeDiagnosticXrRuntime]. The
 * image is rendered on a full sphere so the user can rotate the head and see the scene from any
 * direction.
 *
 * The provider is VR-flavor only — phone-only flavors never inject this class because the
 * `XrEntryGateway` no-op path short-circuits before the runtime is consulted.
 *
 * Asset facts (kept in sync with `THIRD_PARTY_LICENSES.md`):
 * - Source: Poly Haven `lakeside` HDRI (8K tonemapped JPG).
 * - Format: JPEG, 8192 x 4096 (equirectangular, 2:1).
 * - Layout: monoscopic full sphere (360° x 180°).
 * - License: CC0 / public domain (no attribution required).
 * - File size: ~4.5 MB.
 *
 * Caller must be aware that decoding the full 8192x4096 frame as ARGB_8888 needs ~128 MB of heap.
 * The callers in [com.sza.fastmediasorter.ui.xr.DiagnosticXrActivity] therefore use a sample-size
 * fallback if the first ARGB_8888 decode fails with [OutOfMemoryError].
 */
@Singleton
class DiagnosticXrAssetProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Layout metadata for the bundled diagnostic image. Native side passes this to the
     * composition-layer setup (equirect2 if supported; otherwise sphere mesh with eye-shifted
     * UV).
     */
    enum class StereoLayout { TopBottom, SideBySide, Mono }

    data class DiagnosticAsset(
        val bytes: ByteArray,
        val widthPx: Int,
        val heightPx: Int,
        val layout: StereoLayout
    )

    /**
     * Loads the bundled diagnostic asset into memory. The image is small enough on disk
     * (~4.5 MB) to be fully decoded as raw JPEG bytes; downstream decode/upload is handled by
     * the activity to keep this provider stateless.
     *
     * @return [DiagnosticAsset] with raw JPEG bytes + dimensions, or null on IO failure.
     */
    fun load(): DiagnosticAsset? {
        return try {
            val bytes = context.resources.openRawResource(R.drawable.vr_diagnostic_360_mono).use { it.readBytes() }
            DiagnosticAsset(
                bytes = bytes,
                widthPx = NATIVE_WIDTH_PX,
                heightPx = NATIVE_HEIGHT_PX,
                layout = StereoLayout.Mono
            )
        } catch (t: IOException) {
            Timber.e(t, "DiagnosticXrAssetProvider: failed to read bundled diagnostic asset")
            null
        } catch (t: Resources_NotFoundException) {
            Timber.e(t, "DiagnosticXrAssetProvider: bundled diagnostic asset resource missing")
            null
        }
    }

    private companion object {
        const val NATIVE_WIDTH_PX = 8192
        const val NATIVE_HEIGHT_PX = 4096
    }
}

private typealias Resources_NotFoundException = android.content.res.Resources.NotFoundException
