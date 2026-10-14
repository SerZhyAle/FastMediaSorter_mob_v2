package com.sza.fastmediasorter.data.remote.exchange

import com.sza.fastmediasorter.domain.model.ExchangeBinding

/**
 * Where the original PIN of an attached protected resource rests (contract DEVICE-EXCHANGE 17 item AI): the
 * platform secret store, keyed by the resource's binding - entry identity and `resourceId` - never a plain file
 * and never a store a backup sweeps. The PIN reaches it from the user at attach, once per resource.
 */
interface ExchangeResourcePinStore {

    suspend fun pin(binding: ExchangeBinding): String?

    /** False when the secret store refused to keep it, so the caller never believes a lost write. */
    suspend fun save(binding: ExchangeBinding, pin: String): Boolean

    suspend fun forget(binding: ExchangeBinding)

    /** Carries the PINs of [resourceIds] from entry identity [from] to [to], as the entry's address changes. */
    suspend fun move(from: String, to: String, resourceIds: Collection<String>)
}
