package com.sza.fastmediasorter.domain.model

enum class PlaybackOrderMode {
    LOOP_LIST,
    PLAY_THROUGH,
    SHUFFLE,
    REPEAT_ONE;

    fun next(): PlaybackOrderMode = entries[(ordinal + 1) % entries.size]

    fun toPrefsString(): String = name.lowercase()

    companion object {
        fun fromPrefsString(value: String): PlaybackOrderMode =
            entries.firstOrNull { it.name.lowercase() == value } ?: LOOP_LIST
    }
}
