package com.sza.fastmediasorter.ui.stopwatch.helpers

import android.view.View
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityStopwatchBinding
import com.sza.fastmediasorter.databinding.ViewStopwatchRegionBinding
import com.sza.fastmediasorter.domain.model.stopwatch.StopwatchParticipant
import com.sza.fastmediasorter.domain.model.stopwatch.StopwatchScreenState

/**
 * Draws [StopwatchScreenState] onto the four region slots and keeps the focus order honest.
 *
 * The geometry is the layout's business - portrait and landscape place the same four ids differently -
 * so this class only decides which regions are shown, what they read, and where focus goes next. Focus
 * is reassigned on every count change because a chain fixed in XML would walk into a hidden region as
 * soon as the participant count drops (Rule 16).
 */
class StopwatchRegionBinder(
    private val binding: ActivityStopwatchBinding,
    private val onStartOrLap: (Int) -> Unit,
    private val onStopOrReset: (Int) -> Unit,
) {

    private val regions: List<ViewStopwatchRegionBinding> = listOf(
        binding.stopwatchRegion0,
        binding.stopwatchRegion1,
        binding.stopwatchRegion2,
        binding.stopwatchRegion3,
    )

    private val rows: List<View> = listOf(binding.stopwatchRow0, binding.stopwatchRow1)

    /** Wires the listeners once; [bind] afterwards only carries values, never new listeners. */
    fun attachListeners() {
        regions.forEachIndexed { participantId, region ->
            region.root.setOnClickListener { onStartOrLap(participantId) }
            region.btnStopwatchRegionPrimary.setOnClickListener { onStartOrLap(participantId) }
            region.btnStopwatchRegionSecondary.setOnClickListener { onStopOrReset(participantId) }
        }
    }

    fun bind(state: StopwatchScreenState, nowMillis: Long) {
        regions.forEachIndexed { participantId, region ->
            val visible = participantId < state.participantCount
            region.root.isVisible = visible
            if (visible) {
                bindRegion(region, state.participants[participantId], state.participantCount, nowMillis)
            }
        }
        rows.forEach { row -> row.isVisible = hasVisibleRegion(row) }
        // S2792: one button carries both global transitions; with a single participant the region's
        // own controls already cover it, so the bar keeps its width for the rest.
        binding.btnStopwatchStartAll.isVisible = state.participantCount > StopwatchScreenState.SINGLE_PARTICIPANT
        binding.btnStopwatchStartAll.setText(
            if (state.anyRunning) R.string.stopwatch_action_stop_all else R.string.stopwatch_action_start_all,
        )
        applyFocusChain(state.participantCount)
    }

    private fun bindRegion(
        region: ViewStopwatchRegionBinding,
        participant: StopwatchParticipant,
        participantCount: Int,
        nowMillis: Long,
    ) {
        val context = region.root.context
        val label = context.getString(R.string.stopwatch_region_label, participant.id + 1)
        // A single participant owns the whole screen, so naming it adds a line and no information.
        region.stopwatchRegionLabel.isVisible = participantCount > StopwatchScreenState.SINGLE_PARTICIPANT
        region.stopwatchRegionLabel.text = label
        region.root.contentDescription = label
        region.stopwatchRegionReading.text = StopwatchTimeFormatter.format(participant.elapsedAt(nowMillis))
        region.stopwatchRegionLaps.text = lapText(region, participant)

        val running = participant.running
        region.btnStopwatchRegionPrimary.setText(
            if (running) R.string.stopwatch_action_lap else R.string.stopwatch_action_start,
        )
        region.btnStopwatchRegionSecondary.setText(
            if (running) R.string.stopwatch_action_stop else R.string.stopwatch_action_reset,
        )
        region.btnStopwatchRegionSecondary.isEnabled = participant.hasProgress
    }

    /** Newest split first, so the one just recorded is readable without scrolling the list. */
    private fun lapText(region: ViewStopwatchRegionBinding, participant: StopwatchParticipant): CharSequence {
        val context = region.root.context
        if (participant.laps.isEmpty()) {
            return context.getString(R.string.stopwatch_laps_empty)
        }
        return participant.laps.asReversed().joinToString(separator = "\n") { lap ->
            context.getString(
                R.string.stopwatch_lap_line,
                lap.ordinal,
                StopwatchTimeFormatter.format(lap.atElapsedMillis),
            )
        }
    }

    /**
     * The participant a region-less key of the centre/enter vocabulary applies to, or [NO_REGION] when
     * the key belongs to nobody.
     *
     * It tests the region root itself rather than [View.hasFocus], because a focused Start or Stop
     * button inside a region already answers a centre press with its own action - claiming the key for
     * the region would turn a press on the Stop button into a start. The volume vocabulary is not
     * bound by that concern and resolves through [volumeKeyParticipantId] instead.
     */
    fun focusedParticipantId(): Int {
        val focused = regions.indexOfFirst { it.root.isVisible && it.root.isFocused }
        if (focused >= 0) {
            return focused
        }
        // With one region on screen there is nothing to disambiguate, so a stray key still starts it.
        // With several, a centre key that landed outside every region must not pick one for the user.
        val visibleCount = regions.count { it.root.isVisible }
        return if (visibleCount == StopwatchScreenState.SINGLE_PARTICIPANT) 0 else NO_REGION
    }

    /**
     * The participant a hardware volume key applies to (S2792), or [NO_REGION] when none is visible.
     *
     * Unlike [focusedParticipantId] this accepts subtree focus: a volume key never activates the
     * focused view's own action, so the region owning a focused Start or Stop button may claim it.
     * When nothing inside a region holds focus - the normal touch-mode state, where taps leave no view
     * focused - the first visible region is the default, because the root-focus requirement used to
     * drop every touch-mode press to the system and the keys looked dead.
     */
    fun volumeKeyParticipantId(): Int {
        val subtreeFocused = regions.indexOfFirst { it.root.isVisible && it.root.hasFocus() }
        if (subtreeFocused >= 0) {
            return subtreeFocused
        }
        return regions.indexOfFirst { it.root.isVisible }.takeIf { it >= 0 } ?: NO_REGION
    }

    /** Moves focus to a region, which is how a number key selects a participant without a tap. */
    fun focusParticipant(participantId: Int) {
        regions.getOrNull(participantId)?.root?.takeIf { it.isVisible }?.requestFocus()
    }

    private fun hasVisibleRegion(row: View): Boolean =
        regions.any { region -> region.root.parent === row && region.root.isVisible }

    /**
     * Walks the visible regions in participant order and closes the chain on the global actions, so a
     * D-pad never lands on a hidden region and never dead-ends at the last one.
     */
    private fun applyFocusChain(participantCount: Int) {
        val visible = regions.take(participantCount).map { it.root }
        // The slider is the compensation for the volume keys this screen takes (S1411 ADR-2), so it
        // belongs in the explicit chain: an override that jumped from the last region straight to the
        // action bar would leave a D-pad unable to reach the one control that changes the volume.
        val afterRegions = if (binding.stopwatchVolumeRow.isVisible) {
            binding.sliderStopwatchVolume.id
        } else {
            binding.btnStopwatchStartAll.id
        }
        visible.forEachIndexed { index, view ->
            val next = visible.getOrNull(index + 1)?.id ?: afterRegions
            view.nextFocusDownId = next
            view.nextFocusRightId = next
            view.nextFocusForwardId = next
        }
        // S2792: the action bar walks its own XML order (Start all, Reset all, Result, Settings), so
        // no global action is unreachable by D-pad; the chain previously jumped from Reset all
        // straight to Settings and skipped Result on the way right.
        binding.sliderStopwatchVolume.nextFocusUpId = visible.lastOrNull()?.id ?: View.NO_ID
        binding.sliderStopwatchVolume.nextFocusDownId = binding.btnStopwatchStartAll.id
        binding.sliderStopwatchVolume.nextFocusForwardId = binding.btnStopwatchStartAll.id
        binding.btnStopwatchStartAll.nextFocusUpId = afterRegions
        binding.btnStopwatchStartAll.nextFocusRightId = binding.btnStopwatchResetAll.id
        binding.btnStopwatchStartAll.nextFocusForwardId = binding.btnStopwatchResetAll.id
        binding.btnStopwatchResetAll.nextFocusUpId = afterRegions
        binding.btnStopwatchResetAll.nextFocusRightId = binding.btnStopwatchResult.id
        binding.btnStopwatchResetAll.nextFocusForwardId = binding.btnStopwatchResult.id
        binding.btnStopwatchResult.nextFocusUpId = afterRegions
        binding.btnStopwatchResult.nextFocusRightId = binding.btnStopwatchSettings.id
        binding.btnStopwatchResult.nextFocusForwardId = binding.btnStopwatchSettings.id
        binding.btnStopwatchSettings.nextFocusUpId = afterRegions
    }

    companion object {
        /** No region owns the key: the caller must let it through instead of guessing a participant. */
        const val NO_REGION = -1
    }
}
