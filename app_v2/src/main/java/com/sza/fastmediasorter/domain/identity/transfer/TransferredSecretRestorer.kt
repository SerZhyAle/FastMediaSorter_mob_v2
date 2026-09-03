package com.sza.fastmediasorter.domain.identity.transfer

/**
 * Writes one provider's transferred secret back into that provider's own storage (S2101).
 *
 * One implementation per provider that keeps a refresh token in app-owned storage, bound into a map
 * keyed by [TransferableSignInProviderKeys]. Adding a fourth provider therefore adds a binding, not
 * a branch in [RestoreTransferredSignInUseCase] - which is the extensibility the strategic spec asks
 * of the port.
 */
interface TransferredSecretRestorer {

    /**
     * Restores from [payload], which is whatever the same provider's writer put there.
     *
     * Must do nothing when the provider already holds a credential on this device: a restore may
     * never displace one the user established here.
     */
    suspend fun restore(payload: Map<String, String>)
}
