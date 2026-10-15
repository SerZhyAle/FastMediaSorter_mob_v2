package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.domain.transfer.DriveChannelSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4231: persists the Drive channel choices. Kept out of `AppSettings`: nothing outside the channel
 * reads them, and the general settings snapshot has no constructor room to spare.
 */
@Singleton
class DriveChannelSettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DriveChannelSettings {

    override val enabled: Flow<Boolean> = flag(keyEnabled)

    override val backgroundPoll: Flow<Boolean> = flag(keyBackgroundPoll)

    override val clipboardEnabled: Flow<Boolean> = flag(keyClipboard)

    override suspend fun packetBytes(): Long = dataStore.data.first()[keyPacketBytes] ?: DEFAULT_PACKET_BYTES

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[keyEnabled] = enabled }
    }

    override suspend fun setBackgroundPoll(enabled: Boolean) {
        dataStore.edit { it[keyBackgroundPoll] = enabled }
    }

    override suspend fun setClipboardEnabled(enabled: Boolean) {
        dataStore.edit { it[keyClipboard] = enabled }
    }

    override suspend fun receivedPacketIds(): Set<String> =
        dataStore.data.first()[keyReceived].orEmpty().split(SEPARATOR).filter { it.isNotEmpty() }.toSet()

    override suspend fun markReceived(packetId: String) {
        dataStore.edit { prefs ->
            val kept = prefs[keyReceived].orEmpty().split(SEPARATOR).filter { it.isNotEmpty() && it != packetId }
            prefs[keyReceived] = (kept + packetId).takeLast(MAX_REMEMBERED).joinToString(SEPARATOR)
        }
    }

    private fun flag(key: Preferences.Key<Boolean>): Flow<Boolean> = dataStore.data
        .map { it[key] ?: false }
        .distinctUntilChanged()
        .flowOn(ioDispatcher)

    companion object {
        /** 5 GiB, matching `clipboardBytes` of the exchange server (contract item CS). */
        const val DEFAULT_PACKET_BYTES: Long = 5L * 1024 * 1024 * 1024

        private val keyEnabled = booleanPreferencesKey("drive_channel_enabled")
        private val keyBackgroundPoll = booleanPreferencesKey("drive_channel_background_poll")
        private val keyClipboard = booleanPreferencesKey("drive_channel_clipboard")
        private val keyPacketBytes = longPreferencesKey("drive_channel_packet_bytes")
        private val keyReceived = stringPreferencesKey("drive_channel_received_packets")

        // Packet ids are [A-Za-z0-9_-] (contract item CN), so a comma never occurs inside one.
        private const val SEPARATOR = ","
        private const val MAX_REMEMBERED = 200
    }
}
