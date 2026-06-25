package com.sza.fastmediasorter.data.repository.streams

import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0668 / PHASE_03: app-private sidecar for the stream favicon sprite-atlas. Holds two files under
 * `filesDir/streams/`: the atlas PNG ([atlasFile]) and a flat `url -> favicon_index` JSON map
 * ([coords]). Both are rewritten wholesale on every catalog import (same fresh-fetch lifecycle as the
 * CSV); no Room schema change. Coords are keyed by `url` because the entity `id` is randomised per
 * import, while `url` is the stable unique index.
 */
@Singleton
class FaviconAtlasStore @Inject constructor(
    @ApplicationContext private val context: android.content.Context
) {
    private val dir: File get() = File(context.filesDir, "streams")
    private val atlas: File get() = File(dir, "favicon-atlas.png")
    private val coordsJson: File get() = File(dir, "favicon-coords.json")

    /**
     * Writes both sidecar files atomically (temp file + rename). A null/empty [atlasBytes] clears the
     * atlas and writes an empty coords map, so a catalog with no atlas leaves a clean empty state
     * (every row falls back to an empty slot).
     */
    suspend fun write(atlasBytes: ByteArray?, coords: Map<String, Int>) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        if (atlasBytes == null || atlasBytes.isEmpty()) {
            atlas.delete()
            writeAtomically(coordsJson, encodeCoords(emptyMap()).toByteArray(Charsets.UTF_8))
        } else {
            writeAtomically(atlas, atlasBytes)
            writeAtomically(coordsJson, encodeCoords(coords).toByteArray(Charsets.UTF_8))
        }
    }

    /** The persisted atlas PNG, or null when no atlas is present (an OLD catalog, or never imported). */
    fun atlasFile(): File? = atlas.takeIf { it.isFile }

    /**
     * The persisted `url -> favicon_index` map. An absent or corrupt sidecar yields an empty map
     * (logged at info, not error - a missing sidecar is an expected pre-import / old-catalog state).
     */
    suspend fun coords(): Map<String, Int> = withContext(Dispatchers.IO) {
        val file = coordsJson
        if (!file.isFile) return@withContext emptyMap()
        try {
            decodeCoords(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            Timber.i(e, "Favicon coords sidecar unreadable; treating as empty")
            emptyMap()
        }
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            // Rename can fail across some filesystems; fall back to an overwrite so the data still lands.
            target.writeBytes(bytes)
            tmp.delete()
        }
    }

    private fun encodeCoords(coords: Map<String, Int>): String {
        val obj = JSONObject()
        for ((url, index) in coords) obj.put(url, index)
        return obj.toString()
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
