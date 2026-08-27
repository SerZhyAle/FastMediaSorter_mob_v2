package com.sza.fastmediasorter.ui.player.helpers

import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.errorUnlessCancellation
import com.sza.fastmediasorter.core.util.formatFileSize
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Manages the audio info overlay: metadata line, file-info line, and format info from ExoPlayer. */
class AudioInfoDisplayHelper(
    private val binding: ActivityPlayerUnifiedBinding,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val callback: Callback,
) {
    interface Callback {
        fun getString(resId: Int): String
        fun getExoPlayer(): androidx.media3.exoplayer.ExoPlayer?
    }

    private val safeViews = PlayerBindingSafeViews(binding)

    // Cached parts assembled into the info line by buildAudioInfoLine()
    private var audioFileSizeStr: String = ""
    private var audioDurationStr: String = ""

    /**
     * Populates the audio info overlay with metadata and async-loaded size/duration.
     * Top line (audioMetadata): Artist - Album - Title, or directory/filename fallback.
     * Bottom line (audioFileInfo): Size • Duration (format added later by updateAudioFormatInfo).
     */
    fun showAudioFileInfo(file: MediaFile?) {
        if (file == null) return

        binding.audioInfoOverlay.isVisible = true

        // audioFileName view is never used now - full filename is in top-left tvFileNameOverlay
        safeViews.audioFileName.visibility = View.GONE

        val effectiveArtist = file.artist?.takeIf { it.isNotBlank() }
            ?: parseArtistFromPath(file.path)
        val effectiveTitle = file.title?.takeIf { it.isNotBlank() }
        val metadataParts = listOfNotNull(
            effectiveArtist,
            file.album?.takeIf { it.isNotBlank() },
            effectiveTitle
        )
        val metadataLine = if (metadataParts.isNotEmpty()) {
            metadataParts.joinToString(" - ")
        } else {
            val fileNameNoExt = file.name.substringBeforeLast('.')
            val dirName = file.path.substringBeforeLast('/').substringAfterLast('/')
            if (dirName.isNotBlank() && dirName != fileNameNoExt) "$dirName / $fileNameNoExt"
            else fileNameNoExt
        }
        safeViews.audioMetadata.text = metadataLine
        safeViews.audioMetadata.visibility = View.VISIBLE

        audioFileSizeStr = ""
        audioDurationStr = ""
        safeViews.audioFileInfo.text = ""

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileSize = file.size
                audioFileSizeStr = if (fileSize > 0) formatFileSize(fileSize) else ""
                audioDurationStr = file.duration?.let { if (it > 0) formatDuration(it) else "" } ?: ""
                withContext(Dispatchers.Main) { safeViews.audioFileInfo.text = buildAudioInfoLine(null) }
            } catch (e: Exception) {
                e.errorUnlessCancellation("Failed to get audio file info")
                withContext(Dispatchers.Main) {
                    safeViews.audioFileInfo.text = callback.getString(R.string.file_info_unavailable)
                }
            }
        }
    }

    /** Updates the format part of the info line from ExoPlayer's active audio track. */
    fun updateAudioFormatInfo() {
        val formatInfo = callback.getExoPlayer()?.currentTracks?.groups?.firstOrNull { group ->
            group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO
        }?.let { audioGroup ->
            val format = audioGroup.getTrackFormat(0)
            buildString {
                format.sampleMimeType?.let { append(it.substringAfter("audio/").uppercase()) }
                format.sampleRate.let { if (isNotEmpty()) append(" • "); append("${it / 1000} kHz") }
                format.channelCount.let {
                    if (isNotEmpty()) append(" • ")
                    append(when (it) { 1 -> "Mono"; 2 -> "Stereo"; else -> "$it channels" })
                }
                format.bitrate.let { if (it > 0) { if (isNotEmpty()) append(" • "); append("${it / 1000} kbps") } }
            }
        }
        if (!formatInfo.isNullOrEmpty()) safeViews.audioFileInfo.text = buildAudioInfoLine(formatInfo)
    }

    private fun buildAudioInfoLine(format: String?): String = buildList {
        if (audioFileSizeStr.isNotEmpty()) add(audioFileSizeStr)
        if (!format.isNullOrEmpty()) add(format)
        if (audioDurationStr.isNotEmpty()) add(audioDurationStr)
    }.joinToString(" • ")

    /**
     * Infers an artist name from the parent directory of the file path.
     * Handles: "YYYY-Artist-Album", "Artist - Album", plain "Artist" directory names.
     */
    private fun parseArtistFromPath(path: String): String? {
        val withoutScheme = path.substringAfter("://").ifEmpty { path }
        val parts = withoutScheme.split('/')
        val dirName = parts.dropLast(1).lastOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val withoutYear = dirName.replace(Regex("^\\d{4}[-\\s]"), "").trim()
        if (withoutYear.contains(" - "))
            return withoutYear.substringBefore(" - ").trim().takeIf { it.isNotBlank() }
        if (withoutYear.contains("-")) {
            val candidate = withoutYear.substringBefore("-").trim()
            if (candidate.isNotBlank() && !candidate.all { it.isDigit() }) return candidate
        }
        return null
    }

    private fun formatDuration(millis: Long?): String {
        if (millis == null || millis <= 0) return "N/A"
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes % 60, seconds % 60)
        else "%d:%02d".format(minutes, seconds % 60)
    }
}
