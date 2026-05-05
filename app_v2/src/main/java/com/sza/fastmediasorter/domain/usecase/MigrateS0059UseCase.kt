package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * One-time migration (S0059): sets [MediaResource.allFiles] = true on the predefined
 * "Recent" virtual resource and any local "Downloads" folder resources, for users who
 * installed the app before this default was introduced.
 *
 * Idempotent: stores a done-flag in DataStore and returns immediately on subsequent calls.
 * Silent: no user-facing notification.
 */
class MigrateS0059UseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val resourceRepository: ResourceRepository,
    private val settingsRepository: SettingsRepository,
    private val resolveResourceIconUseCase: ResolveResourceIconUseCase
) {

    companion object {
        private val KEY_DONE = booleanPreferencesKey("migration_s0059_done")
    }

    suspend operator fun invoke() {
        // Idempotency guard — skip if already migrated
        val isDone = dataStore.data.first()[KEY_DONE] ?: false
        if (isDone) return

        val resources = resourceRepository.getAllResources().first()

        migrateRecent(resources)
        migrateDownloads(resources)

        dataStore.edit { it[KEY_DONE] = true }
        Timber.i("S0059 migration completed")
    }

    // -------------------------------------------------------------------------

    private suspend fun migrateRecent(resources: List<MediaResource>) {
        val recent = resources.firstOrNull { it.path == LocalMediaScanner.VIRTUAL_PATH_RECENT }
        if (recent != null) {
            if (!recent.allFiles) {
                resourceRepository.updateResource(recent.copy(allFiles = true))
                Timber.i("S0059: set allFiles=true on Recent (id=${recent.id})")
            }
        } else {
            // Recent was manually deleted — recreate it with allFiles=true appended to the list
            val settings = settingsRepository.getSettings().first()
            val maxOrder = resources.maxOfOrNull { it.displayOrder } ?: -1
            val newRecent = MediaResource(
                id = 0,
                name = context.getString(R.string.recent_media),
                comment = context.getString(R.string.virtual_comment_recent),
                path = LocalMediaScanner.VIRTUAL_PATH_RECENT,
                type = ResourceType.LOCAL,
                supportedMediaTypes = settings.getGloballyEnabledMediaTypes(),
                profile = ResourceProfile.NONE,
                displayOrder = maxOrder + 1,
                isWritable = false,
                scanSubdirectories = false,
                allFiles = true,
                iconId = resolveResourceIconUseCase(
                    path = LocalMediaScanner.VIRTUAL_PATH_RECENT,
                    profile = ResourceProfile.NONE,
                    type = ResourceType.LOCAL
                )
            )
            resourceRepository.addResource(newRecent)
            Timber.i("S0059: recreated Recent with allFiles=true (displayOrder=${maxOrder + 1})")
        }
    }

    private suspend fun migrateDownloads(resources: List<MediaResource>) {
        val downloadsPath = Environment
            .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .absolutePath

        // Update every LOCAL resource pointing to the Downloads folder that still has allFiles=false
        resources
            .filter { it.type == ResourceType.LOCAL && it.path == downloadsPath && !it.allFiles }
            .forEach { resource ->
                resourceRepository.updateResource(resource.copy(allFiles = true))
                Timber.i("S0059: set allFiles=true on Downloads resource (id=${resource.id})")
            }
    }
}
