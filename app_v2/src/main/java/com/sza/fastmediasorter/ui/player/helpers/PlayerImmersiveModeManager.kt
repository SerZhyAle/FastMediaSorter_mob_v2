package com.sza.fastmediasorter.ui.player.helpers

import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode
import timber.log.Timber

// Hides system bars and command panels while imageEditMode != NONE (S0127).
class PlayerImmersiveModeManager(
    private val activity: PlayerActivity,
    private val safeViews: PlayerBindingSafeViews
) {

    fun apply(mode: com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode) {
        if (mode == PlayerImageEditMode.NONE) {
            exit()
        } else {
            enter()
        }
    }

    private fun enter() {
        Timber.d("S0127: PlayerImmersiveModeManager.enter")
        activity.activityBinding.topCommandPanel.isVisible = false
        activity.activityBinding.tvFileNameOverlay.isVisible = false
        safeViews.copyToPanel.isVisible = false
        safeViews.moveToPanel.isVisible = false
        hideSystemBars()
    }

    private fun exit() {
        Timber.d("S0127: PlayerImmersiveModeManager.exit")
        showSystemBars()
        activity.viewModel.enterCommandPanelMode()
        activity.updatePanelVisibility(activity.viewModel.state.value.showCommandPanel)
    }

    private fun hideSystemBars() {
        try {
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } catch (e: Exception) {
            Timber.w(e, "S0127: hideSystemBars failed")
        }
    }

    private fun showSystemBars() {
        try {
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
        } catch (e: Exception) {
            Timber.w(e, "S0127: hideSystemBars failed")
        }
    }
}
