package com.sza.fastmediasorter.data.repository.streams

import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1154: read-only access layer for the on-demand channel-preview atlas. The
 * [DeliverableSetDownloader] writes the payload (sheet + `url->index` sidecar) into
 * `filesDir/delivery/CHANNEL_PREVIEW_ATLAS/`; this store only resolves and parses it. There is no
 * `write(..)` - the download path owns the bytes.
 *
 * Coords are keyed by the stream `url` (the stable unique index), mirroring [FaviconAtlasStore].
 */
@Singleton
class ChannelPreviewAtlasStore @Inject constructor(
    @ApplicationContext private val context: android.content.Context
) {
    private val dir: File get() = File(File(context.filesDir, "delivery"), "CHANNEL_PREVIEW_ATLAS")
    private val atlas: File get() = File(dir, "channel-preview-atlas.webp")
    private val coordsJson: File get() = File(dir, "channel-preview-coords.json")

    /** The downloaded atlas sheet, or null when the atlas is not installed. */
    fun atlasFile(): File? = atlas.takeIf { it.isFile }

    /**
     * The persisted `url -> tile_index` map. An absent or corrupt sidecar yields an empty map
     * (logged at info, not error - a missing sidecar is the expected not-yet-downloaded state).
     */
    suspend fun coords(): Map<String, Int> = withContext(Dispatchers.IO) {
        val file = coordsJson
        if (!file.isFile) return@withContext emptyMap()
        try {
            decodeCoords(file.readText(Charsets.UTF_8))
        } catch (e: IOException) {
            Timber.i(e, "Channel-preview atlas coords sidecar unreadable; treating as empty")
            emptyMap()
        } catch (e: JSONException) {
            Timber.i(e, "Channel-preview atlas coords sidecar malformed; treating as empty")
            emptyMap()
        }
    }

    private fun decodeCoords(text: String): Map<String, Int> {
        if (text.isBlank()) return emptyMap()
        val obj = JSONObject(text)
        val out = HashMap<String, Int>(obj.length())
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            // Skip non-integer values defensively; a malformed entry must not poison the whole map.
            val value = obj.opt(key)
            val index = (value as? Number)?.toInt() ?: (value as? String)?.toIntOrNull()
            if (index != null) out[key] = index
        }
        return out
    }
}
