package com.sza.fastmediasorter.wear.data.exchange

import android.content.SharedPreferences
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.sza.fastmediasorter.wear.di.EncryptedPrefs
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastPass
import com.sza.fastmediasorter.wear.domain.model.normalizeWearStreamUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** S4261: what the phone handed over for one channel - the broadcast's id, its descriptor and its pass. */
class WearStoredBroadcast(val broadcastId: String, val descriptor: JsonObject, val pass: WearBroadcastPass?) {

    // DEVICE-EXCHANGE item DK: a sealed endpoint's key opens the broadcast; 7.9: the id is a capability.
    override fun toString(): String = "WearStoredBroadcast(protected=${pass != null})"
}

/**
 * S4261: where the watch keeps a broadcast handed off from the phone (LIVE-BROADCAST item P), keyed by the channel's
 * URL. The descriptor may carry `seal` keys and the pass a password, so neither belongs in the channel file.
 */
interface WearBroadcastPassStore {

    suspend fun get(url: String): WearStoredBroadcast?

    /** False when the secret store refused to keep it, so the caller never reports a lost write as stored. */
    suspend fun save(url: String, value: WearStoredBroadcast): Boolean

    suspend fun forget(url: String)

    /** Forgets every stored broadcast whose channel URL is not in [urls]. */
    suspend fun retainOnly(urls: Set<String>)
}

/**
 * The broadcasts in the watch's Keystore-backed preferences, the file backup and device transfer exclude, as
 * [EncryptedWearResourcePinStore] keeps PINs. The key is a digest of the normalised URL, so the file names no channel.
 */
@Singleton
class EncryptedWearBroadcastPassStore @Inject constructor(
    @EncryptedPrefs private val prefs: SharedPreferences,
) : WearBroadcastPassStore {

    override suspend fun get(url: String): WearStoredBroadcast? =
        withContext(Dispatchers.IO) { prefs.getString(key(url), null)?.let(::decode) }

    override suspend fun save(url: String, value: WearStoredBroadcast): Boolean =
        withContext(Dispatchers.IO) { prefs.edit().putString(key(url), encode(value)).commit() }

    override suspend fun forget(url: String) {
        withContext(Dispatchers.IO) { prefs.edit().remove(key(url)).commit() }
    }

    override suspend fun retainOnly(urls: Set<String>) {
        withContext(Dispatchers.IO) {
            val kept = urls.mapTo(HashSet(), ::key)
            val dropped = prefs.all.keys.filter { it.startsWith(KEY_PREFIX) && it !in kept }
            if (dropped.isNotEmpty()) {
                prefs.edit().apply { dropped.forEach(::remove) }.commit()
            }
        }
    }

    private fun encode(value: WearStoredBroadcast): String = JsonObject().apply {
        addProperty(KEY_BROADCAST_ID, value.broadcastId)
        add(KEY_DESCRIPTOR, value.descriptor)
        value.pass?.let { pass ->
            val member = JsonObject()
            member.addProperty(KEY_USER, pass.user)
            member.addProperty(KEY_PASSWORD, pass.password)
            add(KEY_PASS, member)
        }
    }.toString()

    private fun decode(text: String): WearStoredBroadcast? = try {
        val root = JsonParser.parseString(text).asJsonObject
        val descriptor = root.get(KEY_DESCRIPTOR)?.takeIf(JsonElement::isJsonObject)?.asJsonObject
        val pass = root.get(KEY_PASS)?.takeIf(JsonElement::isJsonObject)?.asJsonObject?.let { member ->
            WearBroadcastPass(member.get(KEY_USER)?.asString.orEmpty(), member.get(KEY_PASSWORD)?.asString.orEmpty())
        }
        descriptor?.let { WearStoredBroadcast(root.get(KEY_BROADCAST_ID)?.asString.orEmpty(), it, pass) }
    } catch (e: JsonParseException) {
        // The value is a secret, so only the exception class is logged; the channel then plays as unavailable.
        Timber.w("EncryptedWearBroadcastPassStore: a stored broadcast does not parse - %s", e.javaClass.simpleName)
        null
    } catch (e: IllegalStateException) {
        Timber.w("EncryptedWearBroadcastPassStore: a stored broadcast has the wrong shape - %s", e.javaClass.simpleName)
        null
    }

    private fun key(url: String): String {
        val digest = MessageDigest.getInstance(DIGEST).digest(normalizeWearStreamUrl(url).toByteArray(Charsets.UTF_8))
        return KEY_PREFIX + digest.joinToString("") { String.format(Locale.ROOT, "%02x", it) }
    }

    private companion object {
        const val KEY_PREFIX = "broadcast_pass_v1/"
        const val DIGEST = "SHA-256"
        const val KEY_BROADCAST_ID = "broadcast_id"
        const val KEY_DESCRIPTOR = "descriptor"
        const val KEY_PASS = "pass"
        const val KEY_USER = "user"
        const val KEY_PASSWORD = "password"
    }
}
