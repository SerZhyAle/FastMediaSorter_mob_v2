package com.sza.fastmediasorter.ui.broadcast.helpers

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.domain.usecase.broadcast.CheckPreparedBroadcastStartUseCase
import com.sza.fastmediasorter.util.showBoundTo
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S4247, owner decision В6: a start that will not use a handed-out link asks first - "start anyway" through the
 * DialogDestructive role of the destructive alert overlay, or cancel. Asked before the camera, the microphone or
 * the screen is taken; a file start passes null, since its mode is known only after the track probe.
 */
class BroadcastPreparedStartGuard @Inject constructor(
    private val checkStart: CheckPreparedBroadcastStartUseCase,
) {

    fun confirm(activity: AppCompatActivity, mode: BroadcastMode?, proceed: () -> Unit) {
        activity.lifecycleScope.launch {
            val verdict = checkStart(mode)
            when (verdict) {
                CheckPreparedBroadcastStartUseCase.Verdict.Matches,
                CheckPreparedBroadcastStartUseCase.Verdict.NothingPrepared -> proceed()
                CheckPreparedBroadcastStartUseCase.Verdict.WillNotWork -> ask(activity, proceed)
            }
        }
    }

    private fun ask(activity: AppCompatActivity, proceed: () -> Unit) {
        MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive)
            .setTitle(R.string.broadcast_prepared_mismatch_title)
            .setMessage(R.string.broadcast_prepared_mismatch_message)
            .setPositiveButton(R.string.broadcast_prepared_mismatch_start) { _, _ -> proceed() }
            .setNegativeButton(R.string.cancel, null)
            .showBoundTo(activity)
    }
}
