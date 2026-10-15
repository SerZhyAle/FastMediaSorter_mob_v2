package com.sza.fastmediasorter.ui.broadcast.helpers

import androidx.appcompat.app.AppCompatActivity
import com.sza.fastmediasorter.broadcast.BroadcastSourceController
import com.sza.fastmediasorter.broadcast.BroadcastState
import com.sza.fastmediasorter.databinding.ActivityBroadcastControlBinding

/**
 * The idle-state source entries of the broadcast control screen that are not a camera or a microphone:
 * a stored file (S4234) and the phone's screen (S4233). One owner for them keeps
 * [BroadcastControlManager] to one wiring line per lifecycle step however many sources join.
 */
class BroadcastSourceEntriesManager {

    private val fileEntry = BroadcastFileEntryManager()
    private val screenEntry = BroadcastScreenEntryManager()

    fun setup(
        controller: BroadcastSourceController,
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding,
    ) {
        fileEntry.setup(controller, activity, binding)
        screenEntry.setup(controller, activity, binding)
    }

    fun render(
        controller: BroadcastSourceController,
        binding: ActivityBroadcastControlBinding,
        state: BroadcastState,
    ) {
        fileEntry.render(controller, binding, state)
        screenEntry.render(controller, binding, state)
    }

    fun onDetach() {
        fileEntry.onDetach()
        screenEntry.onDetach()
    }
}
