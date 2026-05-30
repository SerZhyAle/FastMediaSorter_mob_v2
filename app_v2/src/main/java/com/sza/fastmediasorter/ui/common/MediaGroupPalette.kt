package com.sza.fastmediasorter.ui.common

import com.sza.fastmediasorter.domain.model.MediaType

object MediaGroupPalette {
    const val AUDIO_PRIMARY: Int = 0xFFFFA726.toInt()
    const val DOCUMENT_PRIMARY: Int = 0xFF42A5F5.toInt()
    const val VIDEO_PRIMARY: Int = 0xFF9575CD.toInt()
    const val IMAGE_PRIMARY: Int = 0xFF66BB6A.toInt()

    private const val IMAGE_LIGHT: Int = 0xFFA5D6A7.toInt()
    private const val DOCUMENT_LIGHT: Int = 0xFF90CAF9.toInt()
    private const val DOCUMENT_MID: Int = 0xFF64B5F6.toInt()
    private const val DEFAULT: Int = 0xFF9E9E9E.toInt()

    private val documentTypes = setOf(MediaType.TEXT, MediaType.PDF, MediaType.EPUB, MediaType.OFFICE_DOCUMENT)
    private val imageTypes = setOf(MediaType.IMAGE, MediaType.GIF)

    fun colorForType(type: MediaType): Int {
        return when (type) {
            MediaType.AUDIO -> AUDIO_PRIMARY
            MediaType.VIDEO -> VIDEO_PRIMARY
            MediaType.IMAGE -> IMAGE_PRIMARY
            MediaType.GIF -> IMAGE_LIGHT
            MediaType.TEXT -> DOCUMENT_LIGHT
            MediaType.PDF -> DOCUMENT_PRIMARY
            MediaType.EPUB -> DOCUMENT_MID
            MediaType.OFFICE_DOCUMENT -> DOCUMENT_MID
            else -> DEFAULT
        }
    }

    fun colorForSingleCategory(types: Set<MediaType>): Int? {
        return when {
            types == setOf(MediaType.AUDIO) -> AUDIO_PRIMARY
            types == setOf(MediaType.VIDEO) -> VIDEO_PRIMARY
            types.isNotEmpty() && types.all { it in imageTypes } -> IMAGE_PRIMARY
            types.isNotEmpty() && types.all { it in documentTypes } -> DOCUMENT_PRIMARY
            else -> null
        }
    }
}
