package com.sza.fastmediasorter.ui.game.helpers

import android.view.KeyEvent
import android.view.View
import com.sza.fastmediasorter.domain.game.GameDirection
import com.sza.fastmediasorter.ui.game.GameBoardView

class GameInputManager(
    private val onDirection: (GameDirection) -> Unit,
    private val onPrimaryAction: () -> Unit,
    private val onRestartLevel: () -> Unit,
    private val onBack: () -> Unit
) {

    fun attachBoard(boardView: GameBoardView) {
        boardView.onSwipeDirection = onDirection
        boardView.onTapDirection = onDirection
        boardView.isFocusable = true
        boardView.isFocusableInTouchMode = true
    }

    fun bindDirectionControls(up: View, down: View, left: View, right: View) {
        up.setOnClickListener { onDirection(GameDirection.UP) }
        down.setOnClickListener { onDirection(GameDirection.DOWN) }
        left.setOnClickListener { onDirection(GameDirection.LEFT) }
        right.setOnClickListener { onDirection(GameDirection.RIGHT) }
    }

    fun bindPrimaryAction(view: View) {
        view.setOnClickListener { onPrimaryAction() }
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_W,
            KeyEvent.KEYCODE_NUMPAD_8 -> {
                onDirection(GameDirection.UP)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_S,
            KeyEvent.KEYCODE_NUMPAD_2 -> {
                onDirection(GameDirection.DOWN)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_NUMPAD_4 -> {
                onDirection(GameDirection.LEFT)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_D,
            KeyEvent.KEYCODE_NUMPAD_6 -> {
                onDirection(GameDirection.RIGHT)
                true
            }
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_SPACE -> {
                onPrimaryAction()
                true
            }
            // The screen consumes every D-pad direction before the focus system sees it, so the
            // action-row buttons are unreachable by focus and each command needs its own key.
            KeyEvent.KEYCODE_R,
            KeyEvent.KEYCODE_BUTTON_Y -> {
                onRestartLevel()
                true
            }
            KeyEvent.KEYCODE_ESCAPE -> {
                onBack()
                true
            }
            else -> false
        }
    }
}
