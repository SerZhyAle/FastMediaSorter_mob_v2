package com.sza.fastmediasorter.ui.player

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.sza.fastmediasorter.domain.input.InputSurface
import com.sza.fastmediasorter.domain.model.GamepadAction
import com.sza.fastmediasorter.utils.UserActionLogger

/** Routes key/motion/gamepad input for [PlayerActivity] - extracted to keep the host class under the 1000-LOC budget. */
internal class PlayerInputDispatcher(private val activity: PlayerActivity) {

    fun onKeyDown(keyCode: Int, event: KeyEvent?, superHandler: (Int, KeyEvent?) -> Boolean): Boolean {
        UserActionLogger.logKey(keyCode, event?.action ?: KeyEvent.ACTION_DOWN, KeyEvent.keyCodeToString(keyCode), "PlayerActivity")
        if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) &&
            activity.pdfViewerManager.isInFullscreenMode()) {
            activity.pdfViewerManager.exitFullscreenMode()
            return true
        }
        return activity.keyboardHandler.handleKeyDown(keyCode, event) || superHandler(keyCode, event)
    }

    fun dispatchKeyEvent(event: KeyEvent, superHandler: (KeyEvent) -> Boolean): Boolean {
        if (activity.blackScreenOverlayManager.isVisible) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                activity.blackScreenOverlayManager.hide()
            }
            return true
        }
        // Gamepad buttons intercepted before onKeyDown so input routing stays in one place.
        // BT keyboard / remote keys fall through to super → onKeyDown → keyboardHandler.
        val action = activity.gamepadInputManager.handleKeyEvent(event, InputSurface.PLAYER)
        if (action is GamepadAction.PlayerAction && routePlayerGamepadAction(action)) return true
        return superHandler(event)
    }

    fun dispatchGenericMotionEvent(event: MotionEvent, superHandler: (MotionEvent) -> Boolean): Boolean {
        if (activity.blackScreenOverlayManager.isVisible) {
            activity.blackScreenOverlayManager.hide()
            return true
        }
        // S0289 Phase 08: gamepad analog stays player-specific; pointer events fall through to the
        // bespoke player-mouse handler first, then to the shared BaseActivity foundation.
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            // reserved for joystick-specific routing
        }
        val action = activity.gamepadInputManager.handleMotionEvent(event, InputSurface.PLAYER)
        if (action is GamepadAction.PlayerAction && routePlayerGamepadAction(action)) return true
        return activity.keyboardHandler.handlePointerEvent(activity.window.decorView, event) || superHandler(event)
    }

    /** Routes a [GamepadAction.PlayerAction] to the same callbacks used by keyboard input. */
    private fun routePlayerGamepadAction(action: GamepadAction.PlayerAction): Boolean {
        val callback = activity.keyboardHandler.callback
        when (action) {
            is GamepadAction.PlayerAction.PlayPause -> activity.viewModel.togglePause()
            is GamepadAction.PlayerAction.Next -> callback.onNextFile()
            is GamepadAction.PlayerAction.Prev -> callback.onPreviousFile()
            is GamepadAction.PlayerAction.Seek -> {
                val seconds = (action.ms / 1000L).toInt()
                if (action.ms >= 0L) callback.onSeekForward(seconds) else callback.onSeekBackward(-seconds)
            }
            is GamepadAction.PlayerAction.Volume -> callback.onChangeVolume(if (action.up) +1 else -1)
            is GamepadAction.PlayerAction.ToggleHud -> callback.onToggleCommandPanel()
            is GamepadAction.PlayerAction.ToggleHints -> callback.onShowHelp()
            is GamepadAction.PlayerAction.Exit -> callback.onExitPlayer()
        }
        return true
    }
}
