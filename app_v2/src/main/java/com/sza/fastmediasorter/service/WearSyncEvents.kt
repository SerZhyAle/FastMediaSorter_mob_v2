package com.sza.fastmediasorter.service

import com.sza.fastmediasorter.domain.model.WearPlaybackStatePayload
import com.sza.fastmediasorter.domain.model.WearSourcesExportPayload
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Process-wide event bus for messages received from the watch companion.
 *
 * Lives in `src/main` (GMS-free, pure coroutines) so the main-flavor `WearSyncViewModel` collector
 * compiles for every flavor, while the GMS-backed `PhoneWearListenerService` emitter (S0403
 * `wearGms` source set) publishes into it. Non-Wear flavors keep these flows inert - nothing emits.
 */
object WearSyncEvents {
    val ackFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val watchSourcesReceivedFlow = MutableSharedFlow<WearSourcesExportPayload>(extraBufferCapacity = 1)
    val watchPlaybackStateFlow = MutableSharedFlow<WearPlaybackStatePayload?>(extraBufferCapacity = 1)
}
