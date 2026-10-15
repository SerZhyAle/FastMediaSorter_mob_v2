package com.sza.fastmediasorter.data.remote.exchange

import com.sza.fastmediasorter.data.security.e2e.E2eDeviceKey

/**
 * The private half of each device key of contract DEVICE-EXCHANGE item DF, one per `deviceId`. Keyed by the
 * id and not by the entry's address, because a key lives exactly as long as its `deviceId` and an address edit
 * keeps the id (16 item AA).
 */
interface DeviceExchangeE2eKeyStore {
    /** The key of [deviceId], made and stored on first use; null only when the secret store cannot keep one. */
    suspend fun keyFor(deviceId: String): E2eDeviceKey?

    /** The key of [deviceId] if one was ever made - an entry enrolled before 0.29 has none. */
    suspend fun existing(deviceId: String): E2eDeviceKey?

    suspend fun forget(deviceId: String)
}
