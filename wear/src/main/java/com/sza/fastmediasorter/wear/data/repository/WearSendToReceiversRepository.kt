package com.sza.fastmediasorter.wear.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.sza.fastmediasorter.wear.domain.model.WearSendToReceiverEntry
import com.sza.fastmediasorter.wear.domain.model.WearSendToReceiversPayload
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
 * S2142: the «Send to..» receivers the phone offers, kept as a cache of the phone's own decision.
 *
 * Deliberately its own store rather than a field of the watch's settings: these are not the owner's
 * settings but a derivative of them, and [com.sza.fastmediasorter.wear.data.preferences
 * .WearPreferencesRepositoryImpl] is one of the six mirrors `assert-wear-settings-parity.ps1`
 * compares, where every entry owes an ownership and an exception reason this list cannot give.
 *
 * Shaped after [WearPhonePinsRepository], including the reason it persists: a watch restarted out of
 * range of the phone still offers the last known list rather than nothing. Absence of a connection
 * means "last known", never "empty".
 */
@Singleton
class WearSendToReceiversRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {

    private val dir: File get() = File(context.filesDir, "send-to")
    private val receiversFile: File get() = File(dir, "receivers.json")

    private val _receivers = MutableStateFlow<List<WearSendToReceiverEntry>>(emptyList())

    init {
        // Read here rather than on first collect, for the neighbouring store's reason: the menu asks
        // for this list while composing, and an empty first emission would draw the menu once
        // without its receivers and then reshuffle it under the reader's finger.
        _receivers.value = readFromFile()
    }

    fun observe(): StateFlow<List<WearSendToReceiverEntry>> = _receivers.asStateFlow()

    /**
     * Replaces the whole list. An empty list is a real value - it is how switching the last receiver
     * off on the phone reaches this watch - and is never read as "nothing arrived".
     */
    suspend fun replaceAll(entries: List<WearSendToReceiverEntry>) = withContext(Dispatchers.IO) {
        val usableEntries = usable(entries)
        dir.mkdirs()
        // Stored in the same envelope the channel delivers, rather than as a bare array: one shape
        // for the wire and the file means the pinned wire names cover the stored copy too, and the
        // durable-persistence gate can see that they do.
        val json = gson.toJson(WearSendToReceiversPayload(usableEntries))
        writeAtomically(receiversFile, json.toByteArray(Charsets.UTF_8))
        _receivers.value = usableEntries
        Timber.d("WearSendToReceiversRepository: stored ${usableEntries.size} send-to receiver(s)")
        Timber.d("S2142: receiver list arrived from the phone, ${usableEntries.size} usable")
    }

    /**
     * Gson builds through Unsafe: it honours neither Kotlin nullability nor a declared default, so a
     * payload missing `id` or `title` yields an entry whose non-null fields are null and throws far
     * from here, while drawing the menu row - past the catch that was meant to keep the previous list.
     * Dropping the entry costs one receiver; letting it through costs the screen.
     */
    @Suppress("USELESS_ELVIS")
    private fun usable(entries: List<WearSendToReceiverEntry>): List<WearSendToReceiverEntry> =
        // The elvis operators are what the compiler calls useless and the runtime does not: these
        // fields are declared non-null, and Gson is exactly the way a null gets into one. Reading
        // `it.id.isNotBlank()` directly would throw the very NPE this function exists to prevent.
        entries.filter { (it.id ?: "").isNotBlank() && (it.title ?: "").isNotBlank() }

    private fun readFromFile(): List<WearSendToReceiverEntry> {
        val file = receiversFile
        if (!file.isFile) return emptyList()
        return try {
            val json = file.readText(Charsets.UTF_8)
            usable(gson.fromJson(json, WearSendToReceiversPayload::class.java)?.receivers.orEmpty())
        } catch (e: IOException) {
            Timber.w(e, "WearSendToReceiversRepository: failed to read stored receivers")
            emptyList()
        } catch (e: JsonSyntaxException) {
            Timber.w(e, "WearSendToReceiversRepository: failed to read stored receivers")
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
