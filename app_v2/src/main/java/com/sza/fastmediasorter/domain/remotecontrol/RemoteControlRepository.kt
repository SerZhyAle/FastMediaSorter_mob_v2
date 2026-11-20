package com.sza.fastmediasorter.domain.remotecontrol

import kotlinx.coroutines.flow.Flow

/** Device-only remote-control state: the switch, the grants, the replay ring and the command log. */
interface RemoteControlRepository {
    val listening: Flow<Boolean>
    val grants: Flow<List<RemoteControllerGrant>>
    val recentLog: Flow<List<RemoteCommandLogEntry>>

    suspend fun setListening(on: Boolean)

    suspend fun grant(deviceId: String, deviceName: String)

    suspend fun revoke(deviceId: String)

    /** [mode] null takes the class away from the controller. */
    suspend fun setClassMode(deviceId: String, riskClass: RemoteRiskClass, mode: RemoteConsentMode?)

    suspend fun findAnswered(controllerId: String, requestId: String): RemoteCommandAnswer?

    suspend fun recordAnswered(
        controllerId: String,
        requestId: String,
        answer: RemoteCommandAnswer,
        keepUntilMillis: Long,
    )

    suspend fun appendLog(entry: RemoteCommandLogEntry)
}
