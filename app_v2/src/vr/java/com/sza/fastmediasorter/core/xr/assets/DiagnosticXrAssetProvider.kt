package com.sza.fastmediasorter.core.xr.assets

import android.content.Context
import com.sza.fastmediasorter.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Bundled-asset provider for the S0249 Stage 1A diagnostic image.
 *
 * Reads the stereoscopic top-bottom equirectangular JPEG from `vr` flavor resources and
 * exposes its raw bytes + layout metadata to [com.sza.fastmediasorter.core.xr.runtime.NativeDiagnosticXrRuntime].
 *
 * The provider is VR-flavor only — phone-only flavors never inject this class because the
 * `XrEntryGateway` no-op path short-circuits before the runtime is consulted.
 *
 * Asset facts (kept in sync with `temp/S0249_asset_dimensions.txt`):
 * - Source: Navier8 `blender_test.jpg` (MIT license).
 * - Format: JPEG, 4096 x 4096 (stereo TB; 4096 x 2048 per eye).
 * - Layout: top half = left eye, bottom half = right eye (Blender convention).
 * - File size: ~651 KB.
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
     * Loads the bundled diagnostic asset into memory. The image is small enough (~651 KB) to
     * be fully decoded as raw JPEG bytes; the native runtime decodes via stb_image or
     * equivalent (Phase 03 leaves decoding to the native side to avoid Bitmap->byte[] copies
     * across the JNI boundary for large textures).
     *
     * @return [DiagnosticAsset] with raw JPEG bytes + dimensions, or null on IO failure.
     */
    fun load(): DiagnosticAsset? {
        return try {
            val bytes = context.resources.openRawResource(R.drawable.vr_diagnostic_stereo_tb).use { it.readBytes() }
            DiagnosticAsset(
                bytes = bytes,
                widthPx = NATIVE_WIDTH_PX,
                heightPx = NATIVE_HEIGHT_PX,
                layout = StereoLayout.TopBottom
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
        const val NATIVE_WIDTH_PX = 4096
        const val NATIVE_HEIGHT_PX = 4096
    }
}

private typealias Resources_NotFoundException = android.content.res.Resources.NotFoundException
