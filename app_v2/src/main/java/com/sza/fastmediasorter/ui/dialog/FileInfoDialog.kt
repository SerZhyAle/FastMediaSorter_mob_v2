package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.formatFileSize
import com.sza.fastmediasorter.databinding.DialogFileInfoBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
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
    private val downloadNetworkFileUseCase: com.sza.fastmediasorter.domain.usecase.DownloadNetworkFileUseCase? = null
) : Dialog(context) {

    private lateinit var binding: DialogFileInfoBinding
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val metadataHelper = com.sza.fastmediasorter.core.util.MediaMetadataHelper(
        context, smbClient, sftpClient, ftpClient, credentialsRepository, unifiedCache
    )

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
        
        // Load detailed info asynchronously
        scope.launch {
            val details = metadataHelper.getDetailedInfo(mediaFile)
            updateDetailedInfo(details)
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
                openInExternalPlayer()
            }
            binding.btnDownloadAndOpen.visibility = View.GONE
        } else {
            binding.btnOpenExternal.visibility = View.GONE
            binding.btnDownloadAndOpen.visibility = View.VISIBLE
            binding.btnDownloadAndOpen.setOnClickListener {
                downloadAndOpenFile()
            }
        }
    }
    
    private fun isLocalFile(): Boolean {
        return (mediaFile.path.startsWith("/storage") || 
                mediaFile.path.startsWith("/sdcard") ||
                mediaFile.path.matches(Regex("^/.*"))) && 
               !mediaFile.path.startsWith("smb://") &&
               !mediaFile.path.startsWith("sftp://") &&
               !mediaFile.path.startsWith("ftp://")
    }
    
    private fun openInExternalPlayer() {
        timber.log.Timber.d("FileInfoDialog.openInExternalPlayer: Opening file ${mediaFile.name} (path=${mediaFile.path})")
        
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            
            // Determine MIME type based on file extension or MediaType
            val mimeType = when (mediaFile.type) {
                MediaType.VIDEO -> {
                    val extension = mediaFile.name.substringAfterLast('.', "").lowercase()
                    when (extension) {
                        "mp4" -> "video/mp4"
                        "mkv" -> "video/x-matroska"
                        "avi" -> "video/x-msvideo"
                        "mov" -> "video/quicktime"
                        "webm" -> "video/webm"
                        "3gp" -> "video/3gpp"
                        else -> "video/*"
                    }
                }
                MediaType.AUDIO -> {
                    val extension = mediaFile.name.substringAfterLast('.', "").lowercase()
                    when (extension) {
                        "mp3" -> "audio/mpeg"
                        "m4a" -> "audio/mp4"
                        "wav" -> "audio/wav"
                        "ogg" -> "audio/ogg"
                        "flac" -> "audio/flac"
                        else -> "audio/*"
                    }
                }
                MediaType.IMAGE -> {
                    val extension = mediaFile.name.substringAfterLast('.', "").lowercase()
                    when (extension) {
                        "jpg", "jpeg" -> "image/jpeg"
                        "png" -> "image/png"
                        "webp" -> "image/webp"
                        "bmp" -> "image/bmp"
                        else -> "image/*"
                    }
                }
                MediaType.GIF -> "image/gif"
                MediaType.TEXT -> "text/plain"
                MediaType.PDF -> "application/pdf"
                MediaType.EPUB -> "application/epub+zip"
                MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK,
                MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> "application/octet-stream"
            }
            
            timber.log.Timber.d("FileInfoDialog.openInExternalPlayer: MIME type = $mimeType")
            
            // Handle different path types
            val uri = when {
                mediaFile.path.startsWith("content://") -> {
                    // SAF URI - use directly
                    timber.log.Timber.d("FileInfoDialog.openInExternalPlayer: Using content:// URI directly")
                    android.net.Uri.parse(mediaFile.path)
                }
                else -> {
                    // Regular file path
                    val file = java.io.File(mediaFile.path)
                    timber.log.Timber.d("FileInfoDialog.openInExternalPlayer: File path = ${file.absolutePath}, exists = ${file.exists()}")
                    
                    if (!file.exists()) {
                        timber.log.Timber.w("FileInfoDialog.openInExternalPlayer: File does not exist!")
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.error_opening_file, "File not found"),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        return
                    }
                    
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                }
            }
            
            timber.log.Timber.d("FileInfoDialog.openInExternalPlayer: URI = $uri")
            
            intent.setDataAndType(uri, mimeType)
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            // Use chooser to show all available apps (handles Android 11+ package visibility)
            val chooserIntent = android.content.Intent.createChooser(intent, context.getString(R.string.open_with))
            chooserIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            
            timber.log.Timber.d("FileInfoDialog.openInExternalPlayer: Starting chooser activity")
            context.startActivity(chooserIntent)
            dismiss()
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to open file in external player")
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.error_opening_file, e.message),
                android.widget.Toast.LENGTH_SHORT
            ).show()
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
        // Duration
        if (mediaFile.duration != null) {
            binding.tvAudioDuration.text = context.getString(
                R.string.audio_duration_label,
                formatDuration(mediaFile.duration)
            )
            binding.tvAudioDuration.visibility = View.VISIBLE
        } else {
            binding.tvAudioDuration.visibility = View.GONE
        }
    }

    private fun displayVideoInfo() {
        // Duration
        if (mediaFile.duration != null) {
            binding.tvVideoDuration.text = context.getString(
                R.string.video_duration_label,
                formatDuration(mediaFile.duration)
            )
            binding.tvVideoDuration.visibility = View.VISIBLE
        } else {
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
                String.format("%.1f", megapixels)
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
        
        // Audio Codec (for audio section)
        if (details.audioCodec != null && mediaFile.type == MediaType.AUDIO) {
            val codec = details.audioCodec.substringAfter("audio/").uppercase()
            binding.tvAudioCodecInfo.text = context.getString(R.string.audio_codec_label, codec)
            binding.tvAudioCodecInfo.visibility = View.VISIBLE
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
        
        // Audio Codec for video (in video section)
        if (details.audioCodec != null && mediaFile.type == MediaType.VIDEO) {
            binding.tvAudioCodec.text = context.getString(R.string.audio_codec_label, details.audioCodec)
            binding.tvAudioCodec.visibility = View.VISIBLE
        }
        
        // Update Video Codec if we found a better one or if it was missing
        if (details.videoCodec != null) {
             binding.tvVideoCodec.text = context.getString(R.string.video_codec_label, details.videoCodec)
             binding.tvVideoCodec.visibility = View.VISIBLE
        }
        
        // Update Video Resolution if missing
        if (details.width != null && details.height != null && mediaFile.type == MediaType.VIDEO) {
            binding.tvVideoResolution.text = context.getString(
                R.string.video_resolution_label,
                details.width,
                details.height
            )
            binding.tvVideoResolution.visibility = View.VISIBLE
        }
        
        // Video Bitrate
        if (details.bitrate != null) {
            val bitrateMbps = details.bitrate / 1_000_000.0
            binding.tvVideoBitrate.text = context.getString(R.string.video_bitrate_label, String.format("%.2f", bitrateMbps))
            binding.tvVideoBitrate.visibility = View.VISIBLE
        }
        
        // Video Frame Rate
        if (details.frameRate != null) {
            binding.tvVideoFrameRate.text = context.getString(R.string.video_framerate_label, String.format("%.2f", details.frameRate))
            binding.tvVideoFrameRate.visibility = View.VISIBLE
        }
        
        // GPS Location
        if (details.latitude != null && details.longitude != null) {
            val locationText = context.getString(
                R.string.gps_location_label,
                String.format("%.6f", details.latitude),
                String.format("%.6f", details.longitude)
            )
            binding.tvGpsLocation.text = locationText
            binding.tvGpsLocation.visibility = View.VISIBLE
            
            // Make clickable to open Google Maps
            binding.tvGpsLocation.setOnClickListener {
                val uri = "https://www.google.com/maps?q=${details.latitude},${details.longitude}"
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri))
                context.startActivity(intent)
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

    private fun downloadAndOpenFile() {
        if (downloadNetworkFileUseCase == null) {
            timber.log.Timber.e("DownloadNetworkFileUseCase not available")
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.download_failed, "UseCase not available"),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        val progressDialog = MaterialProgressDialog(context).apply {
            setTitle(context.getString(R.string.downloading_file))
            setMessage(mediaFile.name)
            setProgressStyle(MaterialProgressDialog.STYLE_HORIZONTAL)
            max = 100
            setCancelable(false)
            show()
        }

        scope.launch(Dispatchers.IO) {
            try {
                // Get Downloads folder
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                val targetFile = java.io.File(downloadsDir, mediaFile.name)
                
                timber.log.Timber.d("Downloading ${mediaFile.path} to ${targetFile.absolutePath}")
                
                // Download file using UseCase
                val success = downloadNetworkFileUseCase.execute(
                    remotePath = mediaFile.path,
                    targetFile = targetFile,
                    progressCallback = { progress ->
                        scope.launch(Dispatchers.Main) {
                            progressDialog.progress = progress
                        }
                    }
                )

                launch(Dispatchers.Main) {
                    progressDialog.dismiss()
                    
                    if (success) {
                        timber.log.Timber.d("Download successful: ${targetFile.absolutePath}")
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.downloaded_successfully),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        
                        // Open file with default app
                        openDownloadedFile(targetFile)
                        dismiss()
                    } else {
                        timber.log.Timber.e("Download failed")
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.download_failed, "Unknown error"),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Error downloading file")
                launch(Dispatchers.Main) {
                    progressDialog.dismiss()
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.download_failed, e.message),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun openDownloadedFile(file: java.io.File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            // Determine MIME type
            val mimeType = when (mediaFile.type) {
                MediaType.VIDEO -> {
                    val extension = file.extension.lowercase()
                    when (extension) {
                        "mp4" -> "video/mp4"
                        "mkv" -> "video/x-matroska"
                        "avi" -> "video/x-msvideo"
                        "mov" -> "video/quicktime"
                        "webm" -> "video/webm"
                        "3gp" -> "video/3gpp"
                        else -> "video/*"
                    }
                }
                MediaType.AUDIO -> {
                    val extension = file.extension.lowercase()
                    when (extension) {
                        "mp3" -> "audio/mpeg"
                        "m4a" -> "audio/mp4"
                        "wav" -> "audio/wav"
                        "ogg" -> "audio/ogg"
                        "flac" -> "audio/flac"
                        else -> "audio/*"
                    }
                }
                MediaType.IMAGE -> {
                    val extension = file.extension.lowercase()
                    when (extension) {
                        "jpg", "jpeg" -> "image/jpeg"
                        "png" -> "image/png"
                        "webp" -> "image/webp"
                        "bmp" -> "image/bmp"
                        else -> "image/*"
                    }
                }
                MediaType.GIF -> "image/gif"
                MediaType.TEXT -> "text/plain"
                MediaType.PDF -> "application/pdf"
                MediaType.EPUB -> "application/epub+zip"
                MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK,
                MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> "application/octet-stream"
            }

            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            intent.setDataAndType(uri, mimeType)
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            val chooserIntent = android.content.Intent.createChooser(intent, context.getString(R.string.open_with))
            chooserIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error opening downloaded file")
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.error_opening_file, e.message),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
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
