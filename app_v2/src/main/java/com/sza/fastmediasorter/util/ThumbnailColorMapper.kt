package com.sza.fastmediasorter.util

import android.graphics.Color
import com.sza.fastmediasorter.domain.model.MediaType

object ThumbnailColorMapper {

    private val extensionColorMap: Map<String, Int> = mapOf(
        // Audio (yellow/orange shades)
        "mp3" to 0xFFFFA726.toInt(),
        "flac" to 0xFFFFF59D.toInt(),
        "wav" to 0xFFFFD54F.toInt(),
        "ogg" to 0xFFFFB74D.toInt(),
        "m4a" to 0xFFFFE082.toInt(),

        // Documents (blue shades)
        "txt" to 0xFF90CAF9.toInt(),
        "pdf" to 0xFF42A5F5.toInt(),
        "epub" to 0xFF64B5F6.toInt(),
        "doc" to 0xFF2196F3.toInt(),
        "docx" to 0xFF2196F3.toInt(),
        "rtf" to 0xFFBBDEFB.toInt(),

        // Images (green shades)
        "jpg" to 0xFF81C784.toInt(),
        "jpeg" to 0xFF81C784.toInt(),
        "png" to 0xFF66BB6A.toInt(),
        "gif" to 0xFFA5D6A7.toInt(),
        "bmp" to 0xFFC8E6C9.toInt(),
        "webp" to 0xFF4CAF50.toInt(),

        // Video (purple shades)
        "mp4" to 0xFF9575CD.toInt(),
        "avi" to 0xFF7E57C2.toInt(),
        "mkv" to 0xFFB39DDB.toInt(),
        "mov" to 0xFF673AB7.toInt(),
        "webm" to 0xFFD1C4E9.toInt(),

        // Archives (gray shades)
        "zip" to 0xFFBDBDBD.toInt(),
        "rar" to 0xFF9E9E9E.toInt(),
        "7z" to 0xFFE0E0E0.toInt(),
        "tar" to 0xFF757575.toInt(),

        // Binary/executable shades
        "exe" to 0xFFE57373.toInt(),
        "apk" to 0xFFEF5350.toInt(),
        "bin" to 0xFFFFCDD2.toInt()
    )

    private const val DEFAULT_COLOR: Int = 0xFFEEEEEE.toInt()
    private const val DARK_TEXT: Int = 0xFF424242.toInt()

    fun getColorForExtension(extension: String): Int {
        return extensionColorMap[extension.lowercase()] ?: DEFAULT_COLOR
    }

    fun getColorForFile(fileName: String): Int {
        val extension = fileName.substringAfterLast('.', "")
        return getColorForExtension(extension)
    }

    fun getColorForMediaType(mediaType: MediaType): Int {
        return when (mediaType) {
            MediaType.IMAGE, MediaType.GIF -> 0xFF81C784.toInt()
            MediaType.VIDEO -> 0xFF9575CD.toInt()
            MediaType.TEXT, MediaType.PDF, MediaType.EPUB, MediaType.OFFICE_DOCUMENT -> 0xFF42A5F5.toInt()
            MediaType.AUDIO -> 0xFFFFA726.toInt()
            MediaType.BINARY_ARCHIVE,
            MediaType.BINARY_DISK,
            MediaType.BINARY_EXECUTABLE,
            MediaType.BINARY_OTHER -> 0xFFBDBDBD.toInt()
        }
    }

    fun getContrastingTextColor(backgroundColor: Int): Int {
        val red = Color.red(backgroundColor)
        val green = Color.green(backgroundColor)
        val blue = Color.blue(backgroundColor)
        val brightness = (red * 299 + green * 587 + blue * 114) / 1000
        return if (brightness > 128) DARK_TEXT else Color.WHITE
    }

    fun darken(color: Int, factor: Float = 0.18f): Int {
        val red = (Color.red(color) * (1f - factor)).toInt().coerceIn(0, 255)
        val green = (Color.green(color) * (1f - factor)).toInt().coerceIn(0, 255)
        val blue = (Color.blue(color) * (1f - factor)).toInt().coerceIn(0, 255)
        return Color.argb(Color.alpha(color), red, green, blue)
    }

    fun lighten(color: Int, factor: Float = 0.12f): Int {
        val red = (Color.red(color) + (255 - Color.red(color)) * factor).toInt().coerceIn(0, 255)
        val green = (Color.green(color) + (255 - Color.green(color)) * factor).toInt().coerceIn(0, 255)
        val blue = (Color.blue(color) + (255 - Color.blue(color)) * factor).toInt().coerceIn(0, 255)
        return Color.argb(Color.alpha(color), red, green, blue)
    }
}
