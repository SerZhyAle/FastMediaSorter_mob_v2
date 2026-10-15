package com.sza.fastmediasorter.wear.data.exchange

import com.sza.fastmediasorter.data.security.e2e.E2eDeviceKey

/** The watch's device keys and trusted peer keys held in memory, so a test can read back what was kept. */
class FakeWearExchangeE2eStore : WearExchangeE2eStore {
    val keys = mutableMapOf<String, E2eDeviceKey>()
    val trust = mutableMapOf<String, Map<String, String>>()

    override suspend fun keyFor(deviceId: String): E2eDeviceKey = keys.getOrPut(deviceId) { E2eDeviceKey.generate() }

    override suspend fun existing(deviceId: String): E2eDeviceKey? = keys[deviceId]

    override suspend fun forget(deviceId: String) {
        keys.remove(deviceId)
        trust.remove(deviceId)
    }

    override suspend fun trusted(deviceId: String): Map<String, String> = trust[deviceId].orEmpty()

    override suspend fun saveTrusted(deviceId: String, trusted: Map<String, String>) {
        trust[deviceId] = trusted
    }
}
