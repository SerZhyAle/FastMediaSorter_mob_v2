package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearNetworkChannel
import kotlinx.coroutines.flow.StateFlow

/**
 * Answers what the watch's network link is right now.
 *
 * Deliberately free of Android types: the stream policy that consumes it is the part worth proving
 * off-device, and every watch class that touches the platform directly is untested today.
 */
interface WearNetworkChannelMonitor {

    /** Always holds a value - [WearNetworkChannel.NONE] until the platform reports otherwise. */
    val channel: StateFlow<WearNetworkChannel>
}
