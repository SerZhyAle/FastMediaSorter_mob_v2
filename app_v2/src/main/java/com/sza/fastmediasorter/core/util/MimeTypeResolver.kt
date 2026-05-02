package com.sza.fastmediasorter.core.util

import android.webkit.MimeTypeMap

object MimeTypeResolver {

    fun resolve(extension: String?, headBytesProvider: (() -> ByteArray?)? = null): String {
        if (!extension.isNullOrEmpty()) {
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            if (mime != null) return mime
        }
        if (headBytesProvider != null) {
            val bytes = headBytesProvider() ?: return "application/octet-stream"
            probeSignature(bytes)?.let { return it }
        }
        return "application/octet-stream"
    }

    private fun probeSignature(bytes: ByteArray): String? {
        fun at(offset: Int, vararg values: Int): Boolean {
            if (bytes.size < offset + values.size) return false
            return values.indices.all { i -> bytes[offset + i].toInt() and 0xFF == values[i] }
        }
        return when {
            at(0, 0xFF, 0xD8, 0xFF) -> "image/jpeg"
            at(0, 0x89, 0x50, 0x4E, 0x47) -> "image/png"
            at(0, 0x47, 0x49, 0x46) -> "image/gif"
            at(0, 0x25, 0x50, 0x44, 0x46) -> "application/pdf"
            at(0, 0x50, 0x4B, 0x03, 0x04) -> "application/zip"
            at(4, 0x66, 0x74, 0x79, 0x70) -> "video/mp4"
            at(0, 0x49, 0x44, 0x33) -> "audio/mpeg"
            at(0, 0xFF, 0xFB) -> "audio/mpeg"
            at(0, 0xFF, 0xFA) -> "audio/mpeg"
            at(0, 0x66, 0x4C, 0x61, 0x43) -> "audio/flac"
            at(0, 0x4F, 0x67, 0x67, 0x53) -> "audio/ogg"
            at(0, 0x52, 0x49, 0x46, 0x46) -> "audio/wav"
            at(0, 0x42, 0x4D) -> "image/bmp"
            else -> null
        }
    }
}
