package com.sza.fastmediasorter.ui.launcher.tray

import com.sza.fastmediasorter.domain.model.AppSettings

/**
 * S1415: everything the taskbar tray may show. The declaration order is the render order - left to right,
 * clock first and battery last at the right edge (strategic §3.3), with the new icons between them in the
 * reading order of the Android status bar.
 *
 * This is the one place that lists what the status area can contain, so the next indicator is added here
 * rather than by editing the taskbar layout. S1431 reuses the same registry for its top-strip placement,
 * which is what keeps the two placements from offering different sets.
 *
 * S2027: [TETHERING] sits immediately before [NETWORK] because strategic §5.1 pillar 3 and acceptance
 * criterion 3 both place it next to the network glyph - the two report on the same radio, so a reader
 * scanning for connectivity finds them together instead of five slots apart.
 */
enum class LauncherTrayIndicator {
    CLOCK,
    BLUETOOTH,
    SIM1,
    SIM2,
    SPEED_RX,
    SPEED_TX,
    TETHERING,
    NETWORK,
    BATTERY,
}

/** Which indicators the user kept - one switch per indicator, per strategic §2 goal 1. */
data class LauncherTrayComposition(
    val clock: Boolean,
    val bluetooth: Boolean,
    val tethering: Boolean,
    val sim1: Boolean,
    val sim2: Boolean,
    val speed: Boolean,
    val network: Boolean,
    val battery: Boolean,
) {

    fun isEnabled(indicator: LauncherTrayIndicator): Boolean = when (indicator) {
        LauncherTrayIndicator.CLOCK -> clock
        LauncherTrayIndicator.BLUETOOTH -> bluetooth
        LauncherTrayIndicator.SIM1 -> sim1
        LauncherTrayIndicator.SIM2 -> sim2
        LauncherTrayIndicator.SPEED_RX -> speed
        LauncherTrayIndicator.SPEED_TX -> speed
        LauncherTrayIndicator.TETHERING -> tethering
        LauncherTrayIndicator.NETWORK -> network
        LauncherTrayIndicator.BATTERY -> battery
    }

    companion object {
        fun from(settings: AppSettings): LauncherTrayComposition = LauncherTrayComposition(
            clock = settings.launcherTrayShowClock,
            bluetooth = settings.launcherTrayShowBluetooth,
            tethering = settings.launcherTrayShowTethering,
            sim1 = settings.launcherTrayShowSim1,
            sim2 = settings.launcherTrayShowSim2,
            speed = settings.launcherTrayShowSpeed,
            network = settings.launcherTrayShowNetwork,
            battery = settings.launcherTrayShowBattery,
        )
    }
}
