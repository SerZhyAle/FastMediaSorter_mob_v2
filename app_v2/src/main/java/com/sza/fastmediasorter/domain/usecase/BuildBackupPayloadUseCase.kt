package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.data.local.db.FavoritesDao
import com.sza.fastmediasorter.data.local.db.LauncherCellDao
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * S0406: single source of truth that gathers the full app state into a [BackupPayload],
 * including secrets (network passwords, saved site authorizations). Storage-agnostic - both
 * the local-file and Google Drive paths build the payload through this use case so the two
 * backups share one structure.
 */
class BuildBackupPayloadUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val resourceRepository: ResourceRepository,
    private val favoritesDao: FavoritesDao,
    private val scheduledOperationRepository: ScheduledOperationRepository,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val authSessionRepository: AuthSessionRepository,
    private val launcherCellDao: LauncherCellDao
) {
    suspend operator fun invoke(): BackupPayload {
        val settings = settingsRepository.getSettings().first()
        val resources = resourceRepository.getAllResourcesSync()
        val resourceLookup = resources.associateBy { it.id }

        // S0783: back up file favorites only; live-channel favorites are not part of the favorites backup.
        val favorites = BackupMapper.toBackupFavorites(favoritesDao.getFileFavoritesSync(), resourceLookup)
        val scheduledOps = scheduledOperationRepository.getAll().first()
            .mapNotNull { BackupMapper.toBackupScheduledOperation(it, resourceLookup) }

        val networkCredentials = credentialsRepository.getAllCredentials().first()
            .map { BackupMapper.toBackupNetworkCredential(it) }
        val webAuthSessions = authSessionRepository.exportSessions()
            .map { BackupMapper.toBackupWebAuthSession(it) }
        val launcherCells = launcherCellDao.getAllCellsSync()
            .map { BackupMapper.toBackupLauncherCell(it) }

        return BackupMapper.toBackupPayload(
            settings = settings,
            resources = resources,
            favorites = favorites,
            appVersionCode = BuildConfig.VERSION_CODE.toLong(),
            appVersionName = BuildConfig.VERSION_NAME,
            scheduledOperations = scheduledOps
        ).copy(
            networkCredentials = networkCredentials,
            webAuthSessions = webAuthSessions,
            launcherCells = launcherCells
        )
    }
}
