package com.sza.fastmediasorter.vr.helpers

import android.media.AudioManager
import android.os.Handler
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.player.contracts.PlaybackCommand
import com.sza.fastmediasorter.ui.player.helpers.seekBackward
import com.sza.fastmediasorter.ui.player.helpers.seekForward
import com.sza.fastmediasorter.vr.VrPlayerActivity
import com.sza.fastmediasorter.vr.openxr.OpenXrNative
import com.sza.fastmediasorter.vr.openxr.XrHand
import com.sza.fastmediasorter.vr.render.VrInteractivePanelComposer
import com.sza.fastmediasorter.vr.render.VrInteractivePanelDriver
import timber.log.Timber

/** Routes VR controller / hand-tracking commands and panel zone clicks to the player. Decomposed per S0033 Phase 05. */
class VrPlayerCommandRouter(
    private val activity: VrPlayerActivity,
    private val mainHandler: Handler,
) {

    var currentPanelSpeed: Float = 1.0f
    private var seekDragTargetFraction: Float = -1f

    private val seekDragRunnable = Runnable {
        val pd = activity.vrRenderPipelineManagerInternal?.vrInteractivePanelDriver ?: return@Runnable
        commitSeekDrag(seekDragTargetFraction, pd)
    }

    /**
     * Snapshot the relevant immersive subsystems before processing an explicit VR command
     * so the log differentiates "command suppressed by lock" from "command silently dropped".
     */
    private fun traceImmersiveCommand(command: PlaybackCommand, source: VrCommandSource) {
        val pipeline = activity.vrRenderPipelineManagerInternal
        Timber.d(
            "VrPlayerActivity: cmd=%s source=%s locked=%s hudVisible=%s descriptor=%s",
            command, source, activity.isImmersiveUiLocked(),
            pipeline?.vrHudSceneDriver?.let { "scene-driver" } ?: "fallback",
            pipeline?.currentLayerDescriptor?.type)
    }

    fun dispatchPanelZoneClick(zoneId: Int) {
        val cmd = when (zoneId) {
            VrInteractivePanelComposer.ZONE_PREV        -> PlaybackCommand.PreviousFile
            VrInteractivePanelComposer.ZONE_NEXT        -> PlaybackCommand.NextFile
            VrInteractivePanelComposer.ZONE_PLAY_PAUSE  -> PlaybackCommand.TogglePausePlay
            VrInteractivePanelComposer.ZONE_SEEK_BACK   -> PlaybackCommand.SeekBackward
            VrInteractivePanelComposer.ZONE_SEEK_FWD    -> PlaybackCommand.SeekForward
            VrInteractivePanelComposer.ZONE_SEEK_SLIDER -> null
            VrInteractivePanelComposer.ZONE_VOL_DOWN    -> PlaybackCommand.VolumeDown
            VrInteractivePanelComposer.ZONE_VOL_UP      -> PlaybackCommand.VolumeUp
            VrInteractivePanelComposer.ZONE_BRIGHT_DOWN -> PlaybackCommand.BrightnessDown
            VrInteractivePanelComposer.ZONE_BRIGHT_UP   -> PlaybackCommand.BrightnessUp
            VrInteractivePanelComposer.ZONE_SPEED       -> PlaybackCommand.SetPlaybackSpeed(nextPanelSpeed())
            VrInteractivePanelComposer.ZONE_TRACK       -> PlaybackCommand.CycleAudioTrack
            VrInteractivePanelComposer.ZONE_FORMAT      -> PlaybackCommand.CycleStereoFormat
            VrInteractivePanelComposer.ZONE_EXIT_TO_2D  -> PlaybackCommand.ExitTo2D
            VrInteractivePanelComposer.ZONE_EXIT        -> PlaybackCommand.Exit
            -1 -> null
            else -> null.also { Timber.w("VrPlayerActivity: unknown zone %d — ignored", zoneId) }
        }
        if (cmd != null) {
            handleVrCommand(cmd, VrCommandSource.CONTROLLER)
            activity.vrRenderPipelineManagerInternal?.vrInteractivePanelDriver?.scheduleAutoHide()
        }
    }

    fun scheduleSeekDrag(fraction: Float, panelDriver: VrInteractivePanelDriver) {
        mainHandler.removeCallbacks(seekDragRunnable)
        seekDragTargetFraction = fraction
        mainHandler.postDelayed(seekDragRunnable, SEEK_DEBOUNCE_MS)
    }

    fun commitSeekDrag(fraction: Float, panelDriver: VrInteractivePanelDriver) {
        mainHandler.removeCallbacks(seekDragRunnable)
        val totalMs = activity.currentPlaybackDurationInternal()
        if (totalMs <= 0L) return
        val positionMs = (fraction * totalMs).toLong().coerceIn(0L, totalMs)
        handleVrCommand(PlaybackCommand.SeekTo(positionMs), VrCommandSource.CONTROLLER)
        panelDriver.updateSeekDrag(-1f)
    }

    fun nextPanelSpeed(): Float {
        val speeds = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val idx = speeds.indexOfFirst { it == currentPanelSpeed }
        currentPanelSpeed = speeds[(idx + 1) % speeds.size]
        activity.vrRenderPipelineManagerInternal?.vrInteractivePanelDriver?.updateSpeed(currentPanelSpeed)
        return currentPanelSpeed
    }

    fun handleVrCommand(command: PlaybackCommand, source: VrCommandSource = VrCommandSource.UI) {
        val viewModel = activity.viewModelInternal
        val pipeline = activity.vrRenderPipelineManagerInternal
        val hud = pipeline?.vrHudManager

        when (command) {
            PlaybackCommand.OpenControls,
            PlaybackCommand.OpenFileOps -> traceImmersiveCommand(command, source)
            else -> Timber.d("VrPlayerActivity: handling VR command $command")
        }
        // WHY: any mapped controller command means the user is actively using the controller;
        // wake the HUD and reset the 15 s idle-hide timer.
        hud?.reportActivity()
        when (command) {
            PlaybackCommand.Play -> {
                viewModel.setPaused(false)
                hud?.showPauseIndicator(paused = false)
            }
            PlaybackCommand.Pause -> {
                viewModel.setPaused(true)
                hud?.showPauseIndicator(paused = true)
                hud?.showBannerText(activity.getString(R.string.vr_hud_prev_next_hint))
            }
            PlaybackCommand.TogglePausePlay -> {
                val nowPaused = !viewModel.state.value.isPaused
                viewModel.togglePause()
                hud?.showPauseIndicator(paused = nowPaused)
                if (nowPaused) hud?.showBannerText(activity.getString(R.string.vr_hud_prev_next_hint))
                maybeTriggerHaptic(source, XrHand.RIGHT, HAPTIC_SHORT_NS, HAPTIC_AMPL)
            }
            PlaybackCommand.SeekForward -> {
                activity.seekPlaybackForwardInternal(VR_SEEK_SECONDS)
                showSeekFeedback(+VR_SEEK_SECONDS)
                maybeTriggerHaptic(source, XrHand.LEFT, HAPTIC_SHORT_NS, HAPTIC_AMPL)
            }
            PlaybackCommand.SeekBackward -> {
                activity.seekPlaybackBackwardInternal(VR_SEEK_SECONDS)
                showSeekFeedback(-VR_SEEK_SECONDS)
                maybeTriggerHaptic(source, XrHand.LEFT, HAPTIC_SHORT_NS, HAPTIC_AMPL)
            }
            is PlaybackCommand.SeekMicro -> {
                val d = if (command.forward) VR_SEEK_MICRO else -VR_SEEK_MICRO
                if (command.forward) activity.seekPlaybackForwardInternal(VR_SEEK_MICRO)
                else activity.seekPlaybackBackwardInternal(VR_SEEK_MICRO)
                showSeekFeedback(d)
            }
            is PlaybackCommand.SeekMacro -> {
                val d = if (command.forward) VR_SEEK_MACRO else -VR_SEEK_MACRO
                if (command.forward) activity.seekPlaybackForwardInternal(VR_SEEK_MACRO)
                else activity.seekPlaybackBackwardInternal(VR_SEEK_MACRO)
                showSeekFeedback(d)
            }
            PlaybackCommand.NextFile -> {
                Timber.i("VrPlayerActivity: immersive prev/next dir=NEXT xrSession=alive")
                viewModel.nextFile()
                showFileFeedback()
                maybeTriggerHaptic(source, XrHand.RIGHT, HAPTIC_SHORT_NS, HAPTIC_AMPL)
            }
            PlaybackCommand.PreviousFile -> {
                Timber.i("VrPlayerActivity: immersive prev/next dir=PREV xrSession=alive")
                viewModel.previousFile()
                showFileFeedback()
                maybeTriggerHaptic(source, XrHand.RIGHT, HAPTIC_SHORT_NS, HAPTIC_AMPL)
            }
            PlaybackCommand.OpenControls -> {
                if (activity.isImmersiveUiLocked()) {
                    Timber.i("VrPlayerActivity: OpenControls no-op — reason=immersive-ui-locked")
                    hud?.showBannerText(activity.getString(R.string.vr_hud_guard_controls))
                } else {
                    hud?.setVisible(true, reason = "explicit-open-controls")
                    activity.dialogHelperInternal.showPlaybackControlDialog()
                }
            }
            PlaybackCommand.Exit -> activity.vrSessionLifecycleManagerInternal?.exitVrAndStopPlayback("overlay-exit-command")
            PlaybackCommand.ExitTo2D -> {
                Timber.i("VrPlayerActivity: ExitTo2D — switching to VR panel mode")
                activity.vrSessionLifecycleManagerInternal?.switchToPanelPreservingPosition()
            }
            PlaybackCommand.OpenFileOps -> {
                if (activity.isImmersiveUiLocked()) {
                    Timber.i("VrPlayerActivity: OpenFileOps no-op — reason=immersive-ui-locked")
                    hud?.showBannerText(activity.getString(R.string.vr_hud_guard_file_ops))
                } else activity.vrFileOpsManagerInternal?.show()
            }
            PlaybackCommand.CopyFile -> {
                if (activity.isImmersiveUiLocked()) hud?.showBannerText(activity.getString(R.string.vr_hud_guard_file_ops))
                else activity.vrFileOpsManagerInternal?.showAndCopy()
            }
            PlaybackCommand.MoveFile -> {
                if (activity.isImmersiveUiLocked()) hud?.showBannerText(activity.getString(R.string.vr_hud_guard_file_ops))
                else activity.vrFileOpsManagerInternal?.showAndMove()
            }
            PlaybackCommand.DeleteFile -> {
                if (activity.isImmersiveUiLocked()) hud?.showBannerText(activity.getString(R.string.vr_hud_guard_file_ops))
                else activity.vrFileOpsManagerInternal?.showAndDelete()
            }
            PlaybackCommand.RenameFile -> {
                if (activity.isImmersiveUiLocked()) hud?.showBannerText(activity.getString(R.string.vr_hud_guard_file_ops))
                else activity.vrFileOpsManagerInternal?.showAndRename()
            }
            is PlaybackCommand.VolumeStep -> showVolumeFeedback()
            is PlaybackCommand.ZoomStep -> {
                activity.vrZoomManagerInternal?.onDiscreteStep(command.increase)
                hud?.showZoomIndicator(activity.vrZoomManagerInternal?.zoom() ?: 1f)
            }
            PlaybackCommand.ZoomReset -> {
                activity.vrZoomManagerInternal?.reset()
                hud?.showZoomIndicator(1f)
                maybeTriggerHaptic(source, XrHand.LEFT, HAPTIC_SHORT_NS, HAPTIC_AMPL)
            }
            PlaybackCommand.Recenter -> {
                hud?.showRecenterFlash()
                maybeTriggerHaptic(source, XrHand.RIGHT, HAPTIC_SHORT_NS, HAPTIC_AMPL)
            }
            PlaybackCommand.ToggleImmersiveMode -> {
                activity.vrToggleButtonManagerInternal?.onToggleRequested()
                hud?.showImmersiveModeChanged(immersive = !(pipeline?.vrRenderingActive ?: false))
            }
            PlaybackCommand.ShowCheatsheet -> {
                if (activity.isImmersiveUiLocked()) hud?.showBannerText(activity.getString(R.string.vr_hud_first_run_cheat))
                else activity.vrCheatsheetManagerInternal?.toggleManual()
            }
            PlaybackCommand.ToggleOverlay  -> activity.controlOverlayInternal?.toggle()
            PlaybackCommand.Mute -> toggleMute()
            PlaybackCommand.VolumeUp -> onVolumeStep(+1)
            PlaybackCommand.VolumeDown -> onVolumeStep(-1)
            PlaybackCommand.BrightnessUp, PlaybackCommand.BrightnessDown -> Unit
            is PlaybackCommand.SetPlaybackSpeed -> activity.setPlaybackSpeedInternal(command.speed)
            PlaybackCommand.CycleAudioTrack -> cycleAudioTrackAndUpdatePanel()
            PlaybackCommand.CycleStereoFormat -> Unit
            is PlaybackCommand.SeekTo -> activity.seekPlaybackToInternal(command.positionMs)
        }
    }

    fun onVolumeStep(delta: Int) {
        val am = activity.audioManagerInternal ?: return
        val dir = if (delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        try {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0)
        } catch (t: Throwable) {
            Timber.w(t, "VrPlayerActivity: AudioManager.adjustStreamVolume failed")
        }
        showVolumeFeedback()
    }

    fun cycleAudioTrackAndUpdatePanel() {
        val tracks = activity.availableAudioTracksInternal()
        if (tracks.size < 2) return
        val currentIdx = tracks.indexOfFirst { it.isSelected }.coerceAtLeast(0)
        val next = tracks[(currentIdx + 1) % tracks.size]
        activity.selectAudioTrackInternal(next.groupIndex, next.trackIndex)
        activity.vrRenderPipelineManagerInternal?.vrInteractivePanelDriver?.updatePanelTrackLabel(next.label)
    }

    fun showVolumeFeedback() {
        val am = activity.audioManagerInternal ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val percent = (current * 100 / max).coerceIn(0, 100)
        activity.vrRenderPipelineManagerInternal?.vrHudManager?.showVolumeIndicator(percent)
        activity.vrRenderPipelineManagerInternal?.vrInteractivePanelDriver?.updatePanelVolume(percent)
    }

    fun onZoomGripDelta(delta: Float) {
        activity.vrZoomManagerInternal?.onGripDelta(delta)
        activity.vrRenderPipelineManagerInternal?.vrHudManager?.showZoomIndicator(
            activity.vrZoomManagerInternal?.zoom() ?: 1f)
    }

    fun showSeekFeedback(deltaSeconds: Int) {
        val snapshot = activity.currentPlaybackProgressSnapshotInternal()
        val pos = snapshot.positionMs.coerceAtLeast(0L)
        val total = snapshot.durationMs.coerceAtLeast(0L)
        activity.vrRenderPipelineManagerInternal?.vrHudManager?.showSeekIndicator(deltaSeconds, pos, total)
    }

    fun showFileFeedback() {
        val state = activity.viewModelInternal.state.value
        val file = state.currentFile ?: return
        val index = state.currentIndex.coerceAtLeast(0) + 1
        val total = state.files.size.coerceAtLeast(1)
        activity.vrRenderPipelineManagerInternal?.vrHudManager?.showFileIndicator(file.name, index, total)
    }

    fun toggleMute() {
        val am = activity.audioManagerInternal ?: return
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        try {
            if (current > 0) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            else am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        } catch (t: Throwable) {
            Timber.w(t, "VrPlayerActivity: toggleMute failed")
        }
        showVolumeFeedback()
    }

    fun triggerHaptic(hand: Int, durationNs: Long, amplitude: Float) {
        try {
            OpenXrNative.nativeTriggerHaptic(hand, durationNs, amplitude)
        } catch (t: Throwable) {
            Timber.v(t, "VrPlayerActivity: haptic trigger failed (hand=%d)", hand)
        }
    }

    fun maybeTriggerHaptic(source: VrCommandSource, hand: Int, durationNs: Long, amplitude: Float) {
        // Only physical XR controllers can provide meaningful haptics.
        if (source != VrCommandSource.CONTROLLER) return
        triggerHaptic(hand, durationNs, amplitude)
    }

    companion object {
        private const val VR_SEEK_SECONDS = 10
        private const val VR_SEEK_MICRO = 3
        private const val VR_SEEK_MACRO = 60
        private const val SEEK_DEBOUNCE_MS = 300L
        private const val HAPTIC_SHORT_NS = 20_000_000L
        private const val HAPTIC_AMPL = 0.5f
    }
}
