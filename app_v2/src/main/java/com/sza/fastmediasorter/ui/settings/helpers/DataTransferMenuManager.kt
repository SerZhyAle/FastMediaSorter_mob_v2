package com.sza.fastmediasorter.ui.settings.helpers

import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.domain.model.transfer.TransferDataKind
import com.sza.fastmediasorter.domain.model.transfer.TransferMedium
import javax.inject.Inject

/**
 * S1565: decides which data kinds and which media the transfer menu may offer in this build.
 *
 * The two questions stay separate because the cloud and streams capabilities differ in the `photos`
 * column of `docs/FLAVOR_MATRIX.md`: photos ships Drive but no streams, so one merged flag would
 * either hide Drive there or offer a stream list the flavor does not have. The streams group also
 * carries a third, runtime condition - the user's own streams master toggle - which arrives as a
 * parameter rather than being read here, so nothing in this class touches settings storage.
 */
class DataTransferMenuManager @Inject constructor(
    private val capabilityAvailability: CapabilityAvailability
) {

    fun availableKinds(enableStreams: Boolean): List<TransferDataKind> =
        TransferDataKind.entries.filter { kind ->
            kind != TransferDataKind.PINNED_STREAMS ||
                (capabilityAvailability.isStreamsAvailable() && enableStreams)
        }

    fun availableMedia(): List<TransferMedium> = TransferMedium.entries.filter { medium ->
        medium != TransferMedium.GOOGLE_DRIVE || capabilityAvailability.isCloudAvailable()
    }
}
