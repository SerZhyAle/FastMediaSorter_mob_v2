package com.sza.fastmediasorter.data.companion

import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0984: write side of the companion config contract - mirror of [CompanionConfigParser].
 *
 * Two transport variants, both round-tripped by [CompanionConfigParser.parse]:
 * [serialize] emits plain JSON (starts with `{`) for the file share, and [serializeCompressed]
 * emits the compact `FMSCFG1:` gzip variant for the QR path (S1039).
 */
@Singleton
class CompanionConfigSerializer @Inject constructor() {

    private val gson = Gson()

    fun serialize(dto: CompanionConfigDto): String = gson.toJson(dto)

    /**
     * S1039: dense QR transport - `FMSCFG1:` + base64(gzip(json)), the exact inverse of
     * [CompanionConfigParser.inflate], so [CompanionConfigParser.parse] recovers the identical DTO.
     */
    fun serializeCompressed(dto: CompanionConfigDto): String {
        val json = gson.toJson(dto)
        val gzipped = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        return CompanionConfigParser.COMPRESSED_PREFIX + Base64.getEncoder().encodeToString(gzipped)
    }
}
