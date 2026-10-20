package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.fastmediasorter.core.security.PrivateDiagnosticValues
import com.sza.fastmediasorter.data.local.db.CryptoHelper
import com.sza.fastmediasorter.domain.model.ExchangeGrant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The grants this device made (contract DEVICE-EXCHANGE item BD: kept on disk - id, target, guest login) and the
 * password of each grant's login, encrypted with the Keystore-backed [CryptoHelper] in the preferences DataStore
 * that backup and device transfer exclude. A restored copy decrypts to nothing, so a grant without its password
 * authenticates nobody until it is withdrawn and made again.
 */
@Singleton
class ExchangeGrantRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val encrypt: (String) -> String?,
    private val decrypt: (String?) -> String?,
) {

    // The Keystore has no JVM implementation, so the cipher is a seam only a unit test replaces.
    @Inject
    constructor(dataStore: DataStore<Preferences>) : this(dataStore, CryptoHelper::encrypt, CryptoHelper::decrypt)

    val grants: Flow<List<ExchangeGrant>> = dataStore.data.map { decodeList(it[KEY_LIST]) }

    suspend fun snapshot(): List<ExchangeGrant> = grants.first()

    /** False when the secret store refused the password, so nothing is kept for a grant that could not log in. */
    suspend fun add(grant: ExchangeGrant, password: String): Boolean {
        PrivateDiagnosticValues.register(password)
        val encrypted = encrypt(password)?.takeUnless(String::isEmpty) ?: return false
        dataStore.edit { prefs ->
            val kept = decodeList(prefs[KEY_LIST]).filterNot { it.grantId == grant.grantId }
            prefs[KEY_LIST] = encodeList(kept + grant)
            prefs[passwordKey(grant.login)] = encrypted
        }
        return true
    }

    suspend fun remove(grantId: String) {
        dataStore.edit { prefs ->
            val all = decodeList(prefs[KEY_LIST])
            all.filter { it.grantId == grantId }.forEach { prefs.remove(passwordKey(it.login)) }
            prefs[KEY_LIST] = encodeList(all.filterNot { it.grantId == grantId })
        }
    }

    suspend fun password(login: String): String? =
        dataStore.data.first()[passwordKey(login)]?.let(decrypt)?.takeUnless(String::isEmpty)
            ?.also { PrivateDiagnosticValues.register(it) }

    /**
     * The guests kept for a broadcast source (item BD): their logins only, since a broadcast grant's pair is made
     * again at every `broadcast-start` and never leaves memory.
     */
    suspend fun broadcastGuests(sourceKey: String): List<String> =
        decodeLogins(dataStore.data.first()[guestsKey(sourceKey)])

    suspend fun keepBroadcastGuest(sourceKey: String, guest: String) {
        dataStore.edit { prefs ->
            val kept = decodeLogins(prefs[guestsKey(sourceKey)])
            if (guest !in kept) prefs[guestsKey(sourceKey)] = encodeLogins(kept + guest)
        }
    }

    suspend fun forgetBroadcastGuest(sourceKey: String, guest: String) {
        dataStore.edit { prefs ->
            val kept = decodeLogins(prefs[guestsKey(sourceKey)]).filterNot { it == guest }
            if (kept.isEmpty()) {
                prefs.remove(guestsKey(sourceKey))
            } else {
                prefs[guestsKey(sourceKey)] = encodeLogins(kept)
            }
        }
    }

    private fun passwordKey(login: String): Preferences.Key<String> = stringPreferencesKey(PASSWORD_PREFIX + login)

    private fun guestsKey(sourceKey: String): Preferences.Key<String> =
        stringPreferencesKey(BROADCAST_GUESTS_PREFIX + sourceKey)

    private fun encodeLogins(logins: List<String>): String = JsonArray().apply { logins.forEach(::add) }.toString()

    private fun decodeLogins(raw: String?): List<String> {
        val array = raw?.let { runCatching { JsonParser.parseString(it) }.getOrNull() } as? JsonArray
        return array?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive && e.asJsonPrimitive.isString }?.asString }
            .orEmpty()
    }

    private fun encodeList(grants: List<ExchangeGrant>): String = JsonArray().apply {
        grants.forEach { grant ->
            add(
                JsonObject().apply {
                    addProperty(F_ID, grant.grantId)
                    addProperty(F_KIND, grant.kind.name)
                    addProperty(F_ROOT, grant.rootUri)
                    addProperty(F_LOGIN, grant.login)
                    grant.guest?.let { addProperty(F_GUEST, it) }
                    grant.linkShareId?.let { addProperty(F_SHARE_ID, it) }
                    grant.rootName?.let { addProperty(F_ROOT_NAME, it) }
                }
            )
        }
    }.toString()

    // A damaged list reads as no grants: every grant login then fails, which is the safe direction.
    private fun decodeList(raw: String?): List<ExchangeGrant> {
        val array = raw?.let { runCatching { JsonParser.parseString(it) }.getOrNull() } as? JsonArray
        return array?.mapNotNull { (it as? JsonObject)?.let(::decodeGrant) }.orEmpty()
    }

    private fun decodeGrant(item: JsonObject): ExchangeGrant? {
        val kind = item.text(F_KIND)?.let { name -> ExchangeGrant.Kind.entries.firstOrNull { it.name == name } }
        val required = listOf(F_ID, F_ROOT, F_LOGIN).map { item.text(it) }
        if (kind == null || null in required) return null
        val (id, root, login) = required.map(::checkNotNull)
        return ExchangeGrant(id, kind, root, login, item.text(F_GUEST), item.text(F_SHARE_ID), item.text(F_ROOT_NAME))
    }

    private fun JsonObject.text(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private companion object {
        val KEY_LIST = stringPreferencesKey("exchange_grants_v1")
        const val PASSWORD_PREFIX = "exchange_grant_password_"
        const val BROADCAST_GUESTS_PREFIX = "exchange_broadcast_guests_"
        const val F_ID = "grantId"
        const val F_KIND = "kind"
        const val F_ROOT = "root"
        const val F_LOGIN = "login"
        const val F_GUEST = "guest"
        const val F_SHARE_ID = "shareId"
        const val F_ROOT_NAME = "rootName"
    }
}
