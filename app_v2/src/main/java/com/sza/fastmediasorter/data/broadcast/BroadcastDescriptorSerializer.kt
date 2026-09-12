package com.sza.fastmediasorter.data.broadcast

import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BroadcastDescriptorSerializer @Inject constructor() {

    private val gson = Gson()

    fun serialize(dto: BroadcastDescriptorDto): String = gson.toJson(dto)

    fun serializeCompressed(dto: BroadcastDescriptorDto): String {
        val json = gson.toJson(dto)
        val gzipped = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        return COMPRESSED_PREFIX + Base64.getEncoder().encodeToString(gzipped)
    }

    companion object {
        const val COMPRESSED_PREFIX = "FMSBCAST1:"
    }
}
