package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.security.PrivateDiagnosticValues
import com.sza.fastmediasorter.data.local.db.CryptoHelper
import com.sza.fastmediasorter.domain.model.PreparedBroadcastLink
import com.sza.fastmediasorter.domain.repository.PreparedBroadcastLinkStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
    private val ioDispatcher: CoroutineDispatcher,
) : PreparedBroadcastLinkStore {

    // The Keystore has no JVM implementation, so the cipher is a seam only a unit test replaces.
    @Inject
    constructor(
        dataStore: DataStore<Preferences>,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(dataStore, CryptoHelper::encrypt, CryptoHelper::decrypt, ioDispatcher)

    private val gson = Gson()
    private val listType = object : TypeToken<List<PreparedBroadcastLink>>() {}.type

    // The DataStore is shared by every app setting, so the upstream is narrowed to this key and deduplicated
    // before the Keystore decrypt: otherwise any unrelated setting write re-ran a binder call plus a Gson parse
    // on the collector's thread, which is the main thread for the list that rebuilds from this flow.
    override val links: Flow<List<PreparedBroadcastLink>> = dataStore.data
        .map { it[KEY] }
        .distinctUntilChanged()
        .map { decode(it).orEmpty() }
        .flowOn(ioDispatcher)

    override suspend fun all(): List<PreparedBroadcastLink> = allOrNull().orEmpty()

    override suspend fun allOrNull(): List<PreparedBroadcastLink>? {
        val stored = dataStore.data.first()[KEY]
        return withContext(ioDispatcher) { decode(stored) }
    }

    // Dropping a list that never decrypts again is its own decision, never a side effect of the next write.
    override suspend fun discardUnreadable(): Boolean {
        var discarded = false
        dataStore.edit { prefs ->
            val stored = prefs[KEY]
            if (stored != null && decode(stored) == null) {
                prefs.remove(KEY)
                discarded = true
            }
        }
        if (discarded) Timber.w("KeystorePreparedBroadcastLinkStore: the unreadable prepared links were discarded")
        return discarded
    }

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
            // An unreadable list is not an empty one: writing from it would overwrite every intact ciphertext.
            val current = decode(prefs[KEY])
            if (current == null) {
                Timber.w("KeystorePreparedBroadcastLinkStore: the stored prepared links are unreadable, not written")
                return@edit
            }
            val next = change(current)
            next.forEach { PrivateDiagnosticValues.register(it.linkPassword) }
            val encrypted = encrypt(gson.toJson(next, listType))?.takeUnless(String::isEmpty)
            if (encrypted != null) {
                prefs[KEY] = encrypted
                written = true
            }
        }
        return written
    }

    /** Empty for an absent key; null for a stored string that does not decrypt or parse. */
    private fun decode(stored: String?): List<PreparedBroadcastLink>? {
        if (stored == null) return emptyList()
        val json = decrypt(stored)
        val list = try {
            json?.let { gson.fromJson<List<PreparedBroadcastLink>>(it, listType) }
        } catch (e: JsonParseException) {
            Timber.w(e, "KeystorePreparedBroadcastLinkStore: the prepared links could not be read")
            null
        }
        if (list == null) {
            Timber.w("KeystorePreparedBroadcastLinkStore: the prepared links did not decrypt")
        }
        list?.forEach { PrivateDiagnosticValues.register(it.linkPassword) }
        return list
    }

    private companion object {
        val KEY = stringPreferencesKey("broadcast_prepared_links")
    }
}
