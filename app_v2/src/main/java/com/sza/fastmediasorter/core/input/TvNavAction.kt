package com.sza.fastmediasorter.core.input

/**
 * Semantic navigation action produced by [TvKeyRouter] from raw KeyEvent.
 *
 * Consumers (BaseActivity subclasses) react to these rather than to raw key codes,
 * keeping the input source details out of screen logic.
 */
sealed interface TvNavAction {
    /** Move to next item / slide (DPAD_RIGHT, TAB). */
    data object Next : TvNavAction

    /** Move to previous item / slide (DPAD_LEFT, SHIFT+TAB). */
    data object Prev : TvNavAction

    /** Move focus up (DPAD_UP). */
    data object Up : TvNavAction

    /** Move focus down (DPAD_DOWN). */
    data object Down : TvNavAction

    /** Confirm / activate focused item (DPAD_CENTER, ENTER, NUMPAD_ENTER). */
    data object Select : TvNavAction

    /** Navigate back (BACK). */
    data object Back : TvNavAction
}
