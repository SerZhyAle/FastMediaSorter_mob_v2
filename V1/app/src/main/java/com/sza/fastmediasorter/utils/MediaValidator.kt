package com.sza.fastmediasorter.utils

import com.sza.fastmediasorter.network.SmbClient
import jcifs.smb.SmbFile

/**
 * Utility for quick media file validation
 * Detects corrupted/malformed video and image files before attempting to load
 */
object MediaValidator {
    private const val TAG = "MediaValidator"

    /**
     * Result of media validation
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorType: String? = null,
        val errorDetails: String? = null,
        val recommendation: String? = null,
    )

    /**
     * Validate SMB media file (video or image) by reading header
     * Returns quickly (< 100ms) without full file parsing
     */
    suspend fun validateSmbMedia(
        mediaUrl: String,
        smbClient: SmbClient?,
        isVideo: Boolean,
    ): ValidationResult {
        if (smbClient == null) {
            return ValidationResult(true) // Can't validate, assume valid
        }

        val smbContext = smbClient.getContext()
        if (smbContext == null) {
            Logger.w(TAG, "SMB context is null, skipping validation")
            return ValidationResult(true) // Can't validate without context, assume valid
        }

        val extension = mediaUrl.substringAfterLast('.', "").lowercase()

        return try {
            val smbFile = SmbFile(mediaUrl, smbContext)

            // Check if file exists and is readable
            if (!smbFile.exists()) {
                return ValidationResult(
                    isValid = false,
                    errorType = "File Not Found",
                    errorDetails = "File does not exist on server",
                    recommendation = "Check if file was deleted or moved",
                )
            }

            if (!smbFile.canRead()) {
                return ValidationResult(
                    isValid = false,
                    errorType = "Access Denied",
                    errorDetails = "No read permission for this file",
                    recommendation = "Check SMB share permissions",
                )
            }

            val fileSize = smbFile.length()

            // Validate based on file type and extension
            if (isVideo) {
                validateVideoFormat(smbFile, fileSize, extension)
            } else {
                validateImageFormat(smbFile, fileSize, extension)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error validating media: ${e.message}", e)
            ValidationResult(
                isValid = false,
                errorType = "Validation Error",
                errorDetails = e.message ?: "Unknown error",
                recommendation = "File may be corrupted or inaccessible",
            )
        }
    }

    /**
     * Validate video format based on extension
     */
    private fun validateVideoFormat(
        smbFile: SmbFile,
        fileSize: Long,
        extension: String,
    ): ValidationResult {
        return when (extension) {
            "mp4", "m4v" -> validateMp4Header(smbFile, fileSize)
            "mkv" -> validateMkvHeader(smbFile, fileSize)
            "mov" -> validateMovHeader(smbFile, fileSize)
            "webm" -> validateWebmHeader(smbFile, fileSize)
            "3gp" -> validate3gpHeader(smbFile, fileSize)
            else -> ValidationResult(true) // Unknown format, let player try
        }
    }

    /**
     * Validate image format based on extension
     */
    private fun validateImageFormat(
        smbFile: SmbFile,
        fileSize: Long,
        extension: String,
    ): ValidationResult {
        return when (extension) {
            "jpg", "jpeg" -> validateJpegHeader(smbFile, fileSize)
            "png" -> validatePngHeader(smbFile, fileSize)
            "gif" -> validateGifHeader(smbFile, fileSize)
            "bmp" -> validateBmpHeader(smbFile, fileSize)
            "webp" -> validateWebpHeader(smbFile, fileSize)
            else -> ValidationResult(true) // Unknown format, let viewer try
        }
    }

    /**
     * Validate MP4 file header
     * MP4 format: starts with ftyp box
     */
    private fun validateMp4Header(
        smbFile: SmbFile,
        fileSize: Long,
    ): ValidationResult {
        return try {
            val inputStream = smbFile.inputStream
            val header = ByteArray(64)
            val bytesRead = inputStream.read(header)
            inputStream.close()

            if (bytesRead < 8) {
                return ValidationResult(
                    isValid = false,
                    errorType = "MP4 File Truncated",
                    errorDetails = "File too small ($bytesRead bytes)",
                    recommendation = "File is damaged or incomplete",
                )
            }

            // Check for ftyp box (offset 4-7)
            val boxType = String(header.sliceArray(4..7), Charsets.US_ASCII)
            if (boxType != "ftyp") {
                return ValidationResult(
                    isValid = false,
                    errorType = "Invalid MP4 Header",
                    errorDetails = "Missing 'ftyp' box (found: '$boxType')",
                    recommendation = "File is not a valid MP4",
                )
            }

            ValidationResult(isValid = true)
        } catch (e: Exception) {
            ValidationResult(
                isValid = false,
                errorType = "Header Read Error",
                errorDetails = e.message ?: "Cannot read file header",
            )
        }
    }

    /**
     * Validate MKV file header
     * MKV format: starts with EBML signature
     */
    private fun validateMkvHeader(
        smbFile: SmbFile,
        fileSize: Long,
    ): ValidationResult {
        return try {
            val inputStream = smbFile.inputStream
            val header = ByteArray(32)
            val bytesRead = inputStream.read(header)
            inputStream.close()

            if (bytesRead < 4) {
                return ValidationResult(
                    isValid = false,
                    errorType = "MKV File Truncated",
                    errorDetails = "File too small ($bytesRead bytes)",
                    recommendation = "File is damaged or incomplete",
                )
            }

            // Check EBML signature (0x1A45DFA3)
            if (header[0] != 0x1A.toByte() ||
                header[1] != 0x45.toByte() ||
                header[2] != 0xDF.toByte() ||
                header[3] != 0xA3.toByte()
            ) {
                return ValidationResult(
                    isValid = false,
                    errorType = "Invalid MKV Header",
                    errorDetails = "Missing EBML signature",
                    recommendation = "File is not a valid MKV",
                )
            }

            ValidationResult(isValid = true)
        } catch (e: Exception) {
            ValidationResult(
                isValid = false,
                errorType = "Header Read Error",
                errorDetails = e.message ?: "Cannot read file header",
            )
        }
    }

    /**
     * Validate MOV file header (QuickTime format)
     */
    private fun validateMovHeader(
        smbFile: SmbFile,
        fileSize: Long,
    ): ValidationResult {
        return try {
            val inputStream = smbFile.inputStream
            val header = ByteArray(64)
            val bytesRead = inputStream.read(header)
            inputStream.close()

            if (bytesRead < 8) {
                return ValidationResult(
                    isValid = false,
                    errorType = "MOV File Truncated",
                    errorDetails = "File too small ($bytesRead bytes)",
                )
            }

            // MOV uses same structure as MP4 (ftyp box)
            val boxType = String(header.sliceArray(4..7), Charsets.US_ASCII)
            if (boxType != "ftyp" && boxType != "mdat" && boxType != "wide") {
                return ValidationResult(
                    isValid = false,
                    errorType = "Invalid MOV Header",
                    errorDetails = "Missing expected QuickTime box (found: '$boxType')",
                )
            }

            ValidationResult(isValid = true)
        } catch (e: Exception) {
            ValidationResult(isValid = false, errorType = "Header Read Error", errorDetails = e.message)
        }
    }

    /**
     * Validate WebM file header (Matroska-based)
     */
    private fun validateWebmHeader(
        smbFile: SmbFile,
        fileSize: Long,
    ): ValidationResult {
        // WebM uses same EBML signature as MKV
        return validateMkvHeader(smbFile, fileSize)
    }

    /**
     * Validate 3GP file header (mobile video format)
     */
    private fun validate3gpHeader(
        smbFile: SmbFile,
        fileSize: Long,
    ): ValidationResult {
        return try {
            val inputStream = smbFile.inputStream
            val header = ByteArray(32)
            val bytesRead = inputStream.read(header)
            inputStream.close()

            if (bytesRead < 12) {
                return ValidationResult(
                    isValid = false,
                    errorType = "3GP File Truncated",
                    errorDetails = "File too small ($bytesRead bytes)",
                )
            }

            // Check ftyp box
            val boxType = String(header.sliceArray(4..7), Charsets.US_ASCII)
            if (boxType != "ftyp") {
                return ValidationResult(
                    isValid = false,
                    errorType = "Invalid 3GP Header",
                    errorDetails = "Missing 'ftyp' box",
                )
            }

            // Check brand (3gp4, 3gp5, 3gp6, etc.)
            val brand = String(header.sliceArray(8..11), Charsets.US_ASCII)
            if (!brand.startsWith("3gp") && !brand.startsWith("3g2")) {
                return ValidationResult(
                    isValid = false,
                    errorType = "Invalid 3GP Format",
                    errorDetails = "Unexpected brand: '$brand'",
                )
            }

            ValidationResult(isValid = true)
        } catch (e: Exception) {
            ValidationResult(isValid = false, errorType = "Header Read Error", errorDetails = e.message)
        }
    }

    // ==================== IMAGE VALIDATORS ====================

    /**
     * Validate JPEG file header
     */
    private fun validateJpegHeader(
        smbFile: SmbFile,
        fileSize: Long,
    ): ValidationResult {
        return try {
            val inputStream = smbFile.inputStream
            val header = ByteArray(12)
            val bytesRead = inputStream.read(header)
            inputStream.close()

            if (bytesRead < 2) {
                return ValidationResult(
                    isValid = false,
                    errorType = "JPEG File Truncated",
                    errorDetails = "File too small ($bytesRead bytes), minimum 2 bytes required",
                )
            }

            // Check JPEG SOI marker (0xFFD8)
            if (header[0] != 0xFF.toByte() || header[1] != 0xD8.toByte()) {
                return ValidationResult(
                    isValid = false,
                    errorType = "Invalid JPEG Header",
                    errorDetails = "Missing SOI marker (0xFFD8), found: 0x${header[0].toString(
                        16,
                    )}${header[1].toString(16)}",
                )
            }

            // Check if file ends with EOI marker (requires reading last 2 bytes - expensive for large files)
            // Skip this check for performance

            ValidationResult(isValid = true)
        } catch (e: Exception) {
            ValidationResult(isValid = false, errorType = "Header Read Error", errorDetails = e.message)
        }
    }

    /**
     * Validate PNG file header
     */
    private fun validatePngHeader(
        smbFile: SmbFile,
        fileSize: Long,
    ): ValidationResult {
        return try {
            val inputStream = smbFile.inputStream
            val header = ByteArray(8)
            val bytesRead = inputStream.read(header)
            inputStream.close()

            if (bytesRead < 8) {
                return ValidationResult(
                    isValid = false,
                    errorType = "PNG File Truncated",
                    errorDetails = "File too small ($bytesRead bytes), minimum 8 bytes required",
                )
            }

            // Check PNG signature: 137 80 78 71 13 10 26 10
            val expectedSignature =
                byteArrayOf(
                    0x89.toByte(),
                    0x50,
                    0x4E,
                    0x47,
                    0x0D,
                    0x0A,
                    0x1A,
                    0x0A,
                )

            for (i in 0..7) {
                if (header[i] != expectedSignature[i]) {
                    return ValidationResult(
                        isValid = false,
                        errorType = "Invalid PNG Header",
                        errorDetails = "Incorrect PNG signature at byte $i",
                    )
                }
            }

            ValidationResult(isValid = true)
        } catch (e: Exception) {
            ValidationResult(isValid = false, errorType = "Header Read Error", errorDetails = e.message)
        }
    }

    /**
     * Validate GIF file header
     */
    private fun validateGifHeader(
        smbFile: SmbFile,
        fileSize: Long,
    ): ValidationResult {
        return try {
            val inputStream = smbFile.inputStream
            val header = ByteArray(6)
            val bytesRead = inputStream.read(header)
            inputStream.close()

            if (bytesRead < 6) {
                return ValidationResult(
                    isValid = false,
                    errorType = "GIF File Truncated",
                    errorDetails = "File too small ($bytesRead bytes), minimum 6 bytes required",
                )
            }

            // Check GIF signature: "GIF87a" or "GIF89a"
            val signature = String(header, Charsets.US_ASCII)
            if (signature != "GIF87a" && signature != "GIF89a") {
                return ValidationResult(
                    isValid = false,
                    errorType = "Invalid GIF Header",
                    errorDetails = "Expected 'GIF87a' or 'GIF89a', found: '$signature'",
                )
            }

            ValidationResult(isValid = true)
        } catch (e: Exception) {
            ValidationResult(isValid = false, errorType = "Header Read Error", errorDetails = e.message)
        }
    }

    /**
     * Validate BMP file header
     */
    private fun validateBmpHeader(
        smbFile: SmbFile,
        fileSize: Long,
    ): ValidationResult {
        return try {
            val inputStream = smbFile.inputStream
            val header = ByteArray(14)
            val bytesRead = inputStream.read(header)
            inputStream.close()

            if (bytesRead < 14) {
                return ValidationResult(
                    isValid = false,
                    errorType = "BMP File Truncated",
                    errorDetails = "File too small ($bytesRead bytes), minimum 14 bytes required",
                )
            }

            // Check BMP signature: "BM"
            if (header[0] != 0x42.toByte() || header[1] != 0x4D.toByte()) {
                return ValidationResult(
                    isValid = false,
                    errorType = "Invalid BMP Header",
                    errorDetails = "Missing 'BM' signature",
                )
            }

            // Read file size from header (bytes 2-5, little-endian)
            val declaredSize =
                (
                    (header[2].toInt() and 0xFF) or
                        ((header[3].toInt() and 0xFF) shl 8) or
                        ((header[4].toInt() and 0xFF) shl 16) or
                        ((header[5].toInt() and 0xFF) shl 24)
                ).toLong()

            if (Math.abs(declaredSize - fileSize) > 1024) {
                return ValidationResult(
                    isValid = false,
                    errorType = "BMP File Corrupted",
                    errorDetails = "Size mismatch: header declares $declaredSize bytes, actual file is $fileSize bytes",
                )
            }

            ValidationResult(isValid = true)
        } catch (e: Exception) {
            ValidationResult(isValid = false, errorType = "Header Read Error", errorDetails = e.message)
        }
    }

    /**
     * Validate WebP file header
     */
    private fun validateWebpHeader(
        smbFile: SmbFile,
        fileSize: Long,
    ): ValidationResult {
        return try {
            val inputStream = smbFile.inputStream
            val header = ByteArray(16)
            val bytesRead = inputStream.read(header)
            inputStream.close()

            if (bytesRead < 12) {
                return ValidationResult(
                    isValid = false,
                    errorType = "WebP File Truncated",
                    errorDetails = "File too small ($bytesRead bytes), minimum 12 bytes required",
                )
            }

            // Check RIFF signature
            val riffSignature = String(header.sliceArray(0..3), Charsets.US_ASCII)
            if (riffSignature != "RIFF") {
                return ValidationResult(
                    isValid = false,
                    errorType = "Invalid WebP Header",
                    errorDetails = "Missing RIFF signature",
                )
            }

            // Check WEBP signature at offset 8
            val webpSignature = String(header.sliceArray(8..11), Charsets.US_ASCII)
            if (webpSignature != "WEBP") {
                return ValidationResult(
                    isValid = false,
                    errorType = "Invalid WebP Format",
                    errorDetails = "Missing 'WEBP' signature",
                )
            }

            ValidationResult(isValid = true)
        } catch (e: Exception) {
            ValidationResult(isValid = false, errorType = "Header Read Error", errorDetails = e.message)
        }
    }
}
