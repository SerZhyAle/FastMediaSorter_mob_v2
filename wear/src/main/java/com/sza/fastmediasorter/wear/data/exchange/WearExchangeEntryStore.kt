package com.sza.fastmediasorter.wear.data.exchange

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import com.sza.fastmediasorter.wear.di.EncryptedPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** S4158: where the watch keeps its exchange entries and the device token of each. */
interface WearExchangeEntryStore {

    val entries: Flow<List<WearExchangeEntry>>

    suspend fun all(): List<WearExchangeEntry>

    /** Replaces the entry of the same identity, or appends it. */
    suspend fun put(entry: WearExchangeEntry)

    suspend fun remove(identity: String)

    /** Marks the entry of [identity] outgoing and every other one not; an unknown identity changes nothing. */
    suspend fun setOutgoing(identity: String)

    suspend fun token(identity: String): String?

    /** False when the secret store refused to keep it, so the caller never believes a lost write. */
    suspend fun saveToken(identity: String, token: String): Boolean

    suspend fun forgetToken(identity: String)
}

/**
 * The entries and tokens in the watch's Keystore-backed preferences (contract DEVICE-EXCHANGE 5.2): the token is
 * a credential, and the address and login are private, so both live in the encrypted file under keys no other
 * store reads. No backup copy exists (strategic 3.2): a restored watch enrolls again from the phone.
 */
@Singleton
class EncryptedWearExchangeEntryStore @Inject constructor(
    @EncryptedPrefs private val prefs: SharedPreferences,
    private val gson: Gson,
) : WearExchangeEntryStore {

    private val writes = Mutex()

    // Null until the file was read once; every later read and write goes through this copy.
    private val state = MutableStateFlow<List<WearExchangeEntry>?>(null)

    override val entries: Flow<List<WearExchangeEntry>> = state
        .onStart { if (state.value == null) all() }
        .filterNotNull()

    override suspend fun all(): List<WearExchangeEntry> = withContext(Dispatchers.IO) { load() }

    override suspend fun put(entry: WearExchangeEntry) = mutate { held ->
        val index = held.indexOfFirst { it.identity == entry.identity }
        if (index < 0) held + entry else held.toMutableList().apply { set(index, entry) }
    }

    override suspend fun remove(identity: String) = mutate { held -> held.filterNot { it.identity == identity } }

    override suspend fun setOutgoing(identity: String) = mutate { held ->
        if (held.none { it.identity == identity }) held else held.map { it.copy(outgoing = it.identity == identity) }
    }

    override suspend fun token(identity: String): String? =
        withContext(Dispatchers.IO) { prefs.getString(tokenKey(identity), null) }

    override suspend fun saveToken(identity: String, token: String): Boolean =
        withContext(Dispatchers.IO) { prefs.edit().putString(tokenKey(identity), token).commit() }

    override suspend fun forgetToken(identity: String) {
        withContext(Dispatchers.IO) { prefs.edit().remove(tokenKey(identity)).commit() }
    }

    private suspend fun mutate(change: (List<WearExchangeEntry>) -> List<WearExchangeEntry>) {
        withContext(Dispatchers.IO) {
            writes.withLock {
                val next: List<WearExchangeEntry> = change(load())
                val stored: Array<WearExchangeEntry> = next.toTypedArray()
                if (!prefs.edit().putString(KEY_ENTRIES, gson.toJson(stored)).commit()) {
                    Timber.w("Exchange entries: the encrypted store refused the write")
                }
                state.value = next
            }
        }
    }

    private fun load(): List<WearExchangeEntry> {
        state.value?.let { return it }
        val parsed = try {
            prefs.getString(KEY_ENTRIES, null)
                ?.let { gson.fromJson<List<WearExchangeEntry>>(it, ENTRY_LIST) }
                .orEmpty()
        } catch (e: JsonParseException) {
            // A file this build cannot read is lost to it either way; the phone can hand every entry over again.
            Timber.w("Exchange entries: the stored list could not be read (%s)", e.javaClass.simpleName)
            emptyList()
        }
        return parsed.filter(::isComplete).also { state.value = it }
    }

    // Gson ignores Kotlin nullability, so a damaged record arrives with null in a non-null field.
    private fun isComplete(entry: WearExchangeEntry): Boolean {
        val required: List<String?> = listOf(entry.host, entry.login, entry.deviceId)
        return required.none { it.isNullOrBlank() }
    }

    private fun tokenKey(identity: String) = KEY_TOKEN_PREFIX + identity

    private companion object {
        const val KEY_ENTRIES = "exchange_entries_v1"
        const val KEY_TOKEN_PREFIX = "exchange_device_token_v1/"
        val ENTRY_LIST = object : TypeToken<List<WearExchangeEntry>>() {}.type
    }
}
