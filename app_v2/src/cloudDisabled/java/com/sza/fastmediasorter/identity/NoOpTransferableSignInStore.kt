package com.sza.fastmediasorter.identity

import com.sza.fastmediasorter.domain.identity.transfer.TransferableSignInRecord
import com.sza.fastmediasorter.domain.identity.transfer.TransferableSignInStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inert [TransferableSignInStore] for the `cloudDisabled` source set (S2101).
 *
 * The `lite` and `foss` flavors bind no Google identity, so there is no sign-in state to carry to a
 * new device. `lite` still mounts `cloudSdk`, so the provider-secret call sites compile and run
 * there - they call this stub, which is why they need no flavor guard of their own.
 */
@Singleton
class NoOpTransferableSignInStore @Inject constructor() : TransferableSignInStore {

    override suspend fun isAvailable(): Boolean = false

    override suspend fun save(record: TransferableSignInRecord): Boolean = false

    override suspend fun readOnce(): TransferableSignInRecord? = null

    override suspend fun clear() = Unit
}
