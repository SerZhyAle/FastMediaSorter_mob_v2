package com.sza.fastmediasorter.ui.dialog.helpers

import android.content.Context
import android.view.View
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.AudioMetadataLoader
import com.sza.fastmediasorter.data.repository.AudioMetadataCacheRepository
import com.sza.fastmediasorter.databinding.DialogFileInfoBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class FileInfoAudioDisplayHelper(
    private val context: Context,
    private val binding: DialogFileInfoBinding,
    private val audioMetadataLoader: AudioMetadataLoader,
    private val audioMetadataCacheRepository: AudioMetadataCacheRepository
) {

    suspend fun displayDetailed(file: MediaFile) {
        try {
            val metadata = withContext(Dispatchers.IO) {
                audioMetadataLoader.loadDetailed(file)
            }
            withContext(Dispatchers.Main) {
                if (metadata == null) {
                    binding.ivAudioCoverArt.visibility = View.GONE
                    return@withContext
                }
                populateViews(file, metadata)
            }
        } catch (e: Exception) {
            Timber.w(e, "FileInfoAudioDisplayHelper: displayDetailed failed for ${file.path}")
        }
    }

    private fun populateViews(file: MediaFile, metadata: AudioMetadataLoader.AudioMetadata) {
        metadata.title?.let { title ->
            binding.tvAudioTitle?.text = context.getString(R.string.audio_title_label, title)
            binding.tvAudioTitle?.visibility = View.VISIBLE
        }

        metadata.artist?.let { artist ->
            binding.tvAudioArtist?.text = context.getString(R.string.audio_artist_label, artist)
            binding.tvAudioArtist?.visibility = View.VISIBLE
        }

        metadata.album?.let { album ->
            binding.tvAudioAlbum?.text = context.getString(R.string.audio_album_label, album)
            binding.tvAudioAlbum?.visibility = View.VISIBLE
        }

        file.duration?.takeIf { it > 0 }?.let { durationMs ->
            binding.tvAudioDuration.text = context.getString(
                R.string.audio_duration_label, formatDuration(durationMs)
            )
            binding.tvAudioDuration.visibility = View.VISIBLE
        }

        // Codec: audio/raw is reported by Media3 for FLAC; show FLAC when lossless
        if (metadata.lossless == true) {
            binding.tvAudioCodecInfo.text = context.getString(R.string.audio_codec_label, "FLAC")
            binding.tvAudioCodecInfo.visibility = View.VISIBLE
        }

        metadata.channels?.let { ch ->
            val channelsText = when (ch) {
                1 -> context.getString(R.string.audio_channels_mono)
                2 -> context.getString(R.string.audio_channels_stereo)
                else -> ch.toString()
            }
            binding.tvAudioChannels.text = context.getString(R.string.audio_channels_label, channelsText)
            binding.tvAudioChannels.visibility = View.VISIBLE
        }

        metadata.sampleRateHz?.let { hz ->
            binding.tvAudioSampleRate?.text = context.getString(R.string.audio_sample_rate_label, hz)
            binding.tvAudioSampleRate?.visibility = View.VISIBLE
        }

        metadata.bitDepth?.let { depth ->
            binding.tvAudioBitDepth.text = context.getString(R.string.audio_bit_depth_label, depth)
            binding.tvAudioBitDepth.visibility = View.VISIBLE
        }

        val durationSec = file.duration?.takeIf { it > 0 }?.let { it / 1000.0 }
        val bps = metadata.bitrateBps
            ?: if (durationSec != null && durationSec > 0.0) {
                (file.size * 8 / durationSec).toInt()
            } else null
        if (bps != null) {
            binding.tvAudioBitrate.text = context.getString(
                R.string.audio_bitrate_label, formatBitrate(bps)
            )
            binding.tvAudioBitrate.visibility = View.VISIBLE
        } else {
            binding.tvAudioBitrate.visibility = View.GONE
        }

        metadata.lossless?.let { isLossless ->
            val qualityStr = if (isLossless) {
                context.getString(R.string.audio_lossless_value_lossless)
            } else {
                context.getString(R.string.audio_lossless_value_lossy)
            }
            binding.tvAudioLossless.text = context.getString(R.string.audio_lossless_label, qualityStr)
            binding.tvAudioLossless.visibility = View.VISIBLE
        }

        metadata.replayGainTrackDb?.let { gain ->
            binding.tvAudioReplayGainTrack.text = context.getString(
                R.string.audio_replaygain_track_label, gain
            )
            binding.tvAudioReplayGainTrack.visibility = View.VISIBLE
        }

        metadata.replayGainAlbumDb?.let { gain ->
            binding.tvAudioReplayGainAlbum.text = context.getString(
                R.string.audio_replaygain_album_label, gain
            )
            binding.tvAudioReplayGainAlbum.visibility = View.VISIBLE
        }

        val coverFileName = metadata.coverFileName
        val coverExtension = metadata.coverExtension
        if (coverFileName != null && coverExtension != null) {
            val coverFile = audioMetadataCacheRepository.readCoverFile(coverFileName, coverExtension)
            if (coverFile != null && coverFile.exists()) {
                Glide.with(context)
                    .load(coverFile)
                    .centerCrop()
                    .into(binding.ivAudioCoverArt)
                binding.ivAudioCoverArt.isVisible = true
            } else {
                binding.ivAudioCoverArt.visibility = View.GONE
            }
        } else {
            binding.ivAudioCoverArt.visibility = View.GONE
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSec = durationMs / 1000
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun formatBitrate(bps: Int): String {
        val kbps = bps / 1000
        return "$kbps kbps"
    }
}
