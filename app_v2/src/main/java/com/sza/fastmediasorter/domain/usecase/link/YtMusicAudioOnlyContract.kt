package com.sza.fastmediasorter.domain.usecase.link

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0260 strategic §11.2: a YTMusic share must never persist a thumbnail, preview,
 * or any other non-audio artifact as the final download result.
 */
@Singleton
class YtMusicAudioOnlyContract @Inject constructor() {

    fun validate(
        originalUrl: String,
        canonicalAudioOnly: Boolean,
        resultMime: String?,
        resultFileName: String?,
    ): ValidationOutcome {
        val originalHost = originalUrl.toHttpUrlOrNull()?.host?.lowercase()
        if (!canonicalAudioOnly && originalHost != YT_MUSIC_HOST) {
            return ValidationOutcome.Accept
        }

        val normalizedMime = resultMime?.lowercase().orEmpty()
        val normalizedExtension = resultFileName
            ?.substringAfterLast('.', "")
            ?.lowercase()
            .orEmpty()

        return when {
            normalizedMime.startsWith("audio/") || normalizedExtension in AUDIO_EXTENSIONS -> {
                ValidationOutcome.Accept
            }
            normalizedMime.startsWith("image/") || normalizedExtension in IMAGE_EXTENSIONS -> {
                ValidationOutcome.Reject(
                    reasonCode = "ytmusic_thumbnail_artifact",
                    fallbackAllowed = false,
                )
            }
            else -> {
                ValidationOutcome.Reject(
                    reasonCode = "ytmusic_non_audio_artifact",
                    fallbackAllowed = FALLBACK_ALLOWED,
                )
            }
        }
    }

    sealed interface ValidationOutcome {
        data object Accept : ValidationOutcome
        data class Reject(
            val reasonCode: String,
            val fallbackAllowed: Boolean,
        ) : ValidationOutcome
    }

    companion object {
        const val USER_FACING_ERROR_KEY = "link_download_error_ytmusic_audio_only"

        private const val YT_MUSIC_HOST = "music.youtube.com"
        private const val FALLBACK_ALLOWED = false

        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "opus", "ogg", "wav", "flac")
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp")
    }
}