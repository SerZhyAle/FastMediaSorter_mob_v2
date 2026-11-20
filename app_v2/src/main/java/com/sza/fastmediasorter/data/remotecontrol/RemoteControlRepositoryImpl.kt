package com.sza.fastmediasorter.data.remotecontrol

import com.sza.fastmediasorter.domain.remotecontrol.RemoteCommandAnswer
import com.sza.fastmediasorter.domain.remotecontrol.RemoteCommandLogEntry
import com.sza.fastmediasorter.domain.remotecontrol.RemoteConsentMode
import com.sza.fastmediasorter.domain.remotecontrol.RemoteControlRepository
import com.sza.fastmediasorter.domain.remotecontrol.RemoteControllerGrant
import com.sza.fastmediasorter.domain.remotecontrol.RemoteRiskClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteControlRepositoryImpl @Inject constructor(
    private val decisions: RemoteControlDecisionStore,
    private val ring: RemoteCommandRing,
    private val log: RemoteCommandLog,
) : RemoteControlRepository {
    override val listening: Flow<Boolean> = decisions.listening
    override val grants: Flow<List<RemoteControllerGrant>> = decisions.grants
    override val recentLog: Flow<List<RemoteCommandLogEntry>> = log.entries.onStart { log.refresh() }

    override suspend fun setListening(on: Boolean) = decisions.setListening(on)

    override suspend fun grant(deviceId: String, deviceName: String) = decisions.updateGrants { current ->
        if (current.any { it.deviceId == deviceId }) current else current + RemoteControllerGrant(deviceId, deviceName)
    }

    override suspend fun revoke(deviceId: String) = decisions.updateGrants { current ->
        current.filterNot { it.deviceId == deviceId }
    }

    override suspend fun setClassMode(deviceId: String, riskClass: RemoteRiskClass, mode: RemoteConsentMode?) =
        decisions.updateGrants { current ->
            current.map { grant ->
                if (grant.deviceId != deviceId) {
                    grant
                } else {
                    val classes = if (mode == null) grant.classes - riskClass else grant.classes + (riskClass to mode)
                    grant.copy(classes = classes)
                }
            }
        }

    override suspend fun findAnswered(controllerId: String, requestId: String): RemoteCommandAnswer? =
        ring.find(controllerId, requestId)

    override suspend fun recordAnswered(
        controllerId: String,
        requestId: String,
        answer: RemoteCommandAnswer,
        keepUntilMillis: Long,
    ) = ring.record(controllerId, requestId, answer, keepUntilMillis)

    override suspend fun appendLog(entry: RemoteCommandLogEntry) = log.append(entry)
}
