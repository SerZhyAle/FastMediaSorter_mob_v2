package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.cloud.GoogleDriveSftpRendezvousDataSource
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeDirectory
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeDirectoryWatcher
import com.sza.fastmediasorter.data.repository.settings.SftpRendezvousIdentityStore
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeDirectoryListing
import com.sza.fastmediasorter.domain.model.ExchangeDirectoryRules
import com.sza.fastmediasorter.domain.model.ExchangeDirectorySource
import com.sza.fastmediasorter.domain.model.isFreshAt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The account directory of contract DEVICE-EXCHANGE as one listing: every held entry's server, live through
 * `subscribe`, and the Drive channel, polled (section 8.1). The flow is cold - a server stream and the Drive poll
 * exist only while someone collects it, which section 3.2 of S4116 limits to an open directory surface.
 */
@Singleton
class ExchangeDirectoryRepository @Inject constructor(
    private val accounts: DeviceExchangeAccountRepository,
    private val watcher: DeviceExchangeDirectoryWatcher,
    private val drive: GoogleDriveSftpRendezvousDataSource,
    private val driveIds: SftpRendezvousIdentityStore,
) {

    internal var nowMs: () -> Long = System::currentTimeMillis

    /** [playableModes] are the `LIVE-BROADCAST` modes this build plays; any other broadcast is marked unsupported. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(playableModes: Set<String>): Flow<ExchangeDirectoryListing> =
        accounts.entries.flatMapLatest { entries ->
            val sources: List<Flow<ExchangeDirectorySource?>> = entries.map(::serverSource) + driveSource()
            combine(sources) { answered -> listing(entries, answered.toList(), playableModes) }
        }

    private suspend fun listing(
        entries: List<DeviceExchangeAccountEntry>,
        answered: List<ExchangeDirectorySource?>,
        playableModes: Set<String>,
    ): ExchangeDirectoryListing {
        val ready = answered.filterNotNull()
        val own = entries.mapTo(HashSet()) { it.deviceId } + driveIds.deviceId()
        return ExchangeDirectoryRules.listing(
            servers = ready.filter { it.key != ExchangeDirectorySource.DRIVE_SOURCE },
            drive = ready.firstOrNull { it.key == ExchangeDirectorySource.DRIVE_SOURCE },
            ownDeviceIds = own,
            playableModes = playableModes,
        ).copy(pending = ready.size < answered.size)
    }

    // A stream that ends - the producer took the control stream back - leaves its last directory in place.
    private fun serverSource(entry: DeviceExchangeAccountEntry): Flow<ExchangeDirectorySource?> =
        watcher.watch(entry)
            .map<DeviceExchangeDirectory, ExchangeDirectorySource?> { directory ->
                ExchangeDirectorySource(entry.identity, directory.devices, directory.resources, directory.broadcasts)
            }
            .onStart { emit(null) }
            .catch { failure ->
                if (failure !is IOException) throw failure
                Timber.i("ExchangeDirectoryRepository: a server is not reachable (%s)", failure.javaClass.simpleName)
                emit(ExchangeDirectorySource.unreachable(entry.identity))
            }

    // No Google sign-in reads as an unreachable store: its attachments show unavailable, never gone.
    private fun driveSource(): Flow<ExchangeDirectorySource?> = flow {
        emit(null)
        while (true) {
            val snapshot = drive.snapshot()
            val now = nowMs()
            emit(
                snapshot.fold(
                    onSuccess = { held ->
                        ExchangeDirectorySource(
                            key = ExchangeDirectorySource.DRIVE_SOURCE,
                            // An expired device record no longer vouches for presence (amendment 0.12 C).
                            devices = held.devices.filter { it.record.isFreshAt(now, it.modifiedMs) }.map { it.record },
                            resources = held.resources.map { it.record },
                            broadcasts = emptyList(),
                        )
                    },
                    onFailure = { ExchangeDirectorySource.unreachable(ExchangeDirectorySource.DRIVE_SOURCE) },
                )
            )
            delay(DRIVE_POLL_INTERVAL_MS)
        }
    }

    companion object {
        const val DRIVE_POLL_INTERVAL_MS = 2 * 60 * 1_000L
    }
}
