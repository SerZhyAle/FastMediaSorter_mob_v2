package com.sza.fastmediasorter.data.remotecontrol

import android.content.Context
import android.util.AtomicFile
import com.sza.fastmediasorter.domain.remotecontrol.RemoteCommandLogEntry
import com.sza.fastmediasorter.domain.remotecontrol.RemoteRiskClass
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The on-device command log of REMOTE-CONTROL rule 11: one line per opened request, arguments never stored,
 * nothing sent anywhere. Under noBackupFilesDir so it never leaves the phone through a backup.
 */
@Singleton
class RemoteCommandLog @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val file by lazy { AtomicFile(File(context.noBackupFilesDir, LOG_FILE)) }
    private val mutex = Mutex()
    private val loaded = MutableStateFlow(false)
    private val cache = MutableStateFlow<List<RemoteCommandLogEntry>>(emptyList())
    val entries: StateFlow<List<RemoteCommandLogEntry>> = cache.asStateFlow()

    suspend fun refresh() = locked { ensureLoaded() }

    suspend fun append(entry: RemoteCommandLogEntry) = locked {
        ensureLoaded()
        val next = (cache.value + entry).takeLast(MAX_ENTRIES)
        save(next)
        cache.value = next
    }

    private suspend fun <T> locked(block: () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { block() } }

    private fun ensureLoaded() {
        if (loaded.value) return
        cache.value = load()
        loaded.value = true
    }

    private fun load(): List<RemoteCommandLogEntry> = try {
        val array = JSONArray(file.readFully().decodeToString())
        (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
    } catch (_: FileNotFoundException) {
        emptyList()
    } catch (e: JSONException) {
        Timber.w(e, "Remote-command log unreadable; starting empty")
        emptyList()
    }

    private fun save(entries: List<RemoteCommandLogEntry>) {
        val array = JSONArray()
        entries.forEach { array.put(toJson(it)) }
        val stream = file.startWrite()
        try {
            stream.write(array.toString().encodeToByteArray())
            file.finishWrite(stream)
        } catch (e: IOException) {
            file.failWrite(stream)
            Timber.w(e, "Remote-command log not written")
        }
    }

    private fun toJson(entry: RemoteCommandLogEntry): JSONObject = JSONObject()
        .put(KEY_TIME, entry.timeMillis)
        .put(KEY_NAME, entry.controllerName ?: JSONObject.NULL)
        .put(KEY_DEVICE, entry.deviceId)
        .put(KEY_VERIFIED, entry.verified)
        .put(KEY_VERB, entry.verb ?: JSONObject.NULL)
        .put(KEY_CLASS, entry.riskClass?.wire ?: JSONObject.NULL)
        .put(KEY_STATUS, entry.status)
        .put(KEY_REASON, entry.reason ?: JSONObject.NULL)

    private fun fromJson(json: JSONObject) = RemoteCommandLogEntry(
        timeMillis = json.optLong(KEY_TIME),
        controllerName = json.nullableString(KEY_NAME),
        deviceId = json.optString(KEY_DEVICE),
        verified = json.optBoolean(KEY_VERIFIED),
        verb = json.nullableString(KEY_VERB),
        riskClass = RemoteRiskClass.fromWire(json.nullableString(KEY_CLASS)),
        status = json.optString(KEY_STATUS),
        reason = json.nullableString(KEY_REASON),
    )

    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else optString(key)

    companion object {
        internal const val LOG_FILE = "remote_command_log.json"
        private const val MAX_ENTRIES = 200
        private const val KEY_TIME = "t"
        private const val KEY_NAME = "n"
        private const val KEY_DEVICE = "d"
        private const val KEY_VERIFIED = "ok"
        private const val KEY_VERB = "v"
        private const val KEY_CLASS = "c"
        private const val KEY_STATUS = "s"
        private const val KEY_REASON = "why"
    }
}
