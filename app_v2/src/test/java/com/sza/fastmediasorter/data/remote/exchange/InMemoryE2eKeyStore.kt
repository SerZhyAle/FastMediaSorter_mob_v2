package com.sza.fastmediasorter.data.remote.exchange

import com.sza.fastmediasorter.data.security.e2e.E2eDeviceKey

/** A device-key store held in memory, so a test can read back the key a client declared. */
class InMemoryE2eKeyStore : DeviceExchangeE2eKeyStore {
    val keys = mutableMapOf<String, E2eDeviceKey>()

    override suspend fun keyFor(deviceId: String): E2eDeviceKey = keys.getOrPut(deviceId) { E2eDeviceKey.generate() }

    override suspend fun existing(deviceId: String): E2eDeviceKey? = keys[deviceId]

    override suspend fun forget(deviceId: String) {
        keys.remove(deviceId)
    }
}
