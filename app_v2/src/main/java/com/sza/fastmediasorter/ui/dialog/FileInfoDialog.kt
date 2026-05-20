package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.Toast
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogFileInfoBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.dialog.helpers.FileInfoAudioDisplayHelper
import com.sza.fastmediasorter.ui.dialog.helpers.FileInfoFileSectionHelper
import com.sza.fastmediasorter.ui.dialog.helpers.FileInfoLaunchManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dialog to display detailed file information including EXIF and video metadata
 */
class FileInfoDialog(
    context: Context,
    private val mediaFile: MediaFile,
    smbClient: com.sza.fastmediasorter.data.network.SmbClient? = null,
    sftpClient: com.sza.fastmediasorter.data.remote.sftp.SftpClient? = null,
    ftpClient: com.sza.fastmediasorter.data.remote.ftp.FtpClient? = null,
    credentialsRepository: com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository? = null,
    unifiedCache: com.sza.fastmediasorter.core.cache.UnifiedFileCache,
    private val downloadNetworkFileUseCase: com.sza.fastmediasorter.domain.usecase.DownloadNetworkFileUseCase? = null,
    private val audioMetadataLoader: com.sza.fastmediasorter.core.util.AudioMetadataLoader? = null,
    private val audioMetadataCacheRepository: com.sza.fastmediasorter.data.repository.AudioMetadataCacheRepository? = null
) : Dialog(context) {

    private lateinit var binding: DialogFileInfoBinding
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var audioDisplayHelper: FileInfoAudioDisplayHelper? = null
    private val metadataHelper = com.sza.fastmediasorter.core.util.MediaMetadataHelper(
        context, smbClient, sftpClient, ftpClient, credentialsRepository, unifiedCache
    )
    private lateinit var launchManager: FileInfoLaunchManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogFileInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        launchManager = FileInfoLaunchManager(
            context = context,
            mediaFile = mediaFile,
            downloadNetworkFileUseCase = downloadNetworkFileUseCase,
            scope = scope,
            onDismissRequested = { dismiss() }
        )

        if (mediaFile.type == MediaType.AUDIO &&
            audioMetadataLoader != null &&
            audioMetadataCacheRepository != null
        ) {
            audioDisplayHelper = FileInfoAudioDisplayHelper(
                context, binding, audioMetadataLoader, audioMetadataCacheRepository
            )
        }

        // Set dialog width to 90% of screen width for better readability
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )

        setupDialog()
        displayFileInfo()
        
        // Load detailed info asynchronously
        scope.launch {
            if (mediaFile.type == MediaType.AUDIO && audioDisplayHelper != null) {
                audioDisplayHelper!!.displayDetailed(mediaFile)
            } else {
                val details = metadataHelper.getDetailedInfo(mediaFile)
                updateDetailedInfo(details)
            }
        }
    }
    
    override fun onStop() {
        super.onStop()
        scope.cancel()
    }

    private fun setupDialog() {
        binding.btnClose.setOnClickListener {
            dismiss()
        }
        
        // Show "Open in External Player" button only for local files
        if (isLocalFile()) {
            binding.btnOpenExternal.visibility = View.VISIBLE
            binding.btnOpenExternal.setOnClickListener {
                launchManager.openInExternalPlayer()
            }
            binding.btnDownloadAndOpen.visibility = View.GONE
        } else {
            binding.btnOpenExternal.visibility = View.GONE
            binding.btnDownloadAndOpen.visibility = View.VISIBLE
            binding.btnDownloadAndOpen.setOnClickListener {
                launchManager.downloadAndOpenFile(
                    onProgressDialogReady = { /* dialog is shown by manager */ },
                    onFinished = { dismiss() }
                )
            }
        }
    }
    
    private fun isLocalFile(): Boolean {
        return (mediaFile.path.startsWith("/storage") || 
                mediaFile.path.matches(Regex("^/.*"))) && 
               !mediaFile.path.startsWith("smb://") &&
               !mediaFile.path.startsWith("sftp://") &&
               !mediaFile.path.startsWith("ftp://")
    }

    private fun isCloudFile(): Boolean {
        return mediaFile.path.startsWith("cloud://") ||
            mediaFile.path.startsWith("cloud:/")
    }

    private fun displayFileInfo() {
        FileInfoFileSectionHelper(context, binding).render(
            file = mediaFile,
            lastModifiedMs = mediaFile.lastModified.takeIf { it > 0L },
            isReadOnly = mediaFile.attributes?.readOnly == true,
            isHidden = mediaFile.attributes?.hidden == true
        )

        // EXIF Information (for images/GIFs) - show section for all images, hide later if no data
        if (mediaFile.type == MediaType.IMAGE || mediaFile.type == MediaType.GIF) {
            binding.sectionExif.visibility = View.VISIBLE
            displayExifInfo()
        } else {
            binding.sectionExif.visibility = View.GONE
        }

        // Audio Metadata (for audio files)
        if (mediaFile.type == MediaType.AUDIO) {
            binding.sectionAudio.visibility = View.VISIBLE
            displayAudioInfo()
        } else {
            binding.sectionAudio.visibility = View.GONE
        }

        // Video Metadata (for videos)
        if (mediaFile.type == MediaType.VIDEO) {
            binding.sectionVideo.visibility = View.VISIBLE
            displayVideoInfo()
        } else {
            binding.sectionVideo.visibility = View.GONE
        }

        // Document Metadata (for PDF/TXT/EPUB)
        if (mediaFile.type == MediaType.PDF || mediaFile.type == MediaType.TEXT || mediaFile.type == MediaType.EPUB) {
            binding.sectionDocument.visibility = View.VISIBLE
            displayDocumentInfo()
        } else {
            binding.sectionDocument.visibility = View.GONE
        }
    }

    private fun displayExifInfo() {
        // EXIF DateTime
        if (mediaFile.exifDateTime != null) {
            binding.tvExifDateTime.text = context.getString(
                R.string.exif_datetime_label,
                formatDate(mediaFile.exifDateTime)
            )
            binding.tvExifDateTime.visibility = View.VISIBLE
        } else {
            binding.tvExifDateTime.visibility = View.GONE
        }

        // EXIF Orientation
        if (mediaFile.exifOrientation != null) {
            binding.tvExifOrientation.text = context.getString(
                R.string.exif_orientation_label,
                formatOrientation(mediaFile.exifOrientation)
            )
            binding.tvExifOrientation.visibility = View.VISIBLE
        } else {
            binding.tvExifOrientation.visibility = View.GONE
        }

        // EXIF GPS
        if (mediaFile.exifLatitude != null && mediaFile.exifLongitude != null) {
            binding.tvExifGPS.text = context.getString(
                R.string.exif_gps_label,
                formatGPS(mediaFile.exifLatitude, mediaFile.exifLongitude)
            )
            binding.tvExifGPS.visibility = View.VISIBLE
        } else {
            binding.tvExifGPS.visibility = View.GONE
        }
    }

    private fun displayAudioInfo() {
        // Duration - always visible: show real value or placeholder while async loads
        val durationMs = mediaFile.duration ?: 0L
        if (durationMs > 0) {
            binding.tvAudioDuration.text = context.getString(
                R.string.audio_duration_label,
                formatDuration(durationMs)
            )
        } else {
            // Placeholder: async updateDetailedInfo() will replace this once metadata arrives
            binding.tvAudioDuration.text = context.getString(
                R.string.audio_duration_label,
                context.getString(R.string.loading_placeholder)
            )
        }
        binding.tvAudioDuration.visibility = View.VISIBLE
    }

    private fun displayVideoInfo() {
        // Duration - show only if duration is actually known (> 0); async will fill it in for network files
        if (mediaFile.duration != null && mediaFile.duration > 0) {
            binding.tvVideoDuration.text = context.getString(
                R.string.video_duration_label,
                formatDuration(mediaFile.duration)
            )
            binding.tvVideoDuration.visibility = View.VISIBLE
        } else {
            // Hide until async metadata arrives (e.g. for SMB files where duration is 0 before extraction)
            binding.tvVideoDuration.visibility = View.GONE
        }

        // Resolution
        if (mediaFile.width != null && mediaFile.height != null) {
            binding.tvVideoResolution.text = context.getString(
                R.string.video_resolution_label,
                mediaFile.width,
                mediaFile.height
            )
            binding.tvVideoResolution.visibility = View.VISIBLE
        } else {
            binding.tvVideoResolution.visibility = View.GONE
        }

        // Codec
        if (mediaFile.videoCodec != null) {
            binding.tvVideoCodec.text = context.getString(
                R.string.video_codec_label,
                mediaFile.videoCodec
            )
            binding.tvVideoCodec.visibility = View.VISIBLE
        } else {
            binding.tvVideoCodec.visibility = View.GONE
        }

        // Bitrate
        if (mediaFile.videoBitrate != null) {
            binding.tvVideoBitrate.text = context.getString(
                R.string.video_bitrate_label,
                formatBitrate(mediaFile.videoBitrate)
            )
            binding.tvVideoBitrate.visibility = View.VISIBLE
        } else {
            binding.tvVideoBitrate.visibility = View.GONE
        }

        // Frame Rate
        if (mediaFile.videoFrameRate != null) {
            binding.tvVideoFrameRate.text = context.getString(
                R.string.video_framerate_label,
                mediaFile.videoFrameRate
            )
            binding.tvVideoFrameRate.visibility = View.VISIBLE
        } else {
            binding.tvVideoFrameRate.visibility = View.GONE
        }

        // Rotation
        if (mediaFile.videoRotation != null) {
            binding.tvVideoRotation.text = context.getString(
                R.string.video_rotation_label,
                mediaFile.videoRotation
            )
            binding.tvVideoRotation.visibility = View.VISIBLE
        } else {
            binding.tvVideoRotation.visibility = View.GONE
        }

        // New async-populated fields: hide initially
        binding.tvVideoAspectRatio.visibility = View.GONE
        binding.tvVideoAudioChannels.visibility = View.GONE
        binding.tvVideoAudioBitrate.visibility = View.GONE
    }

    private fun displayDocumentInfo() {
        // Document info will be loaded asynchronously in updateDetailedInfo
        // Hide all fields initially - they will be shown when data is available
        binding.tvDocPageCount.visibility = View.GONE
        binding.tvDocTitle.visibility = View.GONE
        binding.tvDocAuthor.visibility = View.GONE
        binding.tvDocChapterCount.visibility = View.GONE
        binding.tvDocLineCount.visibility = View.GONE
        binding.tvDocWordCount.visibility = View.GONE
        binding.tvDocCharCount.visibility = View.GONE
        binding.tvDocEncoding.visibility = View.GONE
        // PDF-specific fields
        binding.tvPdfVersion.visibility = View.GONE
        binding.tvPdfCreator.visibility = View.GONE
        binding.tvPdfProducer.visibility = View.GONE
        binding.tvPdfSubject.visibility = View.GONE
        binding.tvPdfKeywords.visibility = View.GONE
        binding.tvPdfCreationDate.visibility = View.GONE
        binding.tvPdfModDate.visibility = View.GONE
    }

    private fun updateDetailedInfo(details: com.sza.fastmediasorter.core.util.DetailedMediaInfo) {
        timber.log.Timber.d("updateDetailedInfo: width=${details.width}, height=${details.height}, duration=${details.duration}, codec=${details.videoCodec}, bitrate=${details.bitrate}, fps=${details.frameRate}")
        
        // Image Resolution (for images/GIFs)
        if (details.width != null && details.height != null) {
            binding.tvImageResolution.text = context.getString(R.string.image_resolution_label, details.width, details.height)
            binding.tvImageResolution.visibility = View.VISIBLE
            
            // Calculate and display megapixels
            val megapixels = (details.width * details.height) / 1_000_000.0
            binding.tvImageMegapixels.text = context.getString(
                R.string.image_megapixels_label,
                String.format(Locale.getDefault(), "%.1f", megapixels)
            )
            binding.tvImageMegapixels.visibility = View.VISIBLE
        }
        
        // Image Format (from file extension)
        if (mediaFile.type == MediaType.IMAGE || mediaFile.type == MediaType.GIF) {
            val extension = mediaFile.name.substringAfterLast('.', "").uppercase()
            val formatName = when (extension) {
                "JPG", "JPEG" -> "JPEG"
                "PNG" -> "PNG"
                "WEBP" -> "WebP"
                "HEIC", "HEIF" -> "HEIF"
                "BMP" -> "BMP"
                "AVIF" -> "AVIF"
                "GIF" -> "GIF"
                else -> extension
            }
            binding.tvImageFormat.text = context.getString(R.string.image_format_label, formatName)
            binding.tvImageFormat.visibility = View.VISIBLE
        }
        
        // Color Space
        if (details.colorSpace != null) {
            binding.tvImageColorSpace.text = context.getString(R.string.image_color_space_label, details.colorSpace)
            binding.tvImageColorSpace.visibility = View.VISIBLE
        }
        
        // Camera Info
        if (details.cameraModel != null) {
            binding.tvExifCamera.text = context.getString(R.string.exif_camera_model_label, details.cameraModel)
            binding.tvExifCamera.visibility = View.VISIBLE
        }
        
        if (details.iso != null) {
            binding.tvExifISO.text = context.getString(R.string.exif_iso_label, details.iso)
            binding.tvExifISO.visibility = View.VISIBLE
        }
        
        if (details.aperture != null) {
            binding.tvExifAperture.text = context.getString(R.string.exif_aperture_label, details.aperture)
            binding.tvExifAperture.visibility = View.VISIBLE
        }
        
        if (details.exposureTime != null) {
            binding.tvExifExposure.text = context.getString(R.string.exif_exposure_label, details.exposureTime)
            binding.tvExifExposure.visibility = View.VISIBLE
        }
        
        if (details.focalLength != null) {
            binding.tvExifFocalLength.text = context.getString(R.string.exif_focal_length_label, details.focalLength)
            binding.tvExifFocalLength.visibility = View.VISIBLE
        }
        
        // GIF Frames
        if (details.gifFrameCount != null) {
            binding.tvGifFrames.text = context.getString(R.string.gif_frames_label, details.gifFrameCount)
            binding.tvGifFrames.visibility = View.VISIBLE
        }
        
        // Audio Codes (for audio section)
        if (details.audioCodec != null && mediaFile.type == MediaType.AUDIO) {
            val codec = details.audioCodec.substringAfter("audio/").uppercase()
            binding.tvAudioCodecInfo.text = context.getString(R.string.audio_codec_label, codec)
            binding.tvAudioCodecInfo.visibility = View.VISIBLE
        }

        // Audio Title
        if (details.audioTitle != null) {
            binding.tvAudioTitle?.text = context.getString(R.string.audio_title_label, details.audioTitle)
            binding.tvAudioTitle?.visibility = View.VISIBLE
        }

        // Audio Artist
        if (details.audioArtist != null) {
            binding.tvAudioArtist?.text = context.getString(R.string.audio_artist_label, details.audioArtist)
            binding.tvAudioArtist?.visibility = View.VISIBLE
        }

        // Audio Album
        if (details.audioAlbum != null && mediaFile.type == MediaType.AUDIO) {
            binding.tvAudioAlbum?.text = context.getString(R.string.audio_album_label, details.audioAlbum)
            binding.tvAudioAlbum?.visibility = View.VISIBLE
        }
        
        // Audio Channels
        if (details.audioChannels != null) {
            val channelsText = when (details.audioChannels) {
                1 -> "Mono"
                2 -> "Stereo"
                else -> "${details.audioChannels} channels"
            }
            binding.tvAudioChannels.text = context.getString(R.string.audio_channels_label, channelsText)
            binding.tvAudioChannels.visibility = View.VISIBLE
        }
        
        // Audio Bitrate
        if (details.audioBitrate != null) {
            binding.tvAudioBitrate.text = context.getString(R.string.audio_bitrate_label, formatBitrate(details.audioBitrate))
            binding.tvAudioBitrate.visibility = View.VISIBLE
        }

        // Audio Sample Rate
        if (details.sampleRate != null && mediaFile.type == MediaType.AUDIO) {
            binding.tvAudioSampleRate?.text = context.getString(R.string.audio_sample_rate_label, details.sampleRate)
            binding.tvAudioSampleRate?.visibility = View.VISIBLE
        }
        
        // Audio Codec for video (in video section)
        if (details.audioCodec != null && mediaFile.type == MediaType.VIDEO) {
            binding.tvAudioCodec.text = context.getString(R.string.audio_codec_label, details.audioCodec)
            binding.tvAudioCodec.visibility = View.VISIBLE
        }

        // Audio channels for video (separate from audio-file section)
        if (details.audioChannels != null && mediaFile.type == MediaType.VIDEO) {
            val channelsText = when (details.audioChannels) {
                1 -> context.getString(R.string.audio_channels_mono)
                2 -> context.getString(R.string.audio_channels_stereo)
                else -> "${details.audioChannels} ch"
            }
            binding.tvVideoAudioChannels.text = context.getString(R.string.video_audio_channels_label, channelsText)
            binding.tvVideoAudioChannels.visibility = View.VISIBLE
        }

        // Audio bitrate for video
        if (details.audioBitrate != null && mediaFile.type == MediaType.VIDEO) {
            binding.tvVideoAudioBitrate.text = context.getString(R.string.video_audio_bitrate_label, formatBitrate(details.audioBitrate))
            binding.tvVideoAudioBitrate.visibility = View.VISIBLE
        }
        
        // Update Video Codec if we found a better one or if it was missing
        if (details.videoCodec != null) {
             binding.tvVideoCodec.text = context.getString(R.string.video_codec_label, details.videoCodec)
             binding.tvVideoCodec.visibility = View.VISIBLE
        }

        // Fix duration for network/SMB files: mediaFile.duration may be 0 if not populated during browse
        if (details.duration != null && details.duration > 0 && mediaFile.type == MediaType.VIDEO) {
            binding.tvVideoDuration.text = context.getString(R.string.video_duration_label, formatDuration(details.duration))
            binding.tvVideoDuration.visibility = View.VISIBLE
        }
        if (details.duration != null && details.duration > 0 && mediaFile.type == MediaType.AUDIO) {
            binding.tvAudioDuration.text = context.getString(R.string.audio_duration_label, formatDuration(details.duration))
            binding.tvAudioDuration.visibility = View.VISIBLE
        }
        
        // Update Video Resolution if missing
        if (details.width != null && details.height != null && mediaFile.type == MediaType.VIDEO) {
            binding.tvVideoResolution.text = context.getString(
                R.string.video_resolution_label,
                details.width,
                details.height
            )
            binding.tvVideoResolution.visibility = View.VISIBLE

            // Show aspect ratio (simplified, e.g. 16:9)
            val gcdVal = gcd(details.width, details.height)
            val arW = details.width / gcdVal
            val arH = details.height / gcdVal
            binding.tvVideoAspectRatio.text = context.getString(R.string.video_aspect_ratio_label, "$arW:$arH")
            binding.tvVideoAspectRatio.visibility = View.VISIBLE
        }
        
        // Video Bitrate
        if (details.bitrate != null) {
            val bitrateMbps = details.bitrate / 1_000_000.0
            binding.tvVideoBitrate.text = context.getString(R.string.video_bitrate_label, String.format(Locale.getDefault(), "%.2f", bitrateMbps))
            binding.tvVideoBitrate.visibility = View.VISIBLE
        }
        
        // Video Frame Rate
        if (details.frameRate != null) {
            binding.tvVideoFrameRate.text = context.getString(R.string.video_framerate_label, details.frameRate)
            binding.tvVideoFrameRate.visibility = View.VISIBLE
        }
        
        // GPS Location
        if (details.latitude != null && details.longitude != null) {
            val locationText = context.getString(
                R.string.gps_location_label,
                String.format(Locale.getDefault(), "%.6f", details.latitude),
                String.format(Locale.getDefault(), "%.6f", details.longitude)
            )
            binding.tvGpsLocation.text = locationText
            binding.tvGpsLocation.visibility = View.VISIBLE
            
            // Make clickable to open Google Maps
            binding.tvGpsLocation.setOnClickListener {
                val uri = "https://www.google.com/maps?q=${details.latitude},${details.longitude}"
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri))
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    timber.log.Timber.w(e, "FileInfoDialog: no maps app available")
                    Toast.makeText(context, context.getString(R.string.no_maps_app_available), Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // Document Metadata (PDF/TXT/EPUB)
        updateDocumentInfo(details)
    }
    
    private fun updateDocumentInfo(details: com.sza.fastmediasorter.core.util.DetailedMediaInfo) {
        // PDF: Page count
        if (details.pageCount != null) {
            binding.tvDocPageCount.text = context.getString(R.string.doc_page_count_label, details.pageCount)
            binding.tvDocPageCount.visibility = View.VISIBLE
        }
        
        // PDF/EPUB: Title
        if (details.docTitle != null) {
            binding.tvDocTitle.text = context.getString(R.string.doc_title_label, details.docTitle)
            binding.tvDocTitle.visibility = View.VISIBLE
        }
        
        // PDF/EPUB: Author
        if (details.docAuthor != null) {
            binding.tvDocAuthor.text = context.getString(R.string.doc_author_label, details.docAuthor)
            binding.tvDocAuthor.visibility = View.VISIBLE
        }
        
        // PDF: Version
        if (details.pdfVersion != null) {
            binding.tvPdfVersion.text = context.getString(R.string.pdf_version_label, details.pdfVersion)
            binding.tvPdfVersion.visibility = View.VISIBLE
        }
        
        // PDF: Creator
        if (details.pdfCreator != null) {
            binding.tvPdfCreator.text = context.getString(R.string.pdf_creator_label, details.pdfCreator)
            binding.tvPdfCreator.visibility = View.VISIBLE
        }
        
        // PDF: Producer
        if (details.pdfProducer != null) {
            binding.tvPdfProducer.text = context.getString(R.string.pdf_producer_label, details.pdfProducer)
            binding.tvPdfProducer.visibility = View.VISIBLE
        }
        
        // PDF: Subject
        if (details.pdfSubject != null) {
            binding.tvPdfSubject.text = context.getString(R.string.pdf_subject_label, details.pdfSubject)
            binding.tvPdfSubject.visibility = View.VISIBLE
        }
        
        // PDF: Keywords
        if (details.pdfKeywords != null) {
            binding.tvPdfKeywords.text = context.getString(R.string.pdf_keywords_label, details.pdfKeywords)
            binding.tvPdfKeywords.visibility = View.VISIBLE
        }
        
        // PDF: Creation Date
        if (details.pdfCreationDate != null) {
            binding.tvPdfCreationDate.text = context.getString(R.string.pdf_creation_date_label, details.pdfCreationDate)
            binding.tvPdfCreationDate.visibility = View.VISIBLE
        }
        
        // PDF: Modification Date
        if (details.pdfModificationDate != null) {
            binding.tvPdfModDate.text = context.getString(R.string.pdf_mod_date_label, details.pdfModificationDate)
            binding.tvPdfModDate.visibility = View.VISIBLE
        }
        
        // EPUB: Chapter count
        if (details.chapterCount != null) {
            binding.tvDocChapterCount.text = context.getString(R.string.doc_chapter_count_label, details.chapterCount)
            binding.tvDocChapterCount.visibility = View.VISIBLE
        }
        
        // TXT: Line count
        if (details.lineCount != null) {
            binding.tvDocLineCount.text = context.getString(R.string.doc_line_count_label, details.lineCount)
            binding.tvDocLineCount.visibility = View.VISIBLE
        }
        
        // TXT: Word count
        if (details.wordCount != null) {
            binding.tvDocWordCount.text = context.getString(R.string.doc_word_count_label, details.wordCount)
            binding.tvDocWordCount.visibility = View.VISIBLE
        }
        
        // TXT: Character count
        if (details.charCount != null) {
            binding.tvDocCharCount.text = context.getString(R.string.doc_char_count_label, details.charCount)
            binding.tvDocCharCount.visibility = View.VISIBLE
        }
        
        // TXT: Encoding
        if (details.encoding != null) {
            binding.tvDocEncoding.text = context.getString(R.string.doc_encoding_label, details.encoding)
            binding.tvDocEncoding.visibility = View.VISIBLE
        }
    }

    /** Formats a Unix timestamp to localised date + time string. */
    private fun formatDate(timestamp: Long): String {
        val date = Date(timestamp)
        val dateFormat = DateFormat.getDateFormat(context)
        val timeFormat = DateFormat.getTimeFormat(context)
        return "${dateFormat.format(date)} ${timeFormat.format(date)}"
    }

    /** Formats duration in ms to HH:MM:SS (or MM:SS if < 1 hour). */
    private fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000).toInt()
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
        }
    }

    /** Maps EXIF orientation integer to a human-readable rotation/flip label. */
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
            Locale.getDefault(),
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
            String.format(Locale.getDefault(), "%.1f Kbps", kbps)
        } else {
            val mbps = kbps / 1000.0
            String.format(Locale.getDefault(), "%.2f Mbps", mbps)
        }
    }

    /** Euclid GCD - used to simplify aspect ratio (e.g. 1920x1080 → 16:9) */
    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
}
