package com.sza.fastmediasorter.data.remotecontrol

import android.content.Context
import android.util.AtomicFile
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.sza.fastmediasorter.domain.remotecontrol.RemoteCommandAnswer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
 * Requests already answered, kept on disk until `issuedAt + ttlSeconds + 300 s` on this device's clock
 * (REMOTE-CONTROL rule 12), so a repeated box - even after a restart - gets the first answer and never runs twice.
 * Under noBackupFilesDir: a restored ring would make another device refuse requests it never saw.
 */
@Singleton
class RemoteCommandRing @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    internal var now: () -> Long = System::currentTimeMillis
    private val file by lazy { AtomicFile(File(context.noBackupFilesDir, RING_FILE)) }
    private val mutex = Mutex()

    suspend fun find(controllerId: String, requestId: String): RemoteCommandAnswer? = locked {
        load().firstOrNull { it.controllerId == controllerId && it.requestId == requestId }?.answer
    }

    suspend fun record(controllerId: String, requestId: String, answer: RemoteCommandAnswer, keepUntilMillis: Long) =
        locked {
            val kept = load().filterNot { it.controllerId == controllerId && it.requestId == requestId }
            save((kept + RingEntry(controllerId, requestId, keepUntilMillis, answer)).takeLast(MAX_ENTRIES))
        }

    private suspend fun <T> locked(block: () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { block() } }

    private fun load(): List<RingEntry> {
        val text = try {
            file.readFully().decodeToString()
        } catch (_: FileNotFoundException) {
            return emptyList()
        }
        val cutoff = now()
        return try {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { RingEntry.from(array.getJSONObject(it)) }
                .filter { it.keepUntilMillis >= cutoff }
        } catch (e: JSONException) {
            Timber.w(e, "Remote-command ring unreadable; starting empty")
            emptyList()
        }
    }

    private fun save(entries: List<RingEntry>) {
        val cutoff = now()
        val array = JSONArray()
        entries.filter { it.keepUntilMillis >= cutoff }.forEach { array.put(it.toJson()) }
        val stream = file.startWrite()
        try {
            stream.write(array.toString().encodeToByteArray())
            file.finishWrite(stream)
        } catch (e: IOException) {
            file.failWrite(stream)
            Timber.w(e, "Remote-command ring not written")
        }
    }

    private data class RingEntry(
        val controllerId: String,
        val requestId: String,
        val keepUntilMillis: Long,
        val answer: RemoteCommandAnswer,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put(KEY_CONTROLLER, controllerId)
            .put(KEY_REQUEST, requestId)
            .put(KEY_UNTIL, keepUntilMillis)
            .put(KEY_STATUS, answer.status)
            .put(KEY_EXIT, answer.exit)
            .put(KEY_REASON, answer.reason ?: JSONObject.NULL)
            .put(KEY_MESSAGE, answer.message)
            .put(KEY_DATA, answer.data?.toString() ?: JSONObject.NULL)

        companion object {
            fun from(json: JSONObject): RingEntry? {
                val answer = RemoteCommandAnswer(
                    status = json.optString(KEY_STATUS),
                    exit = json.optInt(KEY_EXIT),
                    reason = json.optString(KEY_REASON).takeIf { !json.isNull(KEY_REASON) },
                    message = json.optString(KEY_MESSAGE),
                    data = json.optString(KEY_DATA).takeIf { !json.isNull(KEY_DATA) }?.let(::parseData),
                )
                val controller = json.optString(KEY_CONTROLLER).takeIf { it.isNotEmpty() }
                val request = json.optString(KEY_REQUEST).takeIf { it.isNotEmpty() }
                if (controller == null || request == null) return null
                return RingEntry(controller, request, json.optLong(KEY_UNTIL), answer)
            }

            private fun parseData(raw: String): JsonObject? = try {
                JsonParser.parseString(raw) as? JsonObject
            } catch (e: JsonParseException) {
                Timber.w(e, "Remote-command ring answer data unreadable")
                null
            }
        }
    }

    companion object {
        internal const val RING_FILE = "remote_command_ring.json"
        private const val MAX_ENTRIES = 500
        private const val KEY_CONTROLLER = "c"
        private const val KEY_REQUEST = "r"
        private const val KEY_UNTIL = "u"
        private const val KEY_STATUS = "s"
        private const val KEY_EXIT = "e"
        private const val KEY_REASON = "why"
        private const val KEY_MESSAGE = "m"
        private const val KEY_DATA = "d"
    }
}
