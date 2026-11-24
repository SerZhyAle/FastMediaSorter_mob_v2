package com.sza.fastmediasorter.ui.remotecontrol.helpers

import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeSealState
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import com.sza.fastmediasorter.domain.usecase.exchange.ManageExchangeAccountsUseCase
import com.sza.fastmediasorter.domain.usecase.exchange.WatchExchangeDirectoryDevicesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.io.IOException
import javax.inject.Inject

internal data class ExternalControlDirectory(
    val devices: List<SftpRendezvousDevice> = emptyList(),
    val checked: Boolean = false,
    val reachable: Boolean = false,
    val supported: Boolean = false,
    val complete: Boolean = false,
    val changedKeys: Set<String> = emptySet(),
    val signInAgain: String? = null,
)

internal class ExternalControlDirectoryManager @Inject constructor(
    private val accounts: ManageExchangeAccountsUseCase,
    private val watchDevices: WatchExchangeDirectoryDevicesUseCase,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<ExternalControlDirectory> = accounts.entries.flatMapLatest { held ->
        val entries = held.filter(DeviceExchangeAccountEntry::acceptsCommands)
        if (entries.isEmpty()) {
            flowOf(ExternalControlDirectory(checked = true))
        } else {
            val ownIds = held.mapTo(HashSet(), DeviceExchangeAccountEntry::deviceId)
            combine(entries.map(::watchEntry)) { parts -> merge(parts.toList(), ownIds) }
        }
    }

    private fun watchEntry(entry: DeviceExchangeAccountEntry): Flow<AccountDirectory?> =
        watchDevices(entry).map<List<SftpRendezvousDevice>, AccountDirectory?> { devices ->
            val sealStates = accounts.sealStates(entry, devices)
            val self = devices.firstOrNull { it.deviceId == entry.deviceId }
            val needsSignIn = self != null && (COMMAND_V1 !in self.capabilities || !self.canReceiveSealed)
            AccountDirectory(
                devices.filter { it.canReceiveSealed && sealStates[it.deviceId] == ExchangeSealState.TRUSTED },
                sealStates.filterValues { it == ExchangeSealState.KEY_CHANGED }.keys,
                needsSignIn,
                accounts.signInAgainString(entry).takeIf { needsSignIn },
            )
        }.onStart { emit(null) }.catch { error ->
            if (error !is IOException) throw error
            emit(AccountDirectory.UNREACHABLE)
        }

    private fun merge(parts: List<AccountDirectory?>, ownIds: Set<String>): ExternalControlDirectory {
        val reachable = parts.filterNotNull().filter { it !== AccountDirectory.UNREACHABLE }
        val changed = reachable.flatMap(AccountDirectory::changedKeys).toSet()
        return ExternalControlDirectory(
            devices = reachable.flatMap(AccountDirectory::devices)
                .filterNot { it.deviceId in ownIds || it.deviceId in changed }
                .distinctBy(SftpRendezvousDevice::deviceId)
                .sortedBy { it.deviceName.lowercase() },
            checked = parts.none { it == null },
            reachable = reachable.isNotEmpty(),
            supported = true,
            complete = reachable.size == parts.size,
            changedKeys = changed,
            signInAgain = reachable.firstOrNull { it.needsSignIn }?.signInAgain,
        )
    }

    private data class AccountDirectory(
        val devices: List<SftpRendezvousDevice>,
        val changedKeys: Set<String>,
        val needsSignIn: Boolean,
        val signInAgain: String?,
    ) {
        companion object {
            val UNREACHABLE = AccountDirectory(emptyList(), emptySet(), false, null)
        }
    }

    private companion object {
        const val COMMAND_V1 = "command-v1"
    }
}
