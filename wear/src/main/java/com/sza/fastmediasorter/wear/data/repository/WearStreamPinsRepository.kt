package com.sza.fastmediasorter.wear.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.sza.fastmediasorter.wear.domain.model.WearStreamPinDeltaItem
import com.sza.fastmediasorter.wear.domain.model.WearStreamPinsDeltaPayload
import com.sza.fastmediasorter.wear.domain.model.WearStreamPinsPayload
import com.sza.fastmediasorter.wear.domain.model.appendStreamPinDelta
import com.sza.fastmediasorter.wear.domain.model.foldWearStreamIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2497: the watch-authored stream pins store with offline-queued delta synchronisation to the phone.
 *
 * Separated from [WearFavoritesRepository] and [WearPhonePinsRepository]:
 * - Favourites represent media favorites (Heart icon) displayed on the Favorites screen.
 * - Pins represent stream ranking top-group placement (Pin icon) displayed on the Streams list.
 * - Phone pins represent the cache of the phone's decision.
 *
 * Persisted locally so that a watch offline or restarted preserves both the pinned order and any
 * pending pin deltas awaiting delivery to the phone.
 */
@Singleton
class WearStreamPinsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val phonePinsRepository: WearPhonePinsRepository
) {

    private val dir: File get() = File(context.filesDir, "streams")
    private val watchPinsFile: File get() = File(dir, "watch_pins.json")
    private val pendingDeltasFile: File get() = File(dir, "pending_pin_deltas.json")

    private val _watchPins = MutableStateFlow<Set<String>>(emptySet())

    init {
        _watchPins.value = readWatchPinsFromFile()
    }

    /** Observes stream identities pinned locally on this watch. */
    fun observeWatchPins(): StateFlow<Set<String>> = _watchPins.asStateFlow()

    /** Observes stream identities pinned on the phone. */
    fun observePhonePins(): StateFlow<Set<String>> = phonePinsRepository.observe()

    /** Synchronous snapshot of watch-pinned stream identities. */
    fun getWatchPins(): Set<String> = _watchPins.value

    /** Checks whether a stream is pinned either on this watch or on the phone. */
    fun isPinned(urlOrIdentity: String): Boolean {
        val identity = foldWearStreamIdentity(urlOrIdentity)
        return identity in _watchPins.value || identity in phonePinsRepository.observe().value
    }

    /** Checks whether a stream is pinned specifically on this watch. */
    fun isPinnedOnWatch(urlOrIdentity: String): Boolean {
        val identity = foldWearStreamIdentity(urlOrIdentity)
        return identity in _watchPins.value
    }

    /** Toggles the watch-pinned state for a stream and queues a delta. Returns the new state. */
    suspend fun togglePin(urlOrIdentity: String): Boolean = withContext(Dispatchers.IO) {
        val identity = foldWearStreamIdentity(urlOrIdentity)
        val wasPinned = identity in _watchPins.value
        val newPinned = !wasPinned
        setPinInternal(identity, newPinned)
        newPinned
    }

    /** Sets the watch-pinned state explicitly and queues a delta. */
    suspend fun setPin(urlOrIdentity: String, isPinned: Boolean) = withContext(Dispatchers.IO) {
        val identity = foldWearStreamIdentity(urlOrIdentity)
        setPinInternal(identity, isPinned)
    }

    private fun setPinInternal(identity: String, isPinned: Boolean) {
        val current = _watchPins.value.toMutableSet()
        val changed = if (isPinned) current.add(identity) else current.remove(identity)
        if (changed) {
            dir.mkdirs()
            val pinsPayload: WearStreamPinsPayload = WearStreamPinsPayload(identities = current.toList())
            val pinsJson = gson.toJson(pinsPayload)
            writeAtomically(watchPinsFile, pinsJson.toByteArray(Charsets.UTF_8))
            _watchPins.value = current

            val deltas = readPendingDeltasFromFile()
            val item = WearStreamPinDeltaItem(
                urlOrIdentity = identity,
                isPinned = isPinned,
                changedAt = System.currentTimeMillis()
            )
            val updatedDeltas = appendStreamPinDelta(deltas, item)
            val deltaPayload: WearStreamPinsDeltaPayload = WearStreamPinsDeltaPayload(items = updatedDeltas)
            val deltasJson = gson.toJson(deltaPayload)
            writeAtomically(pendingDeltasFile, deltasJson.toByteArray(Charsets.UTF_8))
            Timber.d(
                "WearStreamPinsRepository: stream $identity pin=$isPinned saved, pending deltas=${updatedDeltas.size}"
            )
        }
    }

    suspend fun getPendingDelta(): List<WearStreamPinDeltaItem> = withContext(Dispatchers.IO) {
        readPendingDeltasFromFile()
    }

    suspend fun clearPendingDelta() = withContext(Dispatchers.IO) {
        val deltaPayload: WearStreamPinsDeltaPayload = WearStreamPinsDeltaPayload(items = emptyList())
        val deltasJson = gson.toJson(deltaPayload)
        writeAtomically(pendingDeltasFile, deltasJson.toByteArray(Charsets.UTF_8))
    }

    private fun readWatchPinsFromFile(): Set<String> {
        val file = watchPinsFile
        if (!file.isFile) return emptySet()
        return try {
            val json = file.readText(Charsets.UTF_8)
            val payload = gson.fromJson(json, WearStreamPinsPayload::class.java)
            payload?.identities?.toSet().orEmpty()
        } catch (e: IOException) {
            Timber.w(e, "WearStreamPinsRepository: failed to read watch pins")
            emptySet()
        } catch (e: JsonSyntaxException) {
            Timber.w(e, "WearStreamPinsRepository: failed to read watch pins")
            emptySet()
        }
    }

    private fun readPendingDeltasFromFile(): List<WearStreamPinDeltaItem> {
        val file = pendingDeltasFile
        if (!file.isFile) return emptyList()
        return try {
            val json = file.readText(Charsets.UTF_8)
            val payload = gson.fromJson(json, WearStreamPinsDeltaPayload::class.java)
            payload?.items.orEmpty()
        } catch (e: IOException) {
            Timber.w(e, "WearStreamPinsRepository: failed to read pending deltas")
            emptyList()
        } catch (e: JsonSyntaxException) {
            Timber.w(e, "WearStreamPinsRepository: failed to read pending deltas")
            emptyList()
        }
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.writeBytes(bytes)
            tmp.delete()
        }
    }
}
