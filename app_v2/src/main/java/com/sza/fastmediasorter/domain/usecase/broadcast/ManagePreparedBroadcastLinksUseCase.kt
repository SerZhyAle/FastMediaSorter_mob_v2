package com.sza.fastmediasorter.domain.usecase.broadcast

import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialRegistry
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastLinkChoice
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastTunnelIdentities
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeBroadcastAnnouncer
import com.sza.fastmediasorter.data.repository.DeviceExchangeAccountRepository
import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.data.security.e2e.E2eRandom
import com.sza.fastmediasorter.data.security.e2e.E2eRelayRecords
import com.sza.fastmediasorter.domain.model.BroadcastLinkPasswordRules
import com.sza.fastmediasorter.domain.model.DEFAULT_BROADCAST_STREAM_TITLE
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.PreparedBroadcastLink
import com.sza.fastmediasorter.domain.model.PreparedBroadcastStartOutcome
import com.sza.fastmediasorter.domain.model.PreparedLinkChoice
import com.sza.fastmediasorter.domain.model.SftpShareId
import com.sza.fastmediasorter.domain.repository.PreparedBroadcastLinkStore
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * S4247, DEVICE-EXCHANGE item DW: makes, lists, revokes and prunes the broadcasts prepared before their start. A
 * prepared link needs the outgoing entry (ADR-2), lives 1 to 30 days and at most [MAX_LIVE_LINKS] exist at once
 * (owner decision В5); it dies with its entry, its revocation or its expiry.
 */
