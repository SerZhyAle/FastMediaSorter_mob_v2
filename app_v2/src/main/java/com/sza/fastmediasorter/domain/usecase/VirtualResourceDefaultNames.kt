package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.LocalMediaScanner.Companion.VIRTUAL_PATH_ALL_AUDIO
import com.sza.fastmediasorter.data.local.LocalMediaScanner.Companion.VIRTUAL_PATH_ALL_DOCS
import com.sza.fastmediasorter.data.local.LocalMediaScanner.Companion.VIRTUAL_PATH_ALL_IMAGES
import com.sza.fastmediasorter.data.local.LocalMediaScanner.Companion.VIRTUAL_PATH_ALL_VIDEO
import com.sza.fastmediasorter.data.local.LocalMediaScanner.Companion.VIRTUAL_PATH_CAMERA_PHOTOS
import com.sza.fastmediasorter.data.local.LocalMediaScanner.Companion.VIRTUAL_PATH_RECENT

/** Hardcoded lookup table: virtual path × language code → (name, comment). */
object VirtualResourceDefaultNames {

    data class Entry(val name: String, val comment: String)

    /** virtualPath → langCode → Entry. Covers all 6 predefined paths × 3 languages. */
    val TABLE: Map<String, Map<String, Entry>> = mapOf(
        VIRTUAL_PATH_RECENT to mapOf(
            "en" to Entry("Recent Media", "Recently accessed local media files"),
            "ru" to Entry("Недавние медиа", "Недавно открытые локальные медиафайлы"),
            "uk" to Entry("Нещодавні медіа", "Нещодавно відкриті локальні медіафайли")
        ),
        VIRTUAL_PATH_ALL_AUDIO to mapOf(
            "en" to Entry("All Music", "Local audio files (mp3, flac, ogg, aac…)"),
            "ru" to Entry("Вся музыка", "Локальные аудиофайлы (mp3, flac, ogg, aac…)"),
            "uk" to Entry("Вся музика", "Локальні аудіофайли (mp3, flac, ogg, aac…)")
        ),
        VIRTUAL_PATH_ALL_VIDEO to mapOf(
            "en" to Entry("All Videos", "Local video files (mp4, mkv, mov, wmv…)"),
            "ru" to Entry("Все видео", "Локальные видеофайлы (mp4, mkv, mov, wmv…)"),
            "uk" to Entry("Усі відео", "Локальні відеофайли (mp4, mkv, mov, wmv…)")
        ),
        VIRTUAL_PATH_ALL_IMAGES to mapOf(
            "en" to Entry("All Images", "Local image files (jpg, png, gif, webp…)"),
            "ru" to Entry("Все изображения", "Локальные изображения (jpg, png, gif, webp…)"),
            "uk" to Entry("Усі зображення", "Локальні зображення (jpg, png, gif, webp…)")
        ),
        VIRTUAL_PATH_ALL_DOCS to mapOf(
            "en" to Entry("All Documents", "Local documents (pdf, epub, txt…)"),
            "ru" to Entry("Все документы", "Локальные документы (pdf, epub, txt…)"),
            "uk" to Entry("Усі документи", "Локальні документи (pdf, epub, txt…)")
        ),
        VIRTUAL_PATH_CAMERA_PHOTOS to mapOf(
            "en" to Entry("Camera Photos", "Photos and videos from the device camera"),
            "ru" to Entry("Фото с камеры", "Фото и видео с камеры устройства"),
            "uk" to Entry("Фото з камери", "Фото та відео з камери пристрою")
        )
    )
}
