package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.hardware.camera2.CameraMetadata
import android.util.Range
import android.util.Size
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.systeminfo.SystemInfoSection
import com.sza.fastmediasorter.data.capture.CameraHardwareDataSource
import com.sza.fastmediasorter.domain.model.CameraHardwareEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject

/**
 * Renders the device's camera tree as one System info section.
 *
 * Field labels are localized; the values stay in raw technical form on purpose - the section exists
 * to be pasted back verbatim when a device does not offer the lenses it physically has, so a
 * translated "back" would only make two reports harder to compare.
 */
class GatherCameraDiagnosticsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cameraHardware: CameraHardwareDataSource,
) {

    operator fun invoke(): SystemInfoSection = SystemInfoSection(
        title = context.getString(R.string.sysinfo_section_cameras),
        fields = cameraHardware.read().entries.flatMap(::fieldsOf),
    )

    private fun fieldsOf(entry: CameraHardwareEntry): List<Pair<String, String>> {
        val id = entry.logicalParentId?.let { "$it/${entry.cameraId}" } ?: entry.cameraId
        val fields = mutableListOf<Pair<String, String>>()
        fields += label(id, R.string.sysinfo_field_camera_id) to entry.cameraId
        fields += label(id, R.string.sysinfo_field_camera_facing) to facingText(entry.lensFacing)
        entry.logicalParentId?.let {
            fields += label(id, R.string.sysinfo_field_camera_physical_of) to it
        }
        fields += label(id, R.string.sysinfo_field_camera_focal) to focalText(entry.focalLengthsMm)
        fields += label(id, R.string.sysinfo_field_camera_zoom_range) to zoomText(entry.zoomRange)
        fields += label(id, R.string.sysinfo_field_camera_focus_distance) to
            focusText(entry.minFocusDistanceDiopters)
        fields += label(id, R.string.sysinfo_field_camera_max_photo) to sizeText(entry.maxJpegSize)
        entry.maxHighResolutionJpegSize?.let {
            fields += label(id, R.string.sysinfo_field_camera_max_photo_high_res) to sizeText(it)
        }
        return fields
    }

    private fun label(id: String, @StringRes res: Int): String = "[$id] ${context.getString(res)}"

    private fun facingText(facing: Int?): String = when (facing) {
        CameraMetadata.LENS_FACING_BACK -> "back"
        CameraMetadata.LENS_FACING_FRONT -> "front"
        CameraMetadata.LENS_FACING_EXTERNAL -> "external"
        else -> UNKNOWN
    }

    private fun focalText(focalLengths: List<Float>): String =
        if (focalLengths.isEmpty()) {
            UNKNOWN
        } else {
            focalLengths.joinToString(", ") { String.format(Locale.US, "%.2f mm", it) }
        }

    private fun zoomText(range: Range<Float>?): String =
        range?.let { String.format(Locale.US, "%.2f - %.2f", it.lower, it.upper) } ?: UNKNOWN

    private fun focusText(diopters: Float?): String = when {
        diopters == null -> UNKNOWN
        diopters <= 0f -> "fixed focus"
        else -> String.format(Locale.US, "%.1f dpt (~%.0f cm)", diopters, CM_PER_METRE / diopters)
    }

    private fun sizeText(size: Size?): String = size?.let {
        val megapixels = it.width.toLong() * it.height.toLong() / MEGAPIXEL
        String.format(Locale.US, "%dx%d (%.1f MP)", it.width, it.height, megapixels)
    } ?: UNKNOWN

    private companion object {
        const val UNKNOWN = "unknown"
        const val CM_PER_METRE = 100f
        const val MEGAPIXEL = 1_000_000f
    }
}
