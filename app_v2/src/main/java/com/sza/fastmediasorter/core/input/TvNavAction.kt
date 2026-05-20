package com.sza.fastmediasorter.core.input

/**
 * Semantic input action produced by [TvKeyRouter] from a raw `KeyEvent`.
 *
 * Despite the historical `Tv` prefix, this hierarchy covers every non-gamepad
 * key input the app receives, grouped into three sub-families:
 *
 *  - [Nav]      - focus / page navigation: TV remote D-pad, physical keyboard.
 *  - [Media]    - media transport: car steering-wheel buttons, Bluetooth
 *                 car-audio remotes, wired/wireless headset hooks, Android
 *                 Auto media keys.
 *  - [Hardware] - physical device buttons: volume, mute, hardware menu /
 *                 search keys on phones, tablets, set-top boxes.
 *
 * Consumers (`BaseActivity.onTvNavigation` overrides) react to these semantic
 * actions instead of raw key codes, keeping the input-source matrix out of
 * screen logic.
 *
 * Gamepad / joystick events are NOT represented here - they continue to flow
 * through [GamepadInputManager] with its dedicated rate-limiting and analog
 * axis handling.
 */
sealed interface TvNavAction {

    /** Focus / page navigation. TV remote, keyboard. */
    sealed interface Nav : TvNavAction

    /** Media transport. Car steering wheel, BT headset, AAC remote. */
    sealed interface Media : TvNavAction

    /** Physical device buttons. Volume, mute, hardware menu / search. */
    sealed interface Hardware : TvNavAction

    // ── Nav ───────────────────────────────────────────────────────────────────

    /** Move to next slide (DPAD_RIGHT). TAB is intentionally NOT mapped - see TvKeyRouter. */
    data object Next : Nav

    /** Move to previous slide (DPAD_LEFT). SHIFT+TAB is intentionally NOT mapped - see TvKeyRouter. */
    data object Prev : Nav

    /** Move focus up (DPAD_UP). */
    data object Up : Nav

    /** Move focus down (DPAD_DOWN). */
    data object Down : Nav

    /** Confirm / activate focused item (DPAD_CENTER, ENTER, NUMPAD_ENTER). */
    data object Select : Nav

    /** Navigate back (BACK). */
    data object Back : Nav

    // ── Media ─────────────────────────────────────────────────────────────────

    /** Toggle play/pause (MEDIA_PLAY_PAUSE, HEADSETHOOK). */
    data object PlayPause : Media

    /** Start playback (MEDIA_PLAY). */
    data object Play : Media

    /** Pause playback (MEDIA_PAUSE). */
    data object Pause : Media

    /** Stop playback (MEDIA_STOP). */
    data object Stop : Media

    /** Skip to next track / file (MEDIA_NEXT). */
    data object MediaNext : Media

    /** Skip to previous track / file (MEDIA_PREVIOUS). */
    data object MediaPrev : Media

    /** Seek forward (MEDIA_FAST_FORWARD). */
    data object FastForward : Media

    /** Seek backward (MEDIA_REWIND). */
    data object Rewind : Media

    // ── Hardware ──────────────────────────────────────────────────────────────

    /** Volume up (VOLUME_UP). */
    data object VolumeUp : Hardware

    /** Volume down (VOLUME_DOWN). */
    data object VolumeDown : Hardware

    /** Toggle / set mute (VOLUME_MUTE). */
    data object VolumeMute : Hardware

    /** Hardware menu button (MENU). */
    data object Menu : Hardware

    /** Hardware search button (SEARCH). */
    data object Search : Hardware
}
