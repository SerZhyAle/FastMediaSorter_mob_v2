package com.sza.fastmediasorter.domain.model

import com.google.gson.annotations.SerializedName

// S1631: keys pinned - this one travels watch to phone, and the phone is the minified side here.

data class WearPlaybackStatePayload(
    @SerializedName("isPlaying") val isPlaying: Boolean,
    @SerializedName("fileName") val fileName: String,
    @SerializedName("sourceName") val sourceName: String,
    @SerializedName("positionMs") val positionMs: Long,
    @SerializedName("durationMs") val durationMs: Long,
    @SerializedName("mediaType") val mediaType: String   // "AUDIO" | "VIDEO"
)
