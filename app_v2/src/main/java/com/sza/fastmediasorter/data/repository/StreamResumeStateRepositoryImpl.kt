package com.sza.fastmediasorter.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.sza.fastmediasorter.domain.model.StreamResumeState
import com.sza.fastmediasorter.domain.repository.StreamResumeStateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S1152: SharedPreferences-backed store for the last active stream. Single slot, mirrors
 * [ResumeStateRepositoryImpl] but without the media-resource coupling (streams use a URL, not a
 * resolvable resourceId).
 */
class StreamResumeStateRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : StreamResumeStateRepository {

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun save(state: StreamResumeState) = withContext(Dispatchers.IO) {
        Timber.d("StreamResumeState: save url=%s kind=%s", state.url, state.mediaKind)
        prefs.edit()
            .putString(KEY_URL, state.url)
            .putString(KEY_TITLE, state.title)
            .putString(KEY_MEDIA_KIND, state.mediaKind)
            .putBoolean(KEY_WAS_PLAYING, state.wasPlaying)
            .putLong(KEY_SAVED_AT, state.savedAt)
            .apply()
    }

    override suspend fun get(): StreamResumeState? = withContext(Dispatchers.IO) {
        val url = prefs.getString(KEY_URL, null) ?: return@withContext null
        StreamResumeState(
            url = url,
            title = prefs.getString(KEY_TITLE, "").orEmpty(),
            mediaKind = prefs.getString(KEY_MEDIA_KIND, "VIDEO").orEmpty(),
            wasPlaying = prefs.getBoolean(KEY_WAS_PLAYING, false),
            savedAt = prefs.getLong(KEY_SAVED_AT, 0L)
        ).also { Timber.d("StreamResumeStateRepository: get url=%s savedAt=%d", it.url, it.savedAt) }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        Timber.d("StreamResumeStateRepository: clear")
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "stream_resume_state_prefs"
        const val KEY_URL = "url"
        const val KEY_TITLE = "title"
        const val KEY_MEDIA_KIND = "media_kind"
        const val KEY_WAS_PLAYING = "was_playing"
        const val KEY_SAVED_AT = "saved_at"
    }
}
