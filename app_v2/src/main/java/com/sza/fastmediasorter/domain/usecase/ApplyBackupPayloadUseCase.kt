package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import androidx.room.withTransaction
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.data.local.db.AppDatabase
import com.sza.fastmediasorter.data.local.db.LauncherCellEntity
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.util.getPackageInfoCompat
import com.sza.fastmediasorter.worker.WorkManagerScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S0406: single applier that restores a [BackupPayload] into app state - settings, resources,
 * network credentials (with passwords), favorites, scheduled operations, launcher desktop items
 * and saved site authorizations. Shared by the local-file import and Google Drive restore paths.
 * Tolerant of null sections so older (v4) backups apply without throwing. Merge keys per research/03.
 */
class ApplyBackupPayloadUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val resourceRepository: ResourceRepository,
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
        val webSessionsRestored: Int,
        val launcherCellsRestored: Int = 0
    )

    suspend operator fun invoke(payload: BackupPayload): RestoreSummary = withContext(Dispatchers.IO) {
        // S0727: restores via Room/DataStore are main-safe, but the Google Drive restore path may call
        // this off a Main coroutine; pin the whole apply to IO so no caller blocks the UI thread.
        check(payload.version <= BackupPayload.CURRENT_VERSION) {
            "Backup was created by a newer app version (${payload.appVersionName.orEmpty()}). Please update the app."
        }
        if (payload.version < BackupPayload.CURRENT_VERSION) {
            Timber.w(
                "ApplyBackup: restoring older backup v%d (current v%d)",
                payload.version,
                BackupPayload.CURRENT_VERSION
            )
        }

        // 1. Settings (full replace, merged onto current to preserve unknown/local-only fields).
        var settingsRestored = false
        payload.settings?.let { backupSettings ->
            val current = settingsRepository.getSettings().first()
            settingsRepository.updateSettings(BackupMapper.toAppSettings(backupSettings, current, payload.version))
            settingsRestored = true
        }

        // 2-6. S0732: the Room sections commit all-or-nothing in one transaction;
        // a kill mid-restore no longer leaves a partial DB. Counts are hoisted out so
        // RestoreSummary can read them; WorkManager scheduling is deferred to after commit.
        var credentialsRestored = 0
        var resourcesAdded = 0
        var resourcesUpdated = 0
        var resourcesNeedingAuth = 0
        var favoritesAdded = 0
        var favoritesSkipped = 0
        var scheduledOpsAdded = 0
        var launcherCellsRestored = 0
        val enabledScheduledOpIds = mutableListOf<Long>()
        val favoritesDao = db.favoritesDao()
        val launcherCellDao = db.launcherCellDao()

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
                        // Cancellation must abort the whole transaction, not be logged as one skipped
                        // row: swallowing it here would let the loop run on and commit a partial
                        // restore, defeating the all-or-nothing guarantee this block exists for.
                        e.rethrowIfCancellation()
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
                        e.rethrowIfCancellation()
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
                        e.rethrowIfCancellation()
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
                        e.rethrowIfCancellation()
                        Timber.w(e, "ApplyBackup: skip scheduled op")
                    }
                }
            }

            // 6. Launcher desktop cells (shortcuts, gadgets, sections).
            payload.launcherCells?.let { cells ->
                launcherCellDao.deleteAll()
                val entitiesToInsert = mutableListOf<LauncherCellEntity>()
                val packageManager = context.packageManager
                cells.forEach { backupCell ->
                    try {
                        val target = backupCell.target
                        val isApp = backupCell.kind == "SHORTCUT" &&
                            target.startsWith(LauncherCellCommand.PREFIX_APP)
                        val isPin = backupCell.kind == "SHORTCUT" &&
                            target.startsWith(LauncherCellCommand.PREFIX_PIN)
                        if (isApp || isPin) {
                            val pkg = if (isApp) {
                                target.removePrefix(LauncherCellCommand.PREFIX_APP)
                            } else {
                                target.removePrefix(LauncherCellCommand.PREFIX_PIN).substringBefore(":")
                            }
                            val isInstalled = try {
                                packageManager.getPackageInfoCompat(pkg, 0)
                                true
                            } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                                false
                            } catch (e: Exception) {
                                e.rethrowIfCancellation()
                                false
                            }
                            if (!isInstalled) {
                                Timber.i("ApplyBackup: skip launcher shortcut for uninstalled %s", pkg)
                                return@forEach
                            }
                        }
                        entitiesToInsert.add(BackupMapper.toLauncherCellEntity(backupCell))
                        launcherCellsRestored++
                    } catch (e: Exception) {
                        e.rethrowIfCancellation()
                        Timber.w(e, "ApplyBackup: skip launcher cell %s", backupCell.target)
                    }
                }
                if (entitiesToInsert.isNotEmpty()) {
                    launcherCellDao.insertAll(entitiesToInsert)
                }
            }
        }

        // Reschedule enabled operations after the transaction commits (WorkManager runs outside the tx).
        enabledScheduledOpIds.forEach { id ->
            scheduledOperationRepository.getById(id)?.let { workManagerScheduler.scheduleOperation(it) }
        }

        // 7. Saved site authorizations (overwrite per host+accountId).
        var webSessionsRestored = 0
        payload.webAuthSessions?.let { sessions ->
            val raws = sessions.map { BackupMapper.toRawAuthSession(it) }
            authSessionRepository.importSessions(raws)
            webSessionsRestored = raws.size
        }

        Timber.i(
            "ApplyBackup done: res +%d ~%d, creds %d, favs +%d, ops %d, sessions %d, launcher %d",
            resourcesAdded,
            resourcesUpdated,
            credentialsRestored,
            favoritesAdded,
            scheduledOpsAdded,
            webSessionsRestored,
            launcherCellsRestored
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
            webSessionsRestored = webSessionsRestored,
            launcherCellsRestored = launcherCellsRestored
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
