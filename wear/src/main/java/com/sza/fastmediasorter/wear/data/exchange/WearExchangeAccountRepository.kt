package com.sza.fastmediasorter.wear.data.exchange

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** S4158: one phone entry handed to the watch - where to enroll, how to trust it, and the one-time code. */
data class WearExchangeJoin(
    val host: String,
    val port: Int,
    val login: String,
    val pinnedCertificate: String?,
    val pairingCode: String,
    /** The key half of the phone entry's pin (19 item AY); null from an older phone or without a pin. */
    val pinnedKey: String? = null,
) {
    override fun toString(): String = "WearExchangeJoin(port=$port, pinned=${pinnedCertificate != null})"
}

/**
 * S4158: the lifecycle of the watch's exchange entries - enrollment from a phone hand-over, the outgoing mark and
 * removal. Contract DEVICE-EXCHANGE items AA, AB and AD are the rules; the screen and the hand-over receiver call
 * these operations and never re-derive them. Adding an entry on the watch itself does not exist (strategic 5.3).
 */
@Singleton
class WearExchangeAccountRepository @Inject constructor(
    private val client: WearExchangeAccountClient,
    private val store: WearExchangeEntryStore,
    private val profile: WearExchangeDeviceProfile,
    private val e2e: WearExchangeE2eStore,
) {

    val entries: Flow<List<WearExchangeEntry>> = store.entries

    /**
     * Orders enroll against remove: a hand-over reads the held list and writes a token and an entry from it, so one
     * landing between a removal's revoke and its writes would re-create the entry the removal then deletes.
     */
    private val lifecycle = Mutex()

    /**
     * Enrolls the watch into [join]'s account. The same identity again re-enrolls the held entry with its
     * `deviceId`, which rotates its token (7.2); a new identity gets a fresh `deviceId`, regenerated once on
     * `id-collision` (AD 3). The entry is recorded only once its token was stored; the first entry the watch
     * holds becomes the outgoing one.
     */
    suspend fun enroll(join: WearExchangeJoin): WearExchangeEnrollResult = withContext(Dispatchers.IO) {
        lifecycle.withLock { enrollLocked(join) }
    }

    private suspend fun enrollLocked(join: WearExchangeJoin): WearExchangeEnrollResult {
        val held = store.all()
        val fresh = WearExchangeEntry(join.host, join.port, join.login, WearExchangeEntry.newDeviceId(), null)
        val previous = held.firstOrNull { it.identity == fresh.identity }
        val candidate = (previous ?: fresh).copy(
            host = join.host,
            port = join.port,
            login = join.login,
            pinnedCertificate = join.pinnedCertificate,
            pinnedKey = join.pinnedKey,
        )
        var enrolled = candidate
        var result = client.enroll(candidate, join.pairingCode, profile, e2e.keyFor(candidate.deviceId))
        if (previous == null && result.isIdCollision()) {
            e2e.forget(candidate.deviceId)
            enrolled = candidate.copy(deviceId = WearExchangeEntry.newDeviceId())
            result = client.enroll(enrolled, join.pairingCode, profile, e2e.keyFor(enrolled.deviceId))
        }
        return if (result is WearExchangeEnrollResult.Enrolled) {
            record(enrolled.copy(outgoing = previous?.outgoing ?: held.none { it.outgoing }), result)
        } else {
            // A key lives as long as its deviceId (item DF); an id that never became an entry takes its key along.
            if (previous == null) e2e.forget(enrolled.deviceId)
            result
        }
    }

    suspend fun setOutgoing(entry: WearExchangeEntry) = store.setOutgoing(entry.identity)

    /**
     * Removes [entry] (item AB): revokes the watch's own device when the server answers, then forgets the token
     * and the entry either way, without retrying in the background. Removing the outgoing entry passes the mark to
     * the first entry left that may produce: the store applies item DS on every write.
     *
     * Runs to its last write whatever happens to the caller. The revoke can block until a connect timeout, and a
     * wearer who leaves the screen meanwhile cancels the caller's scope: the writes after the revoke would then throw
     * at their first suspension point, leaving an entry whose token the server may already have revoked.
     */
    suspend fun remove(entry: WearExchangeEntry): WearExchangeRemoval = withContext(Dispatchers.IO + NonCancellable) {
        lifecycle.withLock {
            val token = store.token(entry.identity)
            val removal = if (token != null && revoked(entry, token)) {
                WearExchangeRemoval.REVOKED
            } else {
                WearExchangeRemoval.FORGOTTEN_LOCALLY
            }
            store.forgetToken(entry.identity)
            e2e.forget(entry.deviceId)
            store.remove(entry.identity)
            removal
        }
    }

    private suspend fun record(
        entry: WearExchangeEntry,
        enrolled: WearExchangeEnrollResult.Enrolled,
    ): WearExchangeEnrollResult = if (store.saveToken(entry.identity, enrolled.token)) {
        store.put(entry)
        WearExchangeEnrollResult.Enrolled(enrolled.token, entry.deviceId, e2e.existing(entry.deviceId)?.kem)
    } else {
        WearExchangeEnrollResult.Refused(WearExchangeEnrollResult.REASON_TOKEN_NOT_STORED)
    }

    private fun revoked(entry: WearExchangeEntry, token: String): Boolean = try {
        client.revokeOwnDevice(entry, token, profile.productVersion)
        true
    } catch (e: IOException) {
        // The class only: the message of a connect failure names the host, which is private (7.9).
        Timber.i("Exchange entry removed locally; the server was not reached (%s)", e.javaClass.simpleName)
        false
    }

    private fun WearExchangeEnrollResult.isIdCollision(): Boolean =
        this is WearExchangeEnrollResult.Refused && reason == WearExchangeEnvelope.REASON_ID_COLLISION
}
