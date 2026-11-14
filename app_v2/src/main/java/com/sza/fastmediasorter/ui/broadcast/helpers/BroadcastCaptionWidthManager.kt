package com.sza.fastmediasorter.ui.broadcast.helpers

import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityBroadcastControlBinding
import com.sza.fastmediasorter.ui.common.widget.ButtonWidthReserver
import timber.log.Timber

/** BUTTONS 3.3: each state-flipping button of the control screen keeps its widest caption's width. */
object BroadcastCaptionWidthManager {

    fun reserve(binding: ActivityBroadcastControlBinding) {
        Timber.d("S4624: broadcast control caption widths reserved")
        ButtonWidthReserver.reserveForStrings(
            binding.btnToggleCamera,
            R.string.broadcast_control_camera_on,
            R.string.broadcast_control_camera_off
        )
        ButtonWidthReserver.reserveForStrings(
            binding.btnToggleMic,
            R.string.broadcast_control_mic_on,
            R.string.broadcast_control_mic_off
        )
        ButtonWidthReserver.reserveForStrings(
            binding.btnToggleTorch,
            R.string.broadcast_control_torch_on,
            R.string.broadcast_control_torch_off
        )
        ButtonWidthReserver.reserveForStrings(
            binding.btnLightFrame,
            R.string.broadcast_control_light_frame_on,
            R.string.broadcast_control_light_frame_off
        )
        ButtonWidthReserver.reserveForStrings(
            binding.btnScreenOff,
            R.string.broadcast_control_screen_off,
            R.string.broadcast_control_blank_screen
        )
    }
}
