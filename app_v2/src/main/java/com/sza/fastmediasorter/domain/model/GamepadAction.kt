package com.sza.fastmediasorter.domain.model

/**
 * Domain-level gamepad action emitted by
 * [com.sza.fastmediasorter.core.input.GamepadInputManager].
 *
 * Decouples raw Android [android.view.KeyEvent]/[android.view.MotionEvent] codes
 * from the UI layer. Activities route [PlayerAction]/[BrowserAction] to the
 * existing keyboard callbacks (`PlayerKeyboardCallback`,
 * `KeyboardNavigationHandler`) so gamepad input reuses the same business flows
 * as keyboard / remote input.
 */
sealed class GamepadAction {

    sealed class PlayerAction : GamepadAction() {
        object PlayPause : PlayerAction()
        object Next : PlayerAction()
        object Prev : PlayerAction()
        /** Relative seek in milliseconds (negative = backward). */
        data class Seek(val ms: Long) : PlayerAction()
        /** Volume nudge. `up = true` raises, `false` lowers. */
        data class Volume(val up: Boolean) : PlayerAction()
        object ToggleHud : PlayerAction()
        object ToggleHints : PlayerAction()
        object Exit : PlayerAction()
    }

    sealed class BrowserAction : GamepadAction() {
        object Select : BrowserAction()
        object Back : BrowserAction()
        object MultiSelect : BrowserAction()
        object ContextMenu : BrowserAction()
        object Search : BrowserAction()
        /** `forward = true` → next tab, `false` → previous tab. */
        data class SwitchTab(val forward: Boolean) : BrowserAction()
    }
}
