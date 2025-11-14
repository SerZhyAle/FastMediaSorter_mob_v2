package com.sza.fastmediasorter_v2.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import com.sza.fastmediasorter_v2.R
import com.sza.fastmediasorter_v2.databinding.DialogFileInfoBinding
import com.sza.fastmediasorter_v2.domain.model.MediaFile
import com.sza.fastmediasorter_v2.domain.model.MediaType
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dialog to display detailed file information including EXIF and video metadata
 */
class FileInfoDialog(
    context: Context,
    private val mediaFile: MediaFile
) : Dialog(context) {

    private lateinit var binding: DialogFileInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogFileInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set dialog width to 90% of screen width for better readability
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )

        setupDialog()
        displayFileInfo()
    }

    private fun setupDialog() {
        binding.btnClose.setOnClickListener {
            dismiss()
        }
    }

    private fun displayFileInfo() {
        // File Information
        binding.tvFileName.text = context.getString(R.string.file_name_label, mediaFile.name)
        binding.tvFileSize.text = context.getString(R.string.file_size_label, formatFileSize(mediaFile.size))
        binding.tvFileDate.text = context.getString(
            R.string.file_date_label,
            formatDate(mediaFile.createdDate)
        )
        binding.tvFileType.text = context.getString(R.string.file_type_label, mediaFile.type.name)
        binding.tvFilePath.text = context.getString(R.string.file_path_label, mediaFile.path)

        // EXIF Information (for images)
        if (mediaFile.type == MediaType.IMAGE && hasExifData()) {
            binding.sectionExif.visibility = View.VISIBLE
            displayExifInfo()
        } else {
            binding.sectionExif.visibility = View.GONE
        }

        // Video Metadata (for videos)
        if (mediaFile.type == MediaType.VIDEO && hasVideoMetadata()) {
            binding.sectionVideo.visibility = View.VISIBLE
            displayVideoInfo()
        } else {
            binding.sectionVideo.visibility = View.GONE
        }
    }

    private fun hasExifData(): Boolean {
        return mediaFile.exifDateTime != null ||
                mediaFile.exifOrientation != null ||
                (mediaFile.exifLatitude != null && mediaFile.exifLongitude != null)
    }

    private fun hasVideoMetadata(): Boolean {
        return mediaFile.duration != null ||
                mediaFile.width != null ||
                mediaFile.videoCodec != null ||
                mediaFile.videoBitrate != null
    }

    private fun displayExifInfo() {
        // EXIF DateTime
        if (mediaFile.exifDateTime != null) {
            binding.tvExifDateTime.text = context.getString(
                R.string.exif_datetime_label,
                formatDate(mediaFile.exifDateTime!!)
            )
            binding.tvExifDateTime.visibility = View.VISIBLE
        } else {
            binding.tvExifDateTime.visibility = View.GONE
        }

        // EXIF Orientation
        if (mediaFile.exifOrientation != null) {
            binding.tvExifOrientation.text = context.getString(
                R.string.exif_orientation_label,
                formatOrientation(mediaFile.exifOrientation!!)
            )
            binding.tvExifOrientation.visibility = View.VISIBLE
        } else {
            binding.tvExifOrientation.visibility = View.GONE
        }

        // EXIF GPS
        if (mediaFile.exifLatitude != null && mediaFile.exifLongitude != null) {
            binding.tvExifGPS.text = context.getString(
                R.string.exif_gps_label,
                formatGPS(mediaFile.exifLatitude!!, mediaFile.exifLongitude!!)
            )
            binding.tvExifGPS.visibility = View.VISIBLE
        } else {
            binding.tvExifGPS.visibility = View.GONE
        }
    }

    private fun displayVideoInfo() {
        // Duration
        if (mediaFile.duration != null) {
            binding.tvVideoDuration.text = context.getString(
                R.string.video_duration_label,
                formatDuration(mediaFile.duration!!)
            )
            binding.tvVideoDuration.visibility = View.VISIBLE
        } else {
            binding.tvVideoDuration.visibility = View.GONE
        }

        // Resolution
        if (mediaFile.width != null && mediaFile.height != null) {
            binding.tvVideoResolution.text = context.getString(
                R.string.video_resolution_label,
                mediaFile.width!!,
                mediaFile.height!!
            )
            binding.tvVideoResolution.visibility = View.VISIBLE
        } else {
            binding.tvVideoResolution.visibility = View.GONE
        }

        // Codec
        if (mediaFile.videoCodec != null) {
            binding.tvVideoCodec.text = context.getString(
                R.string.video_codec_label,
                mediaFile.videoCodec!!
            )
            binding.tvVideoCodec.visibility = View.VISIBLE
        } else {
            binding.tvVideoCodec.visibility = View.GONE
        }

        // Bitrate
        if (mediaFile.videoBitrate != null) {
            binding.tvVideoBitrate.text = context.getString(
                R.string.video_bitrate_label,
                formatBitrate(mediaFile.videoBitrate!!)
            )
            binding.tvVideoBitrate.visibility = View.VISIBLE
        } else {
            binding.tvVideoBitrate.visibility = View.GONE
        }

        // Frame Rate
        if (mediaFile.videoFrameRate != null) {
            binding.tvVideoFrameRate.text = context.getString(
                R.string.video_framerate_label,
                mediaFile.videoFrameRate!!
            )
            binding.tvVideoFrameRate.visibility = View.VISIBLE
        } else {
            binding.tvVideoFrameRate.visibility = View.GONE
        }

        // Rotation
        if (mediaFile.videoRotation != null) {
            binding.tvVideoRotation.text = context.getString(
                R.string.video_rotation_label,
                mediaFile.videoRotation!!
            )
            binding.tvVideoRotation.visibility = View.VISIBLE
        } else {
            binding.tvVideoRotation.visibility = View.GONE
        }
    }

    /**
     * Format file size to human-readable format (B, KB, MB, GB)
     */
    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.2f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }

    /**
     * Format timestamp to readable date/time
     */
    private fun formatDate(timestamp: Long): String {
        val date = Date(timestamp)
        val dateFormat = DateFormat.getDateFormat(context)
        val timeFormat = DateFormat.getTimeFormat(context)
        return "${dateFormat.format(date)} ${timeFormat.format(date)}"
    }

    /**
     * Format duration in milliseconds to HH:MM:SS or MM:SS
     */
    private fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000).toInt()
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }

    /**
     * Format EXIF orientation value to readable string
     */
    private fun formatOrientation(orientation: Int): String {
        return when (orientation) {
            1 -> "Normal"
            2 -> "Flip horizontal"
            3 -> "Rotate 180°"
            4 -> "Flip vertical"
            5 -> "Transpose"
            6 -> "Rotate 90° CW"
            7 -> "Transverse"
            8 -> "Rotate 270° CW"
            else -> "Unknown ($orientation)"
        }
    }

    /**
     * Format GPS coordinates to readable string
     */
    private fun formatGPS(latitude: Double, longitude: Double): String {
        val latDirection = if (latitude >= 0) "N" else "S"
        val lonDirection = if (longitude >= 0) "E" else "W"
        return String.format(
            "%.6f° %s, %.6f° %s",
            Math.abs(latitude),
            latDirection,
            Math.abs(longitude),
            lonDirection
        )
    }

    /**
     * Format bitrate to readable format (Kbps, Mbps)
     */
    private fun formatBitrate(bitrate: Int): String {
        val kbps = bitrate / 1000.0
        return if (kbps < 1000) {
            String.format("%.1f Kbps", kbps)
        } else {
            val mbps = kbps / 1000.0
            String.format("%.2f Mbps", mbps)
        }
    }
}
