package com.sza.fastmediasorter_v2.domain.usecase

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.sza.fastmediasorter_v2.domain.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

/**
 * EXIF metadata extracted from image files
 */
data class ExifMetadata(
    val orientation: Int? = null, // ExifInterface.TAG_ORIENTATION value (1-8)
    val dateTime: Long? = null, // Photo capture timestamp (milliseconds)
    val latitude: Double? = null, // GPS latitude
    val longitude: Double? = null // GPS longitude
)

/**
 * Use case to extract EXIF metadata from image files
 * Runs on IO dispatcher to avoid blocking UI thread
 */
class ExtractExifMetadataUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tag = "ExtractExifMetadataUC"

    /**
     * Extract EXIF metadata from local file
     * @param filePath Absolute file path
     * @return ExifMetadata with extracted fields or nulls if extraction failed
     */
    suspend fun extractFromFile(filePath: String): ExifMetadata = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) {
                Log.w(tag, "File does not exist or not readable: $filePath")
                return@withContext ExifMetadata()
            }

            val exif = ExifInterface(filePath)
            extractMetadata(exif)
        } catch (e: IOException) {
            Log.e(tag, "Failed to read EXIF from file: $filePath", e)
            ExifMetadata()
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error reading EXIF: $filePath", e)
            ExifMetadata()
        }
    }

    /**
     * Extract EXIF metadata from InputStream (for network files)
     * @param inputStream InputStream of the image file
     * @return ExifMetadata with extracted fields or nulls if extraction failed
     */
    suspend fun extractFromStream(inputStream: InputStream): ExifMetadata = withContext(Dispatchers.IO) {
        try {
            val exif = ExifInterface(inputStream)
            extractMetadata(exif)
        } catch (e: IOException) {
            Log.e(tag, "Failed to read EXIF from stream", e)
            ExifMetadata()
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error reading EXIF from stream", e)
            ExifMetadata()
        }
    }

    /**
     * Extract EXIF metadata from Uri (content:// or file://)
     * @param uri Content Uri of the image
     * @return ExifMetadata with extracted fields or nulls if extraction failed
     */
    suspend fun extractFromUri(uri: Uri): ExifMetadata = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                extractMetadata(exif)
            } ?: run {
                Log.w(tag, "Failed to open input stream for uri: $uri")
                ExifMetadata()
            }
        } catch (e: IOException) {
            Log.e(tag, "Failed to read EXIF from uri: $uri", e)
            ExifMetadata()
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error reading EXIF from uri: $uri", e)
            ExifMetadata()
        }
    }

    /**
     * Common method to extract metadata from ExifInterface
     */
    private fun extractMetadata(exif: ExifInterface): ExifMetadata {
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        ).takeIf { it != ExifInterface.ORIENTATION_UNDEFINED }

        val dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME)?.let { dateStr ->
            try {
                // Parse EXIF datetime format: "YYYY:MM:DD HH:MM:SS"
                val format = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
                format.parse(dateStr)?.time
            } catch (e: Exception) {
                Log.w(tag, "Failed to parse EXIF datetime: $dateStr", e)
                null
            }
        }

        // Extract GPS coordinates
        val latLong = FloatArray(2)
        val hasGps = exif.getLatLong(latLong)
        val latitude = if (hasGps) latLong[0].toDouble() else null
        val longitude = if (hasGps) latLong[1].toDouble() else null

        return ExifMetadata(
            orientation = orientation,
            dateTime = dateTime,
            latitude = latitude,
            longitude = longitude
        )
    }

    /**
     * Check if file type supports EXIF metadata
     */
    fun supportsExif(mediaType: MediaType): Boolean {
        return mediaType == MediaType.IMAGE
    }

    /**
     * Check if file extension typically supports EXIF
     */
    fun supportsExif(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in setOf("jpg", "jpeg", "png", "webp", "heic", "heif")
    }
}
