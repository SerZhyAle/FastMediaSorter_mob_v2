package com.sza.fastmediasorter.wear.data.broadcast

import com.google.gson.annotations.SerializedName

/**
 * S2509 ADR-3: the watch's copy of the S2508 subscription contract, write side only.
 *
 * Duplicated rather than shared through a Gradle module (strategic §6 question 7): the watch needs the
 * four fields and the encoder, never the parser, and a module added for that would widen the graph
 * around less code than the module's own build file.
 *
 * There is deliberately no field naming the watch as the source. The phone's parser refuses a
 * `schemaVersion` above 1 and is already released, so a discriminator would make every watch broadcast
 * unreadable by every shipped build - the opposite of goal 1. Origin, where it matters, rides in [title].
 */
data class BroadcastDescriptorDto(
    @SerializedName("schemaVersion") val schemaVersion: Int = SCHEMA_VERSION,
    @SerializedName("url") val url: String,
    @SerializedName("title") val title: String? = null,
    @SerializedName("mode") val mode: String = MODE_AUDIO_ONLY
) {

    companion object {

        /** The one version the released phone parser accepts; raising it strands every listener. */
        const val SCHEMA_VERSION = 1

        /** The only mode a watch can offer - the target watches carry no camera (ADR-1). */
        const val MODE_AUDIO_ONLY = "AUDIO_ONLY"
    }
}
