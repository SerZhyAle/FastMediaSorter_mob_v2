package com.sza.fastmediasorter.identity

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.identity.transfer.TransferableSignInRecordCodec
import com.sza.fastmediasorter.domain.identity.transfer.TransferableSignInRecord
import com.sza.fastmediasorter.domain.identity.transfer.TransferableSignInStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TransferableSignInStore] over Google Block Store, for the `cloudEnabled` source set (S2101).
 *
 * Every branch this ticket's constraints call for is contained here, so callers above the port
 * neither know the mechanism nor check an API level: the cloud-backup request is gated on
 * end-to-end encryption being available, which needs API 29 and a screen lock, and a device below
 * that boundary still gets device-to-device transfer, available from API 23 and so covering the
 * `legacy` flavor.
 *
 * Nothing here throws. A failure returns the port's failure value and is logged, because a
 * restoration that surfaces an error is worse than one that quietly does not happen - the user is
 * then simply offered the ordinary sign-in.
 */
@Singleton
class BlockStoreTransferableSignInStore @Inject constructor(
    private val gateway: BlockStoreGateway,
    private val codec: TransferableSignInRecordCodec,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : TransferableSignInStore {

    override suspend fun isAvailable(): Boolean = withContext(ioDispatcher) {
        runCatching { gateway.isClientAvailable() }
            .onFailure { Timber.w(it, "Block Store availability check failed") }
            .getOrDefault(false)
    }

    override suspend fun save(record: TransferableSignInRecord): Boolean = withContext(ioDispatcher) {
        Timber.d("S2101: Block Store save requested for ${record.entries.size} entry(ies)")
        val bytes = codec.encode(record)
        if (bytes.size > MAX_ENTRY_BYTES) {
            // Refusing beats truncating: a truncated record decodes into a plausible but wrong
            // sign-in state on the new device, whereas a refused write leaves the ordinary sign-in.
            Timber.w("Transferable sign-in record is %d bytes, over the %d limit", bytes.size, MAX_ENTRY_BYTES)
            return@withContext false
        }
        runCatching {
            gateway.storeBytes(RECORD_KEY, bytes, backupToCloud = gateway.isEndToEndEncryptionAvailable())
            true
        }.onFailure { Timber.w(it, "Failed to store transferable sign-in record") }
            .getOrDefault(false)
    }

    override suspend fun readOnce(): TransferableSignInRecord? = withContext(ioDispatcher) {
        Timber.d("S2101: Block Store read requested")
        runCatching { gateway.retrieveBytes(RECORD_KEY)?.let(codec::decode) }
            .onFailure { Timber.w(it, "Failed to read transferable sign-in record") }
            .getOrNull()
    }

    override suspend fun clear() {
        withContext(ioDispatcher) {
            runCatching { gateway.deleteBytes(RECORD_KEY) }
                .onFailure { Timber.w(it, "Failed to clear transferable sign-in record") }
        }
    }

    private companion object {
        /**
         * One key holds the whole record. Block Store allows 16 keys, but a per-provider key would
         * make a partial read - some providers restored, others silently missing - indistinguishable
         * from a provider that was never signed in to.
         */
        const val RECORD_KEY = "fastmediasorter_signin_v1"

        /** Documented Block Store limit: 4 KB per entry. */
        const val MAX_ENTRY_BYTES = 4096
    }
}
