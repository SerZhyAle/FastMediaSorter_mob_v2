package com.sza.fastmediasorter.domain.port

/** S4231: this installation as the Drive channel names it - the 8.1 device record's id and its shown name. */
data class LocalDeviceIdentity(val deviceId: String, val displayName: String)

interface LocalDeviceIdentitySource {
    suspend fun current(): LocalDeviceIdentity
}
