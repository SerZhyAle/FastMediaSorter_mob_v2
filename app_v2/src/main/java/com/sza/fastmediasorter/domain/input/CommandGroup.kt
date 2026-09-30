package com.sza.fastmediasorter.domain.input

enum class CommandGroup {
    PLAYBACK_CORE,
    NAVIGATION,
    VIEW_ZOOM,
    AUDIO_SUBTITLES,
    SYSTEM_UI,
    SORTING_ACTIONS,
    OPERATION_SLOTS,
    BROWSER_ACTIONS,
    VR_ONLY;

    companion object {
        /**
         * The one command -> group mapping: the remap screen groups its rows with it and the group
         * reset selects the overrides it deletes with it, so the two can never disagree.
         * Order matters - `sorting.op_slot_` must win over `sorting.`; unknown ids fall back to [SYSTEM_UI].
         */
        fun of(commandId: String): CommandGroup = when {
            commandId.startsWith("playback.") -> PLAYBACK_CORE
            commandId.startsWith("navigation.") -> NAVIGATION
            commandId.startsWith("view.") -> VIEW_ZOOM
            commandId.startsWith("audio.") -> AUDIO_SUBTITLES
            commandId.startsWith("system.") -> SYSTEM_UI
            commandId.startsWith("sorting.op_slot_") -> OPERATION_SLOTS
            commandId.startsWith("sorting.") -> SORTING_ACTIONS
            commandId.startsWith("browser.") -> BROWSER_ACTIONS
            commandId.startsWith("vr.") -> VR_ONLY
            else -> SYSTEM_UI
        }
    }
}
