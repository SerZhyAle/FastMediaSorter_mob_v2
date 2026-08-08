package com.sza.fastmediasorter.domain.radio

import kotlinx.coroutines.flow.Flow

/**
 * S1441: capability seam for switching a radio on and off.
 *
 * Mirrors [com.sza.fastmediasorter.domain.networkmonitor.NetworkMonitorContract]: flavors that ship the
 * network monitor bind the real controller from src/networkMonitor, the rest bind a no-op from
 * src/networkMonitorDisabled, so src/main never guards on BuildConfig. The launcher is not the owner -
 * the network monitor consumes the same contract for its section switches.
 */
interface RadioControlContract {

    /** True when this build compiles a real controller; false means every caller goes straight to the system screen. */
    val isToggleSupported: Boolean

    /**
     * Current state of [kind], or `null` when it cannot be established.
     *
     * `null` means unknown, never off - a device that refuses the read must not be drawn as switched off.
     */
    fun state(kind: RadioKind): Flow<Boolean?>

    /**
     * Attempts to flip [kind] and reports whether it actually flipped.
     *
     * The platform's own return value is not proof: some firmwares accept `setWifiEnabled` and do nothing,
     * so only an observed state change counts. `false` therefore means "nothing happened, open the system
     * screen", covering a refusal, a missing permission and a build without a real controller alike.
     */
    suspend fun toggle(kind: RadioKind): Boolean
}
