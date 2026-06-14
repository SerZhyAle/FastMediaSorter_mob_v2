package com.sza.fastmediasorter.core.coordinator

import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.SmbOperationsUseCase
import com.sza.fastmediasorter.worker.WorkManagerScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reacts to a remote source being switched off: cancels the in-flight background work that may still
 * be touching it and drops pooled connections, so "disabled" takes effect promptly rather than only
 * on the next scheduled pass. Best-effort - items already synced in the current pass are not rolled
 * back.
 *
 * New work is already prevented by [com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate]
 * at every entry point; this coordinator only handles the in-flight tail, which for remote sources is
 * the per-resource thumbnail preload plus any open network connection. The periodic network sync is
 * deliberately NOT cancelled - that would tear down the whole schedule; its next pass self-skips
 * disabled sources via the gate. Cloud sources have no background sync/preload, so only the network
 * (SMB/(S)FTP) flags need a reaction here.
 */
@Singleton
class RemoteSourceDisableCoordinator @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val workManagerScheduler: WorkManagerScheduler,
    private val smbOperationsUseCase: dagger.Lazy<SmbOperationsUseCase>,
    @param:ApplicationScope private val appScope: CoroutineScope,
) {

    private val started = AtomicBoolean(false)

    /** Begin observing settings. Idempotent - safe to call from the app-init path more than once. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        appScope.launch {
            var previous: NetworkFlags? = null
            settingsRepository.getSettings().collect { settings ->
                val current = settings.networkFlags()
                val prev = previous
                previous = current
                if (prev != null && prev.anyTurnedOffIn(current)) {
                    Timber.d("S0391: network source disabled, cancelling in-flight work")
                    Timber.i("RemoteSourceDisableCoordinator: a network source was disabled - cancelling in-flight network work")
                    workManagerScheduler.cancelAllThumbnailPreloads()
                    runCatching { smbOperationsUseCase.get().clearAllConnectionPools() }
                        .onFailure { Timber.w(it, "RemoteSourceDisableCoordinator: clearAllConnectionPools failed") }
                }
            }
        }
    }

    private data class NetworkFlags(val smb: Boolean, val sftp: Boolean, val ftp: Boolean) {
        /** True when any flag goes from enabled (this) to disabled ([next]). */
        fun anyTurnedOffIn(next: NetworkFlags): Boolean =
            (smb && !next.smb) || (sftp && !next.sftp) || (ftp && !next.ftp)
    }

    private fun AppSettings.networkFlags() = NetworkFlags(smbEnabled, sftpEnabled, ftpEnabled)
}
