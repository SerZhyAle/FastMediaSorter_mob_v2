package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.network.SmbConnectionManager
import javax.inject.Inject

/**
 * UseCase for resetting all SMB connections and error state.
 * Used for manual recovery from persistent connection issues.
 */
class ResetSmbConnectionsUseCase @Inject constructor(
    private val smbConnectionManager: SmbConnectionManager
) {
    /**
     * Reset all SMB connections, clear error counters, and reinitialize clients.
     * This can help recover from persistent connection issues without restarting the app.
     */
    operator fun invoke() {
        smbConnectionManager.resetAllConnections()
    }
}
