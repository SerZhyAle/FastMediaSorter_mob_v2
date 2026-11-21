package com.sza.fastmediasorter.domain.remotecontrol

import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeServerCall
import com.sza.fastmediasorter.domain.usecase.exchange.ManageExchangeAccountsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** A device of the account that may be asked to control this phone; only a device holding an `e2e-v1` key qualifies. */
data class RemoteControllerCandidate(val deviceId: String, val deviceName: String)

/**
 * The account devices read from every exchange entry that carries commands. [complete] holds when every such
 * entry answered, so a device missing from [devices] is really gone; [unreachable] holds when entries were
 * asked and none answered.
 */
data class RemoteControllerCandidates(
    val devices: List<RemoteControllerCandidate>,
    val complete: Boolean,
    val unreachable: Boolean,
)

/**
 * One row of "Who can control": an account device with its rights here. [onAccount] is false only for a controller
 * the account no longer lists - its rights stay until the person removes them (REMOTE-CONTROL rule 9).
 */
data class RemoteControllerChoice(
    val deviceId: String,
    val deviceName: String,
    val grant: RemoteControllerGrant?,
    val onAccount: Boolean = true,
) {
    companion object {
        /** Listed devices first with their rights, then every grant the account no longer lists, by name. */
        fun merge(
            candidates: RemoteControllerCandidates,
            grants: List<RemoteControllerGrant>,
        ): List<RemoteControllerChoice> {
            val byId = grants.associateBy(RemoteControllerGrant::deviceId)
            val listed = candidates.devices.map {
                RemoteControllerChoice(it.deviceId, it.deviceName, byId[it.deviceId])
            }
            val listedIds = listed.mapTo(HashSet(), RemoteControllerChoice::deviceId)
            val kept = grants.filter { it.deviceId !in listedIds }
                .map { RemoteControllerChoice(it.deviceId, it.deviceName, it, onAccount = !candidates.complete) }
            return (listed + kept).sortedBy { it.deviceName.lowercase() }
        }
    }
}

/**
 * What the "Accept commands" card and its "Who can control" list read and change: the switch, the grants per
 * controller and class, and the latest log lines. The state itself lives in [RemoteControlRepository]; this class
 * only adds the account directory, which names the devices the person can pick from.
 */
class ManageRemoteControlUseCase @Inject constructor(
    private val repository: RemoteControlRepository,
    private val accounts: ManageExchangeAccountsUseCase,
) {
    val listening: Flow<Boolean> get() = repository.listening

    val grants: Flow<List<RemoteControllerGrant>> get() = repository.grants

    val recentLog: Flow<List<RemoteCommandLogEntry>> get() = repository.recentLog

    /** Whether a held exchange entry can carry commands at all; without one the switch has nothing to listen to. */
    val canListen: Flow<Boolean>
        get() = accounts.entries.map { entries -> entries.any(DeviceExchangeAccountEntry::acceptsCommands) }

    suspend fun setListening(on: Boolean) = repository.setListening(on)

    /** The account's other devices that hold a key, read from each entry that carries commands. */
    suspend fun candidates(): RemoteControllerCandidates {
        val entries = accounts.entries.first()
        val asked = entries.filter(DeviceExchangeAccountEntry::acceptsCommands)
        val ownIds = entries.mapTo(HashSet(), DeviceExchangeAccountEntry::deviceId)
        val found = LinkedHashMap<String, RemoteControllerCandidate>()
        var answered = 0
        for (entry in asked) {
            val call = accounts.directory(entry)
            if (call !is ExchangeServerCall.Done) continue
            answered++
            call.value.devices
                .filter { it.canReceiveSealed && it.deviceId !in ownIds }
                .forEach { found.putIfAbsent(it.deviceId, RemoteControllerCandidate(it.deviceId, it.deviceName)) }
        }
        return RemoteControllerCandidates(
            devices = found.values.toList(),
            complete = asked.isNotEmpty() && answered == asked.size,
            unreachable = asked.isNotEmpty() && answered == 0,
        )
    }

    /** A new grant carries only `safe` with `always` (REMOTE-CONTROL rules 9 and 10). */
    suspend fun grant(deviceId: String, deviceName: String) = repository.grant(deviceId, deviceName)

    suspend fun revoke(deviceId: String) = repository.revoke(deviceId)

    suspend fun allowClass(deviceId: String, riskClass: RemoteRiskClass) =
        repository.setClassMode(deviceId, riskClass, RemoteControllerGrant.defaultMode(riskClass))

    suspend fun denyClass(deviceId: String, riskClass: RemoteRiskClass) =
        repository.setClassMode(deviceId, riskClass, null)

    suspend fun setMode(deviceId: String, riskClass: RemoteRiskClass, mode: RemoteConsentMode) =
        repository.setClassMode(deviceId, riskClass, mode)
}
