package com.sza.fastmediasorter.core.util

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.exifinterface.media.ExifInterface
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream

data class DetailedMediaInfo(
    val width: Int? = null,
    val height: Int? = null,
    val cameraModel: String? = null,
    val cameraMake: String? = null,
    val iso: String? = null,
    val aperture: String? = null,
    val exposureTime: String? = null,
    val focalLength: String? = null,
    val colorSpace: String? = null, // Color space/profile from EXIF
    val gifFrameCount: Int? = null,
    val audioCodec: String? = null,
    val audioChannels: Int? = null,
    val audioBitrate: Int? = null, // bits per second
    val videoCodec: String? = null,
    val duration: Long? = null, // milliseconds
    val bitrate: Int? = null, // bits per second
    val frameRate: Double? = null, // frames per second
    val latitude: Double? = null,
    val longitude: Double? = null,
    // Audio metadata
    val audioTitle: String? = null,
    val audioArtist: String? = null,
    val audioAlbum: String? = null,   // Album from METADATA_KEY_ALBUM
    val sampleRate: Int? = null,      // Sample rate in Hz from METADATA_KEY_SAMPLERATE
    // Document metadata (PDF/TXT/EPUB)
    val pageCount: Int? = null,         // PDF pages
    val docTitle: String? = null,       // PDF/EPUB title
    val docAuthor: String? = null,      // PDF/EPUB author
    val chapterCount: Int? = null,      // EPUB chapters
    val lineCount: Int? = null,         // TXT lines
    val wordCount: Int? = null,         // TXT words
    val charCount: Int? = null,         // TXT characters
    val encoding: String? = null,       // TXT encoding
    // PDF-specific metadata
    val pdfVersion: String? = null,     // PDF version (e.g., "1.7")
    val pdfCreator: String? = null,     // Creator application
    val pdfProducer: String? = null,    // Producer (converter tool)
    val pdfSubject: String? = null,     // Document subject
    val pdfKeywords: String? = null,    // Keywords
    val pdfCreationDate: String? = null,    // Creation date
    val pdfModificationDate: String? = null // Modification date
)

