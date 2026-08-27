package com.sza.fastmediasorter.wear.data.repository

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
 * S1708: Wear OS sidecar for the stream favicon sprite-atlas. Holds two files under
 * `filesDir/streams/`: the atlas PNG ([atlasFile]) and a flat `url -> favicon_index` JSON map
 * ([coords]).
 */
@Singleton
class WearFaviconAtlasStore @Inject constructor(
    @ApplicationContext private val context: android.content.Context
) {
    private val dir: File get() = File(context.filesDir, "streams")
    private val atlas: File get() = File(dir, "favicon-atlas.png")
    private val coordsJson: File get() = File(dir, "favicon-coords.json")

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

    fun atlasFile(): File? = atlas.takeIf { it.isFile }

    suspend fun coords(): Map<String, Int> = withContext(Dispatchers.IO) {
        val file = coordsJson
        if (!file.isFile) return@withContext emptyMap()
        try {
            decodeCoords(file.readText(Charsets.UTF_8))
        } catch (e: IOException) {
            Timber.i(e, "Favicon coords sidecar unreadable; treating as empty")
            emptyMap()
        } catch (e: JSONException) {
            Timber.i(e, "Favicon coords sidecar unreadable; treating as empty")
            emptyMap()
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
            val value = obj.opt(key)
            val index = (value as? Number)?.toInt() ?: (value as? String)?.toIntOrNull()
            if (index != null) out[key] = index
        }
        return out
    }
}
