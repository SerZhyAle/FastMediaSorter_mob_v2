package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.ui.player.standalone.helpers.StandaloneViewerCommandManager

/** Keeps newer collection/playback routes separate from each host's document and file-operation callbacks. */
class StandaloneViewerKeyboardCallback(
    private val delegate: PlayerKeyboardHandler.PlayerKeyboardCallback,
    private val commands: () -> StandaloneViewerCommandManager?,
) : PlayerKeyboardHandler.PlayerKeyboardCallback by delegate {
    override fun onStepFiles(offset: Int): Boolean = commands()?.stepFiles(offset) ?: delegate.onStepFiles(offset)
    override fun onEdgeFile(last: Boolean): Boolean = commands()?.edgeFile(last) ?: delegate.onEdgeFile(last)
    override fun onRandomFile(): Boolean = commands()?.randomFile() ?: delegate.onRandomFile()
    override fun onRotateContent(clockwise: Boolean): Boolean =
        commands()?.rotate(clockwise) ?: delegate.onRotateContent(clockwise)
    override fun onChangeSpeed(
        faster: Boolean
    ): Boolean = commands()?.changeSpeed(faster) ?: delegate.onChangeSpeed(faster)
    override fun onCycleAudioTrack(): Boolean = commands()?.cycleAudioTrack() ?: delegate.onCycleAudioTrack()
    override fun onStartSlideshow(random: Boolean): Boolean =
        commands()?.startSlideshow(random) ?: delegate.onStartSlideshow(random)
    override fun onShowFileGrid(): Boolean = commands()?.showFiles(grid = true) ?: delegate.onShowFileGrid()
    override fun onShowFileList(): Boolean = commands()?.showFiles(grid = false) ?: delegate.onShowFileList()
    override fun onShowFileNumber(): Boolean = commands()?.showNumber() ?: delegate.onShowFileNumber()
    override fun hasModalInput(): Boolean = commands()?.hasModalInput == true || delegate.hasModalInput()
    override fun onFrameStep(
        forward: Boolean
    ): Boolean = commands()?.frameStep(forward) ?: delegate.onFrameStep(forward)
    override fun onToggleAbLoop(): Boolean = commands()?.toggleLoop() ?: delegate.onToggleAbLoop()
}
