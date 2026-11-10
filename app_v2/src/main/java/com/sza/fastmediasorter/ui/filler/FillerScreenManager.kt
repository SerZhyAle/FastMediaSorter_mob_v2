package com.sza.fastmediasorter.ui.filler

import android.content.Context
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityFillerBinding
import com.sza.fastmediasorter.util.showBoundToHost

/** Draws [FillerUiState] into the screen and owns the start confirmation; holds no operation state. */
class FillerScreenManager(
    private val context: Context,
    private val binding: ActivityFillerBinding,
) {
    fun render(state: FillerUiState) {
        val place = state.place
        binding.txtFillerLocation.text = place?.let {
            context.getString(R.string.filler_location, it.folderName, it.volumeName)
        } ?: context.getString(R.string.filler_no_folder)
        binding.txtFillerFreeSpace.isVisible = place != null
        binding.txtFillerFreeSpace.text = state.availableBytes?.let {
            context.getString(R.string.filler_free_space, size(it))
        } ?: context.getString(R.string.filler_free_space_unknown)
        binding.txtFillerReserve.text = context.getString(R.string.filler_reserve, size(state.reserveBytes))
        binding.btnFillerChooseFolder.isEnabled = !state.running
        binding.btnFillerStart.isVisible = !state.running
        binding.btnFillerStart.isEnabled = state.canStart
        renderRun(state)
        renderCleanup(state)
        val message = state.outcome?.let(::messageFor)
        binding.txtFillerResult.isVisible = message != null && !state.running
        message?.let { binding.txtFillerResult.text = context.getString(it, size(state.writtenBytes)) }
    }

    fun confirmStart(state: FillerUiState, onConfirmed: () -> Unit) {
        val place = state.place ?: return
        val planned = (state.availableBytes ?: 0) - state.reserveBytes
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.filler_confirm_title)
            .setMessage(
                context.getString(
                    R.string.filler_confirm_message,
                    size(planned),
                    place.folderName,
                    place.volumeName,
                    size(state.reserveBytes),
                ),
            )
            .setPositiveButton(R.string.filler_confirm_positive) { _, _ -> onConfirmed() }
            .setNegativeButton(R.string.cancel, null)
            .showBoundToHost(context)
    }

    private fun renderRun(state: FillerUiState) {
        binding.fillerRunGroup.isVisible = state.running
        if (!state.running) return
        val planned = state.plannedBytes.coerceAtLeast(1)
        binding.progressFiller.max = PROGRESS_SCALE
        binding.progressFiller.setProgressCompat((state.writtenBytes * PROGRESS_SCALE / planned).toInt(), true)
        binding.txtFillerProgress.text =
            context.getString(R.string.filler_progress, size(state.writtenBytes), size(state.plannedBytes))
    }

    private fun renderCleanup(state: FillerUiState) {
        val pending = state.pending
        binding.fillerCleanupGroup.isVisible = pending != null && !state.running
        pending?.let {
            binding.txtFillerCleanupStatus.text = context.getString(R.string.filler_pending_cleanup, it.volumeName)
        }
    }

    @StringRes
    private fun messageFor(outcome: FillerOutcome): Int = when (outcome) {
        FillerOutcome.DONE -> R.string.filler_result_done
        FillerOutcome.CANCELLED -> R.string.filler_result_cancelled
        FillerOutcome.FAILED -> R.string.filler_result_failed
        FillerOutcome.CLEANUP_INCOMPLETE -> R.string.filler_result_cleanup_incomplete
        FillerOutcome.CLEANUP_DONE -> R.string.filler_result_cleanup_done
        FillerOutcome.NOT_LOCAL -> R.string.filler_result_not_local
        FillerOutcome.NO_ACCESS -> R.string.filler_result_no_access
        FillerOutcome.MEASUREMENT_UNAVAILABLE -> R.string.filler_result_measurement_unavailable
        FillerOutcome.INSUFFICIENT_SPACE -> R.string.filler_result_insufficient_space
        FillerOutcome.BUSY -> R.string.filler_result_busy
    }

    private fun size(bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

    private companion object {
        const val PROGRESS_SCALE = 1000
    }
}
