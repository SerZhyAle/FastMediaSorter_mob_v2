package com.sza.fastmediasorter.wear.data.broadcast

import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2509: turns a [BroadcastDescriptorDto] into the two encodings the phone already reads.
 *
 * Both shapes are fixed by S2508 and mirrored here byte for byte: plain JSON is what a saved
 * descriptor file carries, and [COMPRESSED_PREFIX] plus gzip plus Base64 is what a QR code carries,
 * because the raw JSON pushes a watch-sized QR past comfortable scanning density.
 *
 * Write-only by design. The watch builds descriptors and never reads one, so no parser is duplicated
 * and no decode path exists here to drift from the phone's.
 */
@Singleton
class BroadcastDescriptorSerializer @Inject constructor() {

    private val gson = Gson()

    /** The file form: what a listener imports as a saved broadcast. */
    fun serialize(dto: BroadcastDescriptorDto): String = gson.toJson(dto)

    /** The barcode form. Compressed because a watch screen has no room for a denser code. */
    fun serializeCompressed(dto: BroadcastDescriptorDto): String {
        val json = gson.toJson(dto)
        val gzipped = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        return COMPRESSED_PREFIX + Base64.getEncoder().encodeToString(gzipped)
    }

    companion object {

        /** Identical to the phone's constant of the same name; the two must not drift. */
        const val COMPRESSED_PREFIX = "FMSBCAST1:"
    }
}
