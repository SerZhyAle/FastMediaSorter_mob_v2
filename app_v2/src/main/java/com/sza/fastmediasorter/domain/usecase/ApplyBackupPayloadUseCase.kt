package com.sza.fastmediasorter.domain.usecase

import androidx.room.withTransaction
import com.sza.fastmediasorter.data.local.db.AppDatabase
import com.sza.fastmediasorter.data.local.db.FavoritesDao
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.worker.WorkManagerScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S0406: single applier that restores a [BackupPayload] into app state - settings, resources,
 * network credentials (with passwords), favorites, scheduled operations and saved site
 * authorizations. Shared by the local-file import and Google Drive restore paths. Tolerant of
 * null sections so older (v4) backups apply without throwing. Merge keys per research/03.
 */
class ApplyBackupPayloadUseCase @Inject constructor(
    private val db: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val resourceRepository: ResourceRepository,
    private val favoritesDao: FavoritesDao,
    private val scheduledOperationRepository: ScheduledOperationRepository,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val authSessionRepository: AuthSessionRepository,
    private val workManagerScheduler: WorkManagerScheduler
) {
    data class RestoreSummary(
        val settingsRestored: Boolean,
        val resourcesAdded: Int,
        val resourcesUpdated: Int,
        val resourcesNeedingAuth: Int,
        val credentialsRestored: Int,
        val favoritesAdded: Int,
        val favoritesSkipped: Int,
        val scheduledOpsAdded: Int,
        val webSessionsRestored: Int
    )

    suspend operator fun invoke(payload: BackupPayload): RestoreSummary = withContext(Dispatchers.IO) {
        // S0727: restores via Room/DataStore are main-safe, but the Google Drive restore path may call
        // this off a Main coroutine; pin the whole apply to IO so no caller blocks the UI thread.
        check(payload.version <= BackupPayload.CURRENT_VERSION) {
            "Backup was created by a newer app version (${payload.appVersionName.orEmpty()}). Please update the app."
        }
        if (payload.version < BackupPayload.CURRENT_VERSION) {
            Timber.w("ApplyBackup: restoring older backup v%d (current v%d)", payload.version, BackupPayload.CURRENT_VERSION)
        }

        // 1. Settings (full replace, merged onto current to preserve unknown/local-only fields).
        var settingsRestored = false
        payload.settings?.let { backupSettings ->
            val current = settingsRepository.getSettings().first()
            settingsRepository.updateSettings(BackupMapper.toAppSettings(backupSettings, current))
            settingsRestored = true
        }

        // 2-5. S0732: the Room sections (credentials, resources, favorites, scheduled-op rows) commit
        // all-or-nothing in one transaction; a kill mid-restore no longer leaves a partial DB. Counts
        // are hoisted out so the RestoreSummary can read them; WorkManager scheduling is deferred to
        // after commit (it must not run inside the DB transaction).
        var credentialsRestored = 0
        var resourcesAdded = 0
        var resourcesUpdated = 0
        var resourcesNeedingAuth = 0
        var favoritesAdded = 0
        var favoritesSkipped = 0
        var scheduledOpsAdded = 0
        val enabledScheduledOpIds = mutableListOf<Long>()
        db.withTransaction {
            // 2. Network credentials (merge by credentialId; backup password wins per research/03).
            payload.networkCredentials?.let { creds ->
                val existing = credentialsRepository.getAllCredentials().first().associateBy { it.credentialId }
                creds.forEach { backupCred ->
                    try {
                        val entity = BackupMapper.toNetworkCredentialsEntity(backupCred)
                        val prior = existing[entity.credentialId]
                        if (prior != null) {
                            credentialsRepository.update(entity.copy(id = prior.id))
                        } else {
                            credentialsRepository.insert(entity)
                        }
                        credentialsRestored++
                    } catch (e: Exception) {
                        Timber.w(e, "ApplyBackup: skip credential %s", backupCred.credentialId)
                    }
                }
            }

            // 3. Resources (merge by path+type / cloud key; keep id on update).
            payload.resources?.let { backupResources ->
                val existingResources = resourceRepository.getAllResourcesSync()
                backupResources.forEach { backupRes ->
                    try {
                        val candidate = BackupMapper.toMediaResource(backupRes)
                        val existing = existingResources.firstOrNull { isSameResource(it, candidate) }
                        if (existing != null) {
                            resourceRepository.updateResource(candidate.copy(id = existing.id))
                            resourcesUpdated++
                        } else {
                            resourceRepository.addResource(candidate)
                            resourcesAdded++
                            if (candidate.type != ResourceType.LOCAL) resourcesNeedingAuth++
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "ApplyBackup: skip resource %s", backupRes.name)
                    }
                }
            }

            // 4. Favorites (dedup by uri, resolve resource by path).
            payload.favorites?.let { favorites ->
                val resourcesByPath = resourceRepository.getAllResourcesSync().associateBy { it.path }
                favorites.forEach { backupFav ->
                    if (favoritesDao.isFavoriteSync(backupFav.uri)) {
                        favoritesSkipped++
                        return@forEach
                    }
                    val localResource = resourcesByPath[backupFav.resourcePath]
                    if (localResource == null) {
                        favoritesSkipped++
                        return@forEach
                    }
                    try {
                        favoritesDao.insert(BackupMapper.toFavoritesEntity(backupFav, localResource.id))
                        favoritesAdded++
                    } catch (e: Exception) {
                        Timber.w(e, "ApplyBackup: skip favorite %s", backupFav.displayName)
                        favoritesSkipped++
                    }
                }
            }

            // 5. Scheduled operations (upsert rows here; reschedule enabled ops after commit).
            payload.scheduledOperations?.let { ops ->
                val resourcesByPath = resourceRepository.getAllResourcesSync().associateBy { it.path }
                ops.forEach { backupOp ->
                    try {
                        val op = BackupMapper.toScheduledOperation(backupOp, resourcesByPath) ?: return@forEach
                        val newId = scheduledOperationRepository.upsert(op)
                        if (op.isEnabled) enabledScheduledOpIds.add(newId)
                        scheduledOpsAdded++
                    } catch (e: Exception) {
                        Timber.w(e, "ApplyBackup: skip scheduled op")
                    }
                }
            }
        }

        // Reschedule enabled operations after the transaction commits (WorkManager runs outside the tx).
        enabledScheduledOpIds.forEach { id ->
            scheduledOperationRepository.getById(id)?.let { workManagerScheduler.scheduleOperation(it) }
        }

        // 6. Saved site authorizations (overwrite per host+accountId).
        var webSessionsRestored = 0
        payload.webAuthSessions?.let { sessions ->
            val raws = sessions.map { BackupMapper.toRawAuthSession(it) }
            authSessionRepository.importSessions(raws)
            webSessionsRestored = raws.size
        }

        Timber.i(
            "ApplyBackup done: res +%d ~%d, creds %d, favs +%d, ops %d, sessions %d",
            resourcesAdded, resourcesUpdated, credentialsRestored, favoritesAdded, scheduledOpsAdded, webSessionsRestored
        )

        RestoreSummary(
            settingsRestored = settingsRestored,
            resourcesAdded = resourcesAdded,
            resourcesUpdated = resourcesUpdated,
            resourcesNeedingAuth = resourcesNeedingAuth,
            credentialsRestored = credentialsRestored,
            favoritesAdded = favoritesAdded,
            favoritesSkipped = favoritesSkipped,
            scheduledOpsAdded = scheduledOpsAdded,
            webSessionsRestored = webSessionsRestored
        )
    }

    private fun isSameResource(existing: MediaResource, candidate: MediaResource): Boolean {
        if (existing.type == ResourceType.CLOUD && candidate.type == ResourceType.CLOUD) {
            return existing.cloudProvider == candidate.cloudProvider &&
                existing.cloudFolderId == candidate.cloudFolderId &&
                existing.accountId == candidate.accountId
        }
        return existing.type == candidate.type && existing.path == candidate.path
    }
}
