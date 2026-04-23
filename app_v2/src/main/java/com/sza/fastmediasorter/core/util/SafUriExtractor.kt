package com.sza.fastmediasorter.core.util

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import timber.log.Timber

/**
 * Helper class for extracting metadata from SAF (Storage Access Framework) URIs.
 * Handles content:// URIs from document providers, photo pickers, etc.
 */
class SafUriExtractor(private val context: Context) {
    
    /**
     * Extract image metadata from content:// URI
     */
    fun extractImageInfo(uriPath: String): DetailedMediaInfo {
        try {
            val normalizedUri = if (uriPath.startsWith("content://")) uriPath 
                               else uriPath.replaceFirst("content:/", "content://")
            val uri = android.net.Uri.parse(normalizedUri)
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                
                var width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 }
                var height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 }
                
                if (width == null || height == null) {
                    // Try BitmapFactory as fallback
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeStream(stream, null, options)
                        width = options.outWidth
                        height = options.outHeight
                    }
                }
                
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)
                val make = exif.getAttribute(ExifInterface.TAG_MAKE)
                @Suppress("DEPRECATION")
                val iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                val aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
                val exposure = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { exp ->
                    exp.toDoubleOrNull()?.let { "%.3f".format(it) } ?: exp
                }
                val focal = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
                
                // Extract color space
                val colorSpaceTag = exif.getAttributeInt(ExifInterface.TAG_COLOR_SPACE, -1)
                val colorSpace = when (colorSpaceTag) {
                    1 -> "sRGB"
                    2 -> "Adobe RGB"
                    0xFFFF -> "Uncalibrated"
                    else -> null
                }
                
                val latLong = FloatArray(2)
                @Suppress("DEPRECATION")
                val hasLatLong = exif.getLatLong(latLong)
                val latitude = if (hasLatLong) latLong[0].toDouble() else null
                val longitude = if (hasLatLong) latLong[1].toDouble() else null
                
                val cameraModel = if (make != null && model != null) {
                    "$make $model"
                } else {
                    model ?: make
                }
                
                return DetailedMediaInfo(
                    width = width,
                    height = height,
                    cameraModel = cameraModel,
                    cameraMake = make,
                    iso = iso,
                    aperture = aperture,
                    exposureTime = exposure,
                    focalLength = focal,
                    colorSpace = colorSpace,
                    latitude = latitude,
                    longitude = longitude
                )
            } ?: return DetailedMediaInfo()
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract image info from SAF URI: $uriPath")
            return DetailedMediaInfo()
        }
    }
    
    /**
     * Extract GIF metadata from content:// URI
     */
    fun extractGifInfo(uriPath: String): DetailedMediaInfo {
        try {
            val normalizedUri = if (uriPath.startsWith("content://")) uriPath 
                               else uriPath.replaceFirst("content:/", "content://")
            val uri = android.net.Uri.parse(normalizedUri)
            
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, options)
                
                return DetailedMediaInfo(
                    width = options.outWidth,
                    height = options.outHeight
                )
            } ?: return DetailedMediaInfo()
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract GIF info from SAF URI: $uriPath")
            return DetailedMediaInfo()
        }
    }
    
    /**
     * Extract video/audio metadata from content:// URI
     */
    fun extractVideoAudioInfo(uriPath: String): DetailedMediaInfo {
        val retriever = MediaMetadataRetriever()
        try {
            val normalizedUri = if (uriPath.startsWith("content://")) uriPath 
                               else uriPath.replaceFirst("content:/", "content://")
            val uri = android.net.Uri.parse(normalizedUri)
            
            retriever.setDataSource(context, uri)
            
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
            val audioTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val audioArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val audioAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
            
            var videoCodec: String? = null
            var audioCodec: String? = null
            var audioChannels: Int? = null
            var audioBitrate: Int? = null
            var frameRate: Double? = null
            
            // Use MediaExtractor for detailed codec info
            try {
                val extractor = MediaExtractor()
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    extractor.setDataSource(pfd.fileDescriptor)
                    
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                        
                        when {
                            mime.startsWith("video/") -> {
                                videoCodec = mime.substringAfter("video/").uppercase()
                                if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                                    frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble()
                                }
                            }
                            mime.startsWith("audio/") -> {
                                audioCodec = mime.substringAfter("audio/").uppercase()
                                if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                    audioChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                }
                                if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                                    audioBitrate = format.getInteger(MediaFormat.KEY_BIT_RATE)
                                }
                            }
                        }
                    }
                    extractor.release()
                }
            } catch (e: Exception) {
                Timber.w(e, "MediaExtractor failed for SAF URI, using basic metadata only")
            }
            
            return DetailedMediaInfo(
                width = width,
                height = height,
                duration = duration,
                bitrate = bitrate,
                videoCodec = videoCodec,
                audioCodec = audioCodec,
                audioChannels = audioChannels,
                audioBitrate = audioBitrate,
                frameRate = frameRate,
                audioTitle = audioTitle,
                audioArtist = audioArtist,
                audioAlbum = audioAlbum,
                sampleRate = sampleRate
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract video/audio info from SAF URI: $uriPath")
            return DetailedMediaInfo()
        } finally {
            retriever.release()
        }
    }
    
    /**
     * Extract PDF metadata from content:// URI.
     *
     * Page count via PdfRenderer, Info-dict fields via PdfInfoParser. We open the URI twice
     * (once for PdfRenderer's ParcelFileDescriptor, once for an InputStream to the parser)
     * because PdfRenderer consumes the descriptor exclusively.
     */
    fun extractPdfInfo(uriPath: String): DetailedMediaInfo {
        val uri = android.net.Uri.parse(uriPath)

        val pageCount: Int? = try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                val pdfRenderer = android.graphics.pdf.PdfRenderer(pfd)
                val count = pdfRenderer.pageCount
                pdfRenderer.close()
                pfd.close()
                count
            } else null
        } catch (e: Exception) {
            Timber.w(e, "Failed to read PDF page count from URI: $uriPath")
            null
        }

        val info = try {
            context.contentResolver.openInputStream(uri)?.use { PdfInfoParser.parse(it) }
                ?: PdfInfoParser.PdfInfo()
        } catch (e: Exception) {
            Timber.w(e, "Failed to read PDF info dict from URI: $uriPath")
            PdfInfoParser.PdfInfo()
        }

        return DetailedMediaInfo(
            pageCount = pageCount,
            docTitle = info.title,
            docAuthor = info.author,
            pdfVersion = info.version,
            pdfCreator = info.creator,
            pdfProducer = info.producer,
            pdfSubject = info.subject,
            pdfKeywords = info.keywords,
            pdfCreationDate = info.creationDate,
            pdfModificationDate = info.modificationDate
        )
    }
    
    /**
     * Extract text file metadata from content:// URI
     */
    fun extractTextInfo(uriPath: String): DetailedMediaInfo {
        return try {
            val uri = android.net.Uri.parse(uriPath)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return DetailedMediaInfo()
            val text = inputStream.bufferedReader().readText()
            inputStream.close()
            
            val lines = text.lines().size
            val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
            val chars = text.length
            
            DetailedMediaInfo(
                lineCount = lines,
                wordCount = words,
                charCount = chars,
                encoding = "UTF-8"
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to extract TXT info from URI: $uriPath")
            DetailedMediaInfo()
        }
    }
    
    /**
     * Extract EPUB metadata from content:// URI
     */
    fun extractEpubInfo(uriPath: String): DetailedMediaInfo {
        return try {
            val uri = android.net.Uri.parse(uriPath)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return DetailedMediaInfo()
            
            val epubReader = io.documentnode.epub4j.epub.EpubReader()
            val book = epubReader.readEpub(inputStream)
            inputStream.close()
            
            val title = book.metadata?.titles?.firstOrNull()
            val author = book.metadata?.authors?.firstOrNull()?.let { 
                "${it.firstname ?: ""} ${it.lastname ?: ""}".trim()
            }?.ifBlank { null }
            val chapterCount = book.spine?.spineReferences?.size ?: book.tableOfContents?.tocReferences?.size ?: 0
            
            DetailedMediaInfo(
                docTitle = title,
                docAuthor = author,
                chapterCount = if (chapterCount > 0) chapterCount else null
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to extract EPUB info from URI: $uriPath")
            DetailedMediaInfo()
        }
    }
}
