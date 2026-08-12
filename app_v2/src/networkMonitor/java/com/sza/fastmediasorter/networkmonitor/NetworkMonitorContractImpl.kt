package com.sza.fastmediasorter.networkmonitor

import com.sza.fastmediasorter.domain.networkmonitor.NetworkMonitorContract

/** S1433: the Network Monitor program is compiled into this build. */
class NetworkMonitorContractImpl : NetworkMonitorContract {

    override val isAvailableInBuild: Boolean = true
}
