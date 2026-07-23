package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceDao
import javax.inject.Inject

/**
 * S1144: read/write the per-channel preferred audio/subtitle track for a stream source, keyed by the
 * stable channel URL (ADR-2/ADR-3: language-codes, not raw indices). [read] returns null when the channel
 * has no stored preference, so the apply path falls back to the global default (ADR-4). The write helpers
 * are read-modify-write within a single call so updating one field never clobbers the others.
 */
class StreamTrackPreferenceUseCase @Inject constructor(
    private val streamSourceDao: StreamSourceDao
) {

    /** Per-channel track preference. `subtitlesEnabled` null = follow the global default. */
    data class TrackPreference(
        val audioLang: String?,
        val subtitleLang: String?,
        val subtitlesEnabled: Boolean?
    )

    /** Returns the stored preference for [url], or null when the channel row is absent or all fields unset. */
    suspend fun read(url: String): TrackPreference? {
        val row = streamSourceDao.getByUrl(url) ?: return null
        if (row.preferredAudioLang == null &&
            row.preferredSubtitleLang == null &&
            row.subtitlesEnabled == null
        ) {
            return null
        }
        return TrackPreference(row.preferredAudioLang, row.preferredSubtitleLang, row.subtitlesEnabled)
    }

    /** Persist the audio-language pick for [url], preserving the stored subtitle fields. */
    suspend fun writeAudio(url: String, lang: String?) {
        val current = streamSourceDao.getByUrl(url)
        streamSourceDao.updateTrackPreferences(
            url = url,
            audioLang = lang,
            subtitleLang = current?.preferredSubtitleLang,
            subtitlesEnabled = current?.subtitlesEnabled
        )
    }

    /** Persist the subtitle-language pick and on/off state for [url], preserving the stored audio field. */
    suspend fun writeSubtitle(url: String, lang: String?, enabled: Boolean?) {
        val current = streamSourceDao.getByUrl(url)
        streamSourceDao.updateTrackPreferences(
            url = url,
            audioLang = current?.preferredAudioLang,
            subtitleLang = lang,
            subtitlesEnabled = enabled
        )
    }
}
