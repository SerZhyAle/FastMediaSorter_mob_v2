package com.sza.fastmediasorter.networkmonitor

import com.sza.fastmediasorter.domain.networkmonitor.NetworkMonitorContract

/** S1433: the Network Monitor program is not compiled into this build. */
class NoOpNetworkMonitorContract : NetworkMonitorContract {

    override val isAvailableInBuild: Boolean = false
}
