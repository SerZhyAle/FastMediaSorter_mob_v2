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
import java.io.File
import javax.inject.Inject

class ResumeStateRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ResumeStateRepository {

    companion object {
        private const val PREFS_NAME_PREFIX = "resume_state_prefs"

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

    private fun prefs(windowId: String): SharedPreferences =
        context.getSharedPreferences("resume_state_prefs_$windowId", Context.MODE_PRIVATE)

    override suspend fun saveState(windowId: String, state: ResumeState) = withContext(Dispatchers.IO) {
        Timber.d("ResumeStateRepository[$windowId]: saveState - file=${state.filePath}, resource=${state.resourceId}, screen=${state.screenType}, playing=${state.isPlaying}")
        prefs(windowId).edit()
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

    override suspend fun getState(windowId: String): ResumeState? = withContext(Dispatchers.IO) {
        val p = prefs(windowId)
        val filePath = p.getString(KEY_FILE_PATH, null) ?: return@withContext null
        val resourceId = p.getLong(KEY_RESOURCE_ID, -1L)
        if (resourceId == -1L) return@withContext null

        try {
            ResumeState(
                filePath = filePath,
                resourceId = resourceId,
                currentFolderPath = p.getString(KEY_CURRENT_FOLDER_PATH, null),
                screenType = ScreenType.valueOf(p.getString(KEY_SCREEN_TYPE, null) ?: return@withContext null),
                sortMode = SortMode.valueOf(p.getString(KEY_SORT_MODE, null) ?: return@withContext null),
                isPlaying = p.getBoolean(KEY_IS_PLAYING, false),
                isSlideshowEnabled = p.getBoolean(KEY_IS_SLIDESHOW_ENABLED, false),
                mediaType = MediaType.valueOf(p.getString(KEY_MEDIA_TYPE, null) ?: return@withContext null),
                savedAt = p.getLong(KEY_SAVED_AT, 0L)
            ).also {
                Timber.d("ResumeStateRepository[$windowId]: getState - loaded state for file=${it.filePath}, savedAt=${it.savedAt}")
            }
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "ResumeStateRepository[$windowId]: getState - invalid enum value in prefs, clearing")
            prefs(windowId).edit().clear().apply()
            null
        }
    }

    override suspend fun clearState(windowId: String) = withContext(Dispatchers.IO) {
        Timber.d("ResumeStateRepository[$windowId]: clearState")
        prefs(windowId).edit().clear().apply()
    }

    /**
     * S1306: every window id got its own `resume_state_prefs_<uuid>.xml`, and nothing ever deleted
     * them - a file (plus its .bak) accumulated per window for the life of the install, none of it
     * readable again once its state passed the 48 h TTL. Sweep the leftovers on a background pass.
     *
     * Files younger than the TTL are kept: their window may still be restorable.
     */
    override suspend fun sweepStaleWindows(): Int = withContext(Dispatchers.IO) {
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val files = prefsDir.listFiles() ?: return@withContext 0
        val cutoff = System.currentTimeMillis() - RESUME_TTL_MS
        var deleted = 0

        files.asSequence()
            .filter { it.name.startsWith("${PREFS_NAME_PREFIX}_") && it.name.endsWith(".xml") }
            .filter { it.lastModified() < cutoff }
            .forEach { file ->
                // Drop through SharedPreferences so any in-memory instance is invalidated too.
                val prefsName = file.name.removeSuffix(".xml")
                val removed = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    context.deleteSharedPreferences(prefsName)
                } else {
                    context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
                    file.delete()
                }
                if (removed) deleted++
            }

        if (deleted > 0) {
            Timber.i("ResumeStateRepository: swept $deleted stale window state file(s)")
        }
        deleted
    }
}
