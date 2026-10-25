package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import com.sza.fastmediasorter.core.security.PrivateDiagnosticValues
import com.sza.fastmediasorter.data.local.db.CryptoHelper
import com.sza.fastmediasorter.domain.model.PreparedBroadcastLink
import com.sza.fastmediasorter.domain.repository.PreparedBroadcastLinkStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4247: the prepared broadcasts as one Gson list encrypted with the Keystore-backed [CryptoHelper], in the same
 * backup-excluded preferences DataStore as [KeystoreBroadcastLinkPasswordStore]. A restored copy decrypts to
 * nothing, so no prepared secret survives a backup or a device transfer (DEVICE-EXCHANGE item DW).
 */
@Singleton
class KeystorePreparedBroadcastLinkStore internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val encrypt: (String) -> String?,
    private val decrypt: (String?) -> String?,
) : PreparedBroadcastLinkStore {

    // The Keystore has no JVM implementation, so the cipher is a seam only a unit test replaces.
    @Inject
    constructor(dataStore: DataStore<Preferences>) : this(dataStore, CryptoHelper::encrypt, CryptoHelper::decrypt)

    private val gson = Gson()
    private val listType = object : TypeToken<List<PreparedBroadcastLink>>() {}.type

    override val links: Flow<List<PreparedBroadcastLink>> = dataStore.data.map { decode(it[KEY]) }

    override suspend fun all(): List<PreparedBroadcastLink> = decode(dataStore.data.first()[KEY])

    override suspend fun put(link: PreparedBroadcastLink): Boolean =
        write { list -> list.filterNot { it.broadcastId == link.broadcastId } + link }

    override suspend fun remove(broadcastId: String) {
        write { list -> list.filterNot { it.broadcastId == broadcastId } }
    }

    override suspend fun markConsumed(broadcastId: String) {
        write { list ->
            list.map {
                if (it.broadcastId != broadcastId) {
                    it
                } else {
                    it.copy(
                        consumed = true,
                        linkPassword = null,
                        relayLinkKey = null,
                        tunnelPin = null,
                        guests = emptyList(),
                    )
                }
            }
        }
    }

    private suspend fun write(change: (List<PreparedBroadcastLink>) -> List<PreparedBroadcastLink>): Boolean {
        var written = false
        dataStore.edit { prefs ->
            val next = change(decode(prefs[KEY]))
            next.forEach { PrivateDiagnosticValues.register(it.linkPassword) }
            val encrypted = encrypt(gson.toJson(next, listType))?.takeUnless(String::isEmpty)
            if (encrypted != null) {
                prefs[KEY] = encrypted
                written = true
            }
        }
        return written
    }

    private fun decode(stored: String?): List<PreparedBroadcastLink> {
        val json = stored?.let(decrypt)
        val list = try {
            json?.let { gson.fromJson<List<PreparedBroadcastLink>>(it, listType) }
        } catch (e: JsonParseException) {
            Timber.w(e, "KeystorePreparedBroadcastLinkStore: the prepared links could not be read")
            null
        }
        if (stored != null && list == null) {
            Timber.w("KeystorePreparedBroadcastLinkStore: the prepared links did not decrypt, reading none")
        }
        list?.forEach { PrivateDiagnosticValues.register(it.linkPassword) }
        return list.orEmpty()
    }

    private companion object {
        val KEY = stringPreferencesKey("broadcast_prepared_links")
    }
}
