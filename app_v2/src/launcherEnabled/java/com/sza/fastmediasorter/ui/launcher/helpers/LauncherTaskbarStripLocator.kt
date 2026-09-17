package com.sza.fastmediasorter.ui.launcher.helpers

import android.graphics.Rect
import android.view.View
import androidx.core.view.isVisible
import com.sza.fastmediasorter.databinding.LauncherTaskbarBinding

/**
 * S2728: answers whether a screen point sits inside one of the taskbar's horizontally scrolling strips.
 *
 * Both strips scroll on the same axis the desktop pages on, so their own scroll would otherwise also be
 * read as a desktop swipe. The rest of the bar - start button, tray, empty background - is deliberately
 * outside this test: S2534 admits the desktop gesture there on purpose.
 *
 * Kept apart from [LauncherTaskbarManager], which owns what the strips SHOW; where they are on screen is
 * a question the gesture recognizer asks, and it needs no lifecycle, no flows and no callbacks.
 */
class LauncherTaskbarStripLocator(private val binding: LauncherTaskbarBinding) {

    private val stripBounds = Rect()

    fun isTouchOnScrollingStrip(rawX: Float, rawY: Float): Boolean =
        containsScreenPoint(binding.taskbarRecents, rawX, rawY) ||
            containsScreenPoint(binding.taskbarUpperRecents, rawX, rawY) ||
            containsScreenPoint(binding.taskbarPinned, rawX, rawY)

    private fun containsScreenPoint(view: View, rawX: Float, rawY: Float): Boolean =
        view.isVisible &&
            view.getGlobalVisibleRect(stripBounds) &&
            stripBounds.contains(rawX.toInt(), rawY.toInt())
}
