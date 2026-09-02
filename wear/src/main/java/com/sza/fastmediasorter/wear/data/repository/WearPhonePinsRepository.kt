package com.sza.fastmediasorter.wear.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
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
 * S2149: the set of stream identities the phone has pinned, kept as a cache of someone else's decision.
 *
 * Deliberately not the watch's own favourites store. The star on the watch means "I marked this here",
 * so a pin that arrived from the phone must not be writable by the watch and must be withdrawable by
 * the phone alone - which works only because an arriving set replaces the previous one whole.
 *
 * The set is persisted, so a watch restarted out of range of the phone still ranks by the last known
 * set rather than by nothing. Absence of a connection means "last known", never "empty".
 */
@Singleton
class WearPhonePinsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {

    private val dir: File get() = File(context.filesDir, "streams")
    private val pinsFile: File get() = File(dir, "phone_pins.json")

    private val _pins = MutableStateFlow<Set<String>>(emptySet())

    init {
        // Read here rather than on first collect for the same reason the neighbouring channel store
        // does: the projection asks for this set while composing, and an empty first emission would
        // draw the list once without the top group and then reshuffle it under the reader.
        _pins.value = readFromFile()
    }

    fun observe(): StateFlow<Set<String>> = _pins.asStateFlow()

    /**
     * Replaces the whole set. An empty list is a real value - it is how an unpin of the last channel
     * on the phone reaches this watch - and is never treated as "nothing arrived".
     */
    suspend fun replaceAll(identities: List<String>) = withContext(Dispatchers.IO) {
        val received = identities.toSet()
        dir.mkdirs()
        val json = gson.toJson(identities.toTypedArray())
        writeAtomically(pinsFile, json.toByteArray(Charsets.UTF_8))
        _pins.value = received
        Timber.d("WearPhonePinsRepository: stored ${received.size} phone-pinned identities")
    }

    private fun readFromFile(): Set<String> {
        val file = pinsFile
        if (!file.isFile) return emptySet()
        return try {
            val json = file.readText(Charsets.UTF_8)
            // A plain string array, not a generic collection type token: the persisted shape carries
            // no model, so there are no wire names to pin against minification.
            gson.fromJson(json, Array<String>::class.java).orEmpty().toSet()
        } catch (e: IOException) {
            Timber.w(e, "WearPhonePinsRepository: failed to read phone pins")
            emptySet()
        } catch (e: JsonSyntaxException) {
            Timber.w(e, "WearPhonePinsRepository: failed to read phone pins")
            emptySet()
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
