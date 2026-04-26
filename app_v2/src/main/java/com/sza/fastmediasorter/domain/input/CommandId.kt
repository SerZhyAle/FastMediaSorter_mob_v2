package com.sza.fastmediasorter.domain.input

object CommandId {

    // --- PLAYBACK_CORE ---
    const val PAUSE_PLAY = "playback.pause_play"
    const val PLAY = "playback.play"
    const val PAUSE = "playback.pause"
    const val STOP = "playback.stop"
    const val SPEED_UP = "playback.speed_up"
    const val SPEED_DOWN = "playback.speed_down"
    const val SPEED_RESET = "playback.speed_reset"
    const val REPEAT_MODE = "playback.repeat_mode"
    const val FRAME_STEP_FORWARD = "playback.frame_step_forward"
    const val FRAME_STEP_BACKWARD = "playback.frame_step_backward"
    const val SLIDESHOW_TOGGLE = "playback.slideshow_toggle"

    // --- NAVIGATION ---
    const val SEEK_FORWARD_5S = "navigation.seek_forward_5s"
    const val SEEK_BACKWARD_5S = "navigation.seek_backward_5s"
    const val SEEK_FORWARD_30S = "navigation.seek_forward_30s"
    const val SEEK_BACKWARD_30S = "navigation.seek_backward_30s"
    const val SEEK_MICRO_FORWARD = "navigation.seek_micro_forward"
    const val SEEK_MICRO_BACKWARD = "navigation.seek_micro_backward"
    const val SEEK_MACRO_FORWARD = "navigation.seek_macro_forward"
    const val SEEK_MACRO_BACKWARD = "navigation.seek_macro_backward"
    const val NEXT_FILE = "navigation.next_file"
    const val PREVIOUS_FILE = "navigation.previous_file"
    const val JUMP_START = "navigation.jump_start"
    const val JUMP_END = "navigation.jump_end"
    const val CHAPTER_NEXT = "navigation.chapter_next"
    const val CHAPTER_PREV = "navigation.chapter_prev"

    // --- VIEW_ZOOM ---
    const val ZOOM_IN = "view.zoom_in"
    const val ZOOM_OUT = "view.zoom_out"
    const val ZOOM_RESET = "view.zoom_reset"
    const val ROTATE = "view.rotate"
    const val FIT_SCREEN = "view.fit_screen"
    const val FULLSCREEN = "view.fullscreen"
    const val PAN = "view.pan"
    const val ASPECT_RATIO = "view.aspect_ratio"

    // --- AUDIO_SUBTITLES ---
    const val VOLUME_UP = "audio.volume_up"
    const val VOLUME_DOWN = "audio.volume_down"
    const val MUTE = "audio.mute"
    const val CYCLE_TRACK = "audio.cycle_track"
    const val CYCLE_SUBTITLE = "audio.cycle_subtitle"
    const val SUBTITLE_DELAY = "audio.subtitle_delay"

    // --- SYSTEM_UI ---
    const val TOGGLE_CONTROLS = "system.toggle_controls"
    const val TOGGLE_OVERLAY = "system.toggle_overlay"
    const val TOGGLE_INFO = "system.toggle_info"
    const val SCREENSHOT = "system.screenshot"
    const val EXIT = "system.exit"
    const val SHOW_HELP = "system.show_help"
    const val SEARCH = "system.search"
    const val UNDO = "system.undo"
    const val REDO = "system.redo"

    // --- SORTING_ACTIONS ---
    const val MOVE = "sorting.move"
    const val COPY = "sorting.copy"
    const val DELETE = "sorting.delete"
    const val RENAME = "sorting.rename"
    const val FAVOURITE = "sorting.favourite"
    const val FILE_OPS = "sorting.file_ops"
    const val CREATE_FOLDER = "sorting.create_folder"
    const val PASTE = "sorting.paste"
    const val SAVE = "sorting.save"

    // --- VR_ONLY ---
    const val VR_RECENTER = "vr.recenter"
    const val VR_TOGGLE_IMMERSIVE = "vr.toggle_immersive"
    const val VR_SWITCH_PROJECTION = "vr.switch_projection"
    const val VR_CONTROLLER_RAY = "vr.controller_ray"
    const val VR_CHEATSHEET = "vr.cheatsheet"
    const val VR_ZOOM_GRIP = "vr.zoom_grip"
    const val VR_ZOOM_START = "vr.zoom_start"
    const val VR_ZOOM_END = "vr.zoom_end"
    const val VR_DOUBLE_PINCH = "vr.double_pinch"
    const val VR_SWIPE_LEFT = "vr.swipe_left"
    const val VR_SWIPE_RIGHT = "vr.swipe_right"
    const val VR_SWIPE_UP = "vr.swipe_up"
    const val VR_SWIPE_DOWN = "vr.swipe_down"
}
