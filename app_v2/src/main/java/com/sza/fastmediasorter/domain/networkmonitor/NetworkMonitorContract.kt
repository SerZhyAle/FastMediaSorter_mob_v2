package com.sza.fastmediasorter.domain.networkmonitor

/**
 * S1433: capability seam for the Network Monitor program.
 * Flavors that ship it bind the real implementation from src/networkMonitor; the rest bind a no-op
 * from src/networkMonitorDisabled, so src/main never guards on BuildConfig.
 */
interface NetworkMonitorContract {

    /** True when this build compiles the Network Monitor surface (standard / noLegal). */
    val isAvailableInBuild: Boolean
}
