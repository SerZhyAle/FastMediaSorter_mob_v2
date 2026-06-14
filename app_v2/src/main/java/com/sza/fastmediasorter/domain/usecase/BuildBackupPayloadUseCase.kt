package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.data.local.db.FavoritesDao
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
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
    private val authSessionRepository: AuthSessionRepository
) {
    suspend operator fun invoke(): BackupPayload {
        Timber.d("S0406: build unified backup payload (settings/resources/favorites/creds/sessions)")
        val settings = settingsRepository.getSettings().first()
        val resources = resourceRepository.getAllResourcesSync()
        val resourceLookup = resources.associateBy { it.id }

        val favorites = BackupMapper.toBackupFavorites(favoritesDao.getAllFavoritesSync(), resourceLookup)
        val scheduledOps = scheduledOperationRepository.getAll().first()
            .mapNotNull { BackupMapper.toBackupScheduledOperation(it, resourceLookup) }

        val networkCredentials = credentialsRepository.getAllCredentials().first()
            .map { BackupMapper.toBackupNetworkCredential(it) }
        val webAuthSessions = authSessionRepository.exportSessions()
            .map { BackupMapper.toBackupWebAuthSession(it) }

        return BackupMapper.toBackupPayload(
            settings = settings,
            resources = resources,
            favorites = favorites,
            appVersionCode = BuildConfig.VERSION_CODE.toLong(),
            appVersionName = BuildConfig.VERSION_NAME,
            scheduledOperations = scheduledOps
        ).copy(
            networkCredentials = networkCredentials,
            webAuthSessions = webAuthSessions
        )
    }
}
