package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R

/**
 * The 4-item background-audio exit dialog (Stop / Keep Playing / Always Stop / Always Continue),
 * shared by the player and the streams screen (S0577). The caller supplies the behavior via the
 * lambdas; this object neither persists the choice nor touches any player.
 */
object BackgroundAudioExitDialog {

    fun show(
        context: Context,
        onStopThisTime: () -> Unit,
        onContinueThisTime: () -> Unit,
        onAlwaysStop: () -> Unit,
        onAlwaysContinue: () -> Unit,
    ) {
        val items = arrayOf(
            context.getString(R.string.background_audio_exit_stop),
            context.getString(R.string.background_audio_exit_continue),
            context.getString(R.string.background_audio_exit_always_stop),
            context.getString(R.string.background_audio_exit_always_continue),
        )
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.background_audio_exit_message)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> onStopThisTime()
                    1 -> onContinueThisTime()
                    2 -> onAlwaysStop()
                    3 -> onAlwaysContinue()
                }
            }
            .show()
    }
}