class MediaMetadataHelper(
    private val context: Context,
    private val smbClient: SmbClient? = null,
    private val sftpClient: SftpClient? = null,
    private val ftpClient: FtpClient? = null,
    private val credentialsRepository: NetworkCredentialsRepository? = null,
    private val unifiedCache: UnifiedFileCache
) {
    private val networkDownloader = NetworkFileDownloader(
        context = context,
        smbClient = smbClient,
        sftpClient = sftpClient,
        ftpClient = ftpClient,
        credentialsRepository = credentialsRepository,
        unifiedCache = unifiedCache
    )
    
    private val safExtractor = SafUriExtractor(context)
    private val documentExtractor = DocumentMetadataExtractor(context)

    suspend fun getDetailedInfo(mediaFile: MediaFile): DetailedMediaInfo = withContext(Dispatchers.IO) {
        val info = DetailedMediaInfo()
        try {
            val file: File
            var tempFile: File? = null
            val isContentUri = mediaFile.path.startsWith("content:/")
            
            // For network files, download to temp for metadata extraction
            if (mediaFile.path.startsWith("smb://") || mediaFile.path.startsWith("sftp://") || mediaFile.path.startsWith("ftp://")) {
                tempFile = networkDownloader.downloadToTemp(
                    networkPath = mediaFile.path, 
                    fileType = mediaFile.type,
                    fileSize = mediaFile.size // Pass size for cache key
                )
                if (tempFile == null || !tempFile.exists()) {
                    Timber.w("Failed to download network file for metadata: ${mediaFile.path}")
                    return@withContext info
                }
                if (tempFile.length() == 0L) {
                    Timber.w("Downloaded file is empty: ${mediaFile.path}")
                    tempFile.delete()
                    return@withContext info
                }
                file = tempFile
            } else if (isContentUri) {
                // Handle SAF URIs - use ContentResolver directly (don't create File object)
                // For SAF, we'll pass the URI to specialized extraction methods
                Timber.d("Processing SAF URI for metadata: ${mediaFile.path}")
                val result = when (mediaFile.type) {
                    MediaType.IMAGE -> safExtractor.extractImageInfo(mediaFile.path)
                    MediaType.GIF -> safExtractor.extractGifInfo(mediaFile.path)
                    MediaType.VIDEO, MediaType.AUDIO -> safExtractor.extractVideoAudioInfo(mediaFile.path)
                    MediaType.PDF -> safExtractor.extractPdfInfo(mediaFile.path)
                    MediaType.TEXT -> safExtractor.extractTextInfo(mediaFile.path)
                    MediaType.EPUB -> safExtractor.extractEpubInfo(mediaFile.path)
                    MediaType.OFFICE_DOCUMENT -> info
                    MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK, MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> info
                }
                return@withContext result
            } else {
                file = File(mediaFile.path)
                if (!file.exists()) return@withContext info
            }

            var result = when (mediaFile.type) {
                MediaType.IMAGE -> extractImageInfo(file)
                MediaType.GIF -> extractGifInfo(file, mediaFile.size, tempFile != null)
                MediaType.VIDEO, MediaType.AUDIO -> extractVideoAudioInfo(file)
                MediaType.PDF -> documentExtractor.extractPdfInfo(file)
                MediaType.TEXT -> documentExtractor.extractTextInfo(file)
                MediaType.EPUB -> documentExtractor.extractEpubInfo(file)
                MediaType.OFFICE_DOCUMENT -> info
                MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK, MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> info
            }
            
            // For video/audio: if no metadata extracted and file is network, try extended download
            if ((mediaFile.type == MediaType.VIDEO || mediaFile.type == MediaType.AUDIO) && 
                tempFile != null && 
                tempFile.length() == 1024 * 1024L &&
                (result.width == null && result.height == null && result.duration == null || 
                 result.audioCodec == null && mediaFile.type == MediaType.AUDIO)) {
                
                Timber.d("Initial download insufficient for metadata (duration=${result.duration}, audioCodec=${result.audioCodec}), extending to 5MB")
                tempFile.delete()
                
                // Download extended size
                val extendedFile = networkDownloader.downloadToTemp(mediaFile.path, mediaFile.type, mediaFile.size, useExtendedSize = true)
                if (extendedFile != null && extendedFile.exists()) {
                    result = extractVideoAudioInfo(extendedFile)
                    extendedFile.delete()
                }
            }
            
            // Clean up temp file
            tempFile?.delete()
            
            return@withContext result
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract metadata for ${mediaFile.path}")
            info
        }
    }

    private fun extractImageInfo(file: File): DetailedMediaInfo {
        var width: Int? = null
        var height: Int? = null
        var model: String? = null
        var make: String? = null
        var iso: String? = null
        var aperture: String? = null
        var exposure: String? = null
        var focal: String? = null
        var colorSpace: String? = null
        var latitude: Double? = null
        var longitude: Double? = null

        try {
            val exif = ExifInterface(file.absolutePath)
            width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 }
            height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 }
            
            if (width == null || height == null) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, options)
                width = options.outWidth
                height = options.outHeight
            }

            model = exif.getAttribute(ExifInterface.TAG_MODEL)
            make = exif.getAttribute(ExifInterface.TAG_MAKE)
            @Suppress("DEPRECATION")
            iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
            aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
            exposure = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { exp ->
                // Format exposure to 3 decimal places
                exp.toDoubleOrNull()?.let { "%.3f".format(it) } ?: exp
            }
            focal = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
            
            // Extract color space
            val colorSpaceTag = exif.getAttributeInt(ExifInterface.TAG_COLOR_SPACE, -1)
            colorSpace = when (colorSpaceTag) {
                1 -> "sRGB"
                2 -> "Adobe RGB"
                0xFFFF -> "Uncalibrated"
                else -> null
            }
            
            // Extract GPS coordinates
            val latLong = FloatArray(2)
            @Suppress("DEPRECATION")
            if (exif.getLatLong(latLong)) {
                latitude = latLong[0].toDouble()
                longitude = latLong[1].toDouble()
            }
        } catch (e: Exception) {
            Timber.w("Error reading EXIF: ${e.message}")
        }

        return DetailedMediaInfo(
            width = width,
            height = height,
            cameraModel = model,
            cameraMake = make,
            iso = iso,
            aperture = aperture,
            exposureTime = exposure,
            focalLength = focal,
            colorSpace = colorSpace,
            latitude = latitude,
            longitude = longitude
        )
    }

    private fun extractGifInfo(file: File, totalFileSize: Long, isPartialDownload: Boolean): DetailedMediaInfo {
        var width = 0
        var height = 0
        var frameCount = 0
        
        try {
             val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
             BitmapFactory.decodeFile(file.absolutePath, options)
             width = options.outWidth
             height = options.outHeight
             
             // If file is partial download (network files), pass total size for extrapolation
             frameCount = if (isPartialDownload && totalFileSize > file.length()) {
                 GifFrameCounter.countFrames(file, totalFileSize)
             } else {
                 GifFrameCounter.countFrames(file, null)
             }
        } catch (e: Exception) { 
            Timber.w("Error reading GIF info: ${e.message}")
        }
        
        return DetailedMediaInfo(width = width, height = height, gifFrameCount = if (frameCount > 0) frameCount else null)
    }
    
    private fun extractVideoAudioInfo(file: File): DetailedMediaInfo {
        val retriever = MediaMetadataRetriever()
        var width: Int? = null
        var height: Int? = null
        var videoCodec: String? = null
        var audioCodec: String? = null
        var audioChannels: Int? = null
        var audioBitrate: Int? = null
        var duration: Long? = null
        var bitrate: Int? = null
        var frameRate: Double? = null
        var latitude: Double? = null
        var longitude: Double? = null
        var audioTitle: String? = null
        var audioArtist: String? = null
        var audioAlbum: String? = null
        var sampleRate: Int? = null
        
        // Validate file before attempting to read metadata
        if (!file.exists()) {
            Timber.w("File does not exist: ${file.absolutePath}")
            return DetailedMediaInfo()
        }
        
        if (file.length() == 0L) {
            Timber.w("File is empty: ${file.absolutePath}")
            return DetailedMediaInfo()
        }
        
        try {
            retriever.setDataSource(file.absolutePath)
            
            // Extract basic metadata
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (w != null) width = w.toIntOrNull()
            if (h != null) height = h.toIntOrNull()
            
            // Duration in milliseconds
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            duration = durationStr?.toLongOrNull()
            
            // Bitrate in bits per second
            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            bitrate = bitrateStr?.toIntOrNull()
            
            // GPS Location from video metadata
            val location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            if (location != null) {
                // Format: ISO-6709 string like "+37.5090+127.0620/" or "+51.5074-000.1278/"
                try {
                    val coords = location.replace("/", "").trim()
                    // Parse latitude (starts with + or -)
                    val latMatch = Regex("([+-]\\d+\\.\\d+)").find(coords)
                    if (latMatch != null) {
                        latitude = latMatch.value.toDoubleOrNull()
                        // Parse longitude (second coordinate)
                        val lonMatch = Regex("([+-]\\d+\\.\\d+)", RegexOption.MULTILINE)
                            .findAll(coords).elementAtOrNull(1)
                        longitude = lonMatch?.value?.toDoubleOrNull()
                    }
                } catch (e: Exception) {
                    Timber.w("Failed to parse GPS location from video: $location")
                }
            }

            // Extract Title, Artist, Album
            audioTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            audioArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            audioAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
            
            val extractor = MediaExtractor()
            extractor.setDataSource(file.absolutePath)
            
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) {
                    videoCodec = mime
                    if (width == null && format.containsKey(MediaFormat.KEY_WIDTH)) {
                        width = format.getInteger(MediaFormat.KEY_WIDTH)
                    }
                    if (height == null && format.containsKey(MediaFormat.KEY_HEIGHT)) {
                        height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    }
                    // Frame rate
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble()
                    }
                    // Alternative: calculate from duration and frame count
                    if (frameRate == null && duration != null && duration > 0) {
                        try {
                            val frameCount = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull()
                            if (frameCount != null && frameCount > 0) {
                                frameRate = (frameCount * 1000.0) / duration
                            }
                        } catch (e: Exception) {
                            // Frame count not available
                        }
                    }
                } else if (mime?.startsWith("audio/") == true) {
                    audioCodec = mime
                    // Extract audio channels
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        audioChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    // Extract audio bitrate
                    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        audioBitrate = format.getInteger(MediaFormat.KEY_BIT_RATE)
                    }
                }
            }
            extractor.release()
            
        } catch (e: Exception) {
            Timber.w(e, "Error reading video metadata from ${file.absolutePath} (size=${file.length()} bytes). If partial file, metadata may be unavailable - this is normal.")
        } finally {
            retriever.release()
        }
        
        // Log result with note about partial files
        if (width == null && height == null && duration == null) {
            Timber.d("extractVideoAudioInfo: No metadata extracted (file size=${file.length()} bytes). Likely partial file without moov atom.")
        } else {
            Timber.d("extractVideoAudioInfo result: width=$width, height=$height, duration=$duration, videoCodec=$videoCodec, audioCodec=$audioCodec, audioChannels=$audioChannels, audioBitrate=$audioBitrate, bitrate=$bitrate, fps=$frameRate, album=$audioAlbum, sampleRate=$sampleRate")
        }
        
        return DetailedMediaInfo(
            width = width, 
            height = height, 
            videoCodec = videoCodec, 
            audioCodec = audioCodec,
            audioChannels = audioChannels,
            audioBitrate = audioBitrate,
            duration = duration,
            bitrate = bitrate,
            frameRate = frameRate,
            latitude = latitude,
            longitude = longitude,
            audioTitle = audioTitle,
            audioArtist = audioArtist,
            audioAlbum = audioAlbum,
            sampleRate = sampleRate
        )
    }

}
