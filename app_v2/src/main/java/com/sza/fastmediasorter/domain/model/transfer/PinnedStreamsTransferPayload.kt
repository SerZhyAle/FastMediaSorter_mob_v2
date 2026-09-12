package com.sza.fastmediasorter.domain.model.transfer

import com.google.gson.annotations.SerializedName

/**
 * The user's pinned-stream list as it travels between devices (S1565).
 *
 * Deliberately carries no stream `id`: `AddStreamSourceUseCase` mints it with `UUID.randomUUID()`
 * on the device that first added the channel, so it identifies nothing on the receiving one. The
 * channel address is the identity - `stream_sources` enforces a unique index on it - and
 * [PinnedStreamEntry.sortIndex] carries the order the user arranged.
 */
data class PinnedStreamsTransferPayload(
    @SerializedName("version") val version: Int,
    @SerializedName("kind") val kind: String,
    @SerializedName("exportedAt") val exportedAt: Long,
    @SerializedName("entries") val entries: List<PinnedStreamEntry>
) {
    data class PinnedStreamEntry(
        @SerializedName("url") val url: String,
        @SerializedName("title") val title: String,
        @SerializedName("mediaKind") val mediaKind: String,
        @SerializedName("sortIndex") val sortIndex: Int
    )
}
