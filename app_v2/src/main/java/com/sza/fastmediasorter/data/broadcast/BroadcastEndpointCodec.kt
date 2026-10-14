package com.sza.fastmediasorter.data.broadcast

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import timber.log.Timber
import java.lang.reflect.Type

/**
 * S4160: the stored form of a descriptor's `endpoints` list on a stream row. Kept verbatim, unknown
 * transports included, so a later app that plays `P2P` finds it already saved; the player skips what it
 * cannot use.
 */
object BroadcastEndpointCodec {

    private val gson = Gson()
    private val endpointListType: Type =
        TypeToken.getParameterized(List::class.java, BroadcastEndpointDto::class.java).type

    /** Null for an absent or empty list, so a legacy descriptor leaves the column null. */
    fun encode(endpoints: List<BroadcastEndpointDto>?): String? =
        endpoints?.takeIf { it.isNotEmpty() }?.let { gson.toJson(it, endpointListType) }

    /** Null for a null column or a value that is not a list of endpoints - the row then plays its `url`. */
    @Suppress("SENSELESS_COMPARISON")
    fun decode(stored: String?): List<BroadcastEndpointDto>? {
        if (stored.isNullOrBlank()) return null
        val decoded: List<BroadcastEndpointDto?>? = try {
            gson.fromJson(stored, endpointListType)
        } catch (e: JsonParseException) {
            Timber.w(e, "BroadcastEndpointCodec: stored endpoint list is unreadable")
            null
        }
        // Gson fills a missing non-null member with null despite the Kotlin type; such an entry is unusable.
        return decoded?.filterNotNull()?.filter { it.url != null && it.transport != null }?.takeIf { it.isNotEmpty() }
    }
}