class ManagePreparedBroadcastLinksUseCase @Inject constructor(
    private val store: PreparedBroadcastLinkStore,
    private val accounts: DeviceExchangeAccountRepository,
    private val credentials: BroadcastCredentialRegistry,
    private val tunnelIdentities: BroadcastTunnelIdentities,
    private val settings: SettingsRepository,
    private val announcer: ExchangeBroadcastAnnouncer,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    sealed interface Outcome {
        data object NoOutgoingEntry : Outcome
        data object LimitReached : Outcome
        data object InvalidPassword : Outcome
        data object NotStored : Outcome
        data class Prepared(val link: PreparedBroadcastLink) : Outcome
    }

    /** The live prepared links, oldest first; the first collection prunes the dead ones. */
    val links: Flow<List<PreparedBroadcastLink>>
        get() = store.links
            .onStart { prune() }
            .map { all -> live(all, System.currentTimeMillis()) }

    /** What the live broadcast did with a prepared link: the screen tells the person about a collision. */
    val startOutcome: StateFlow<PreparedBroadcastStartOutcome>
        get() = announcer.preparedOutcome

    suspend fun prepare(
        mode: BroadcastMode,
        lifetimeDays: Int,
        choice: PreparedLinkChoice,
        customPassword: String?,
        guests: List<String>,
    ): Outcome = withContext(ioDispatcher) {
        gate.withLock {
            pruneLocked()
            val entry = accounts.producer.first() ?: return@withContext Outcome.NoOutgoingEntry
            val now = System.currentTimeMillis()
            val stored = store.allOrNull() ?: return@withContext Outcome.NotStored
            if (live(stored, now).size >= MAX_LIVE_LINKS) return@withContext Outcome.LimitReached
            val linkChoice = linkChoiceOf(choice, customPassword) ?: return@withContext Outcome.InvalidPassword
            val pair = credentials.newLinkPair(linkChoice)
            val tunnel = if (entry.serverSeals) tunnelIdentities.prepare() else null
            val broadcast = settings.getSettings().first().broadcast
            val link = PreparedBroadcastLink(
                broadcastId = SftpShareId.generate(),
                entryIdentity = entry.identity,
                publicEndpoint = entry.publicEndpoint ?: "${entry.host}:${entry.port}",
                certFingerprint = entry.pinnedCertificate,
                mode = mode.name,
                title = broadcast.streamTitle.ifBlank { DEFAULT_BROADCAST_STREAM_TITLE },
                sourceId = broadcast.sourceDeviceId,
                createdAtMs = now,
                untilMs = now + TimeUnit.DAYS.toMillis(lifetimeDays.coerceIn(MIN_DAYS, MAX_DAYS).toLong()),
                linkChoice = choice,
                linkUser = pair?.user,
                linkPassword = pair?.password,
                relayLinkKey = E2eBytes.b64url(E2eRandom.secure.next(E2eRelayRecords.GROUP_KEY_LENGTH)),
                tunnelAlias = tunnel?.alias,
                tunnelPin = tunnel?.pin,
                guests = guests.map(String::trim).filter(DeviceExchangeAccountEntry::isValidLogin).distinct(),
            )
            if (!store.put(link)) {
                tunnel?.let { tunnelIdentities.deletePrepared(it.alias) }
                return@withContext Outcome.NotStored
            }
            Outcome.Prepared(link)
        }
    }

    suspend fun revoke(broadcastId: String) = withContext(ioDispatcher) {
        gate.withLock {
            val link = store.all().firstOrNull { it.broadcastId == broadcastId }
            store.remove(broadcastId)
            link?.tunnelAlias?.let(tunnelIdentities::deletePrepared)
        }
    }

    /** Drops every record whose time passed or whose entry is gone, and every prepared key no record names. */
    suspend fun prune() = withContext(ioDispatcher) { gate.withLock { pruneLocked() } }

    // Runs under [gate]: a sweep that ran between prepare's key and its stored record would delete the fresh key.
    private suspend fun pruneLocked() {
        val now = System.currentTimeMillis()
        val identities = accounts.entries.first().map { it.identity }.toSet()
        // An unreadable list names none of its keys, so sweeping from it would delete the keys of intact records.
        val stored = readStored() ?: return
        val (dead, kept) = stored.partition { it.untilMs <= now || it.entryIdentity !in identities }
        dead.forEach { link ->
            store.remove(link.broadcastId)
            link.tunnelAlias?.let(tunnelIdentities::deletePrepared)
        }
        tunnelIdentities.sweepPrepared(kept.filterNot { it.consumed }.mapNotNull { it.tunnelAlias }.toSet())
    }

    /**
     * The stored records, or null while the list stays unreadable. A failed read is retried first, since a Keystore
     * failure is usually transient; a list that fails every attempt is discarded as corrupt, and null remains only
     * when even that did not clear it.
     */
    private suspend fun readStored(): List<PreparedBroadcastLink>? {
        repeat(UNREADABLE_ATTEMPTS) { attempt ->
            store.allOrNull()?.let { return it }
            if (attempt < UNREADABLE_ATTEMPTS - 1) delay(UNREADABLE_RETRY_MS)
        }
        Timber.w("ManagePreparedBroadcastLinksUseCase: the prepared links stayed unreadable, discarding them")
        return if (store.discardUnreadable()) store.allOrNull() else null
    }

    private fun live(all: List<PreparedBroadcastLink>, now: Long) =
        all.filter { it.isLive(now) }.sortedBy { it.createdAtMs }

    private fun linkChoiceOf(choice: PreparedLinkChoice, customPassword: String?): BroadcastLinkChoice? =
        when (choice) {
            PreparedLinkChoice.GENERATED -> BroadcastLinkChoice.Generated
            PreparedLinkChoice.OPEN -> BroadcastLinkChoice.Open
            PreparedLinkChoice.CUSTOM ->
                customPassword
                    ?.takeIf { BroadcastLinkPasswordRules.validate(it) == BroadcastLinkPasswordRules.Verdict.OK }
                    ?.let(BroadcastLinkChoice::Custom)
        }

    companion object {
        const val MAX_LIVE_LINKS = 5
        const val DEFAULT_DAYS = 7
        const val MIN_DAYS = 1
        const val MAX_DAYS = 30

        private const val UNREADABLE_ATTEMPTS = 3
        private const val UNREADABLE_RETRY_MS = 500L

        // The use case is unscoped, so each injection point holds its own instance; only a shared lock keeps a
        // prune of one collection from sweeping the key a prepare of another has made and not yet stored.
        private val gate = Mutex()
    }
}
