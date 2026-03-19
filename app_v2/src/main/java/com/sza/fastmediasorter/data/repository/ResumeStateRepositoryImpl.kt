package com.sza.fastmediasorter.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResumeState
import com.sza.fastmediasorter.domain.model.ScreenType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.repository.ResumeStateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResumeStateRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ResumeStateRepository {

    companion object {
        private const val PREFS_NAME = "resume_state_prefs"

        private const val KEY_FILE_PATH = "file_path"
        private const val KEY_RESOURCE_ID = "resource_id"
        private const val KEY_CURRENT_FOLDER_PATH = "current_folder_path"
        private const val KEY_SCREEN_TYPE = "screen_type"
        private const val KEY_SORT_MODE = "sort_mode"
        private const val KEY_IS_PLAYING = "is_playing"
        private const val KEY_IS_SLIDESHOW_ENABLED = "is_slideshow_enabled"
        private const val KEY_MEDIA_TYPE = "media_type"
        private const val KEY_SAVED_AT = "saved_at"

        /** 48 hours TTL for resume state validity */
        const val RESUME_TTL_MS = 48L * 60 * 60 * 1000
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun saveState(state: ResumeState) = withContext(Dispatchers.IO) {
        Timber.d("ResumeStateRepository: saveState — file=${state.filePath}, resource=${state.resourceId}, screen=${state.screenType}, playing=${state.isPlaying}")
        prefs.edit()
            .putString(KEY_FILE_PATH, state.filePath)
            .putLong(KEY_RESOURCE_ID, state.resourceId)
            .putString(KEY_CURRENT_FOLDER_PATH, state.currentFolderPath)
            .putString(KEY_SCREEN_TYPE, state.screenType.name)
            .putString(KEY_SORT_MODE, state.sortMode.name)
            .putBoolean(KEY_IS_PLAYING, state.isPlaying)
            .putBoolean(KEY_IS_SLIDESHOW_ENABLED, state.isSlideshowEnabled)
            .putString(KEY_MEDIA_TYPE, state.mediaType.name)
            .putLong(KEY_SAVED_AT, state.savedAt)
            .apply()
    }

    override suspend fun getState(): ResumeState? = withContext(Dispatchers.IO) {
        val filePath = prefs.getString(KEY_FILE_PATH, null) ?: return@withContext null
        val resourceId = prefs.getLong(KEY_RESOURCE_ID, -1L)
        if (resourceId == -1L) return@withContext null

        try {
            ResumeState(
                filePath = filePath,
                resourceId = resourceId,
                currentFolderPath = prefs.getString(KEY_CURRENT_FOLDER_PATH, null),
                screenType = ScreenType.valueOf(prefs.getString(KEY_SCREEN_TYPE, null) ?: return@withContext null),
                sortMode = SortMode.valueOf(prefs.getString(KEY_SORT_MODE, null) ?: return@withContext null),
                isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false),
                isSlideshowEnabled = prefs.getBoolean(KEY_IS_SLIDESHOW_ENABLED, false),
                mediaType = MediaType.valueOf(prefs.getString(KEY_MEDIA_TYPE, null) ?: return@withContext null),
                savedAt = prefs.getLong(KEY_SAVED_AT, 0L)
            ).also {
                Timber.d("ResumeStateRepository: getState — loaded state for file=${it.filePath}, savedAt=${it.savedAt}")
            }
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "ResumeStateRepository: getState — invalid enum value in prefs, clearing")
            clearStateInternal()
            null
        }
    }

    override suspend fun clearState() = withContext(Dispatchers.IO) {
        Timber.d("ResumeStateRepository: clearState")
        clearStateInternal()
    }

    private fun clearStateInternal() {
        prefs.edit().clear().apply()
    }
}
