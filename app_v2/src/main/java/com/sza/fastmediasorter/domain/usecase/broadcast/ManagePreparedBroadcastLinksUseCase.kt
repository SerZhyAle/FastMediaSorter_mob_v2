package com.sza.fastmediasorter.domain.usecase.broadcast

import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialRegistry
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastLinkChoice
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastTunnelIdentities
import com.sza.fastmediasorter.data.repository.DeviceExchangeAccountRepository
import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.data.security.e2e.E2eRandom
import com.sza.fastmediasorter.data.security.e2e.E2eRelayRecords
import com.sza.fastmediasorter.domain.model.BroadcastLinkPasswordRules
import com.sza.fastmediasorter.domain.model.DEFAULT_BROADCAST_STREAM_TITLE
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.PreparedBroadcastLink
import com.sza.fastmediasorter.domain.model.PreparedLinkChoice
import com.sza.fastmediasorter.domain.model.SftpShareId
import com.sza.fastmediasorter.domain.repository.PreparedBroadcastLinkStore
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
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

    suspend fun prepare(
        mode: BroadcastMode,
        lifetimeDays: Int,
        choice: PreparedLinkChoice,
        customPassword: String?,
        guests: List<String>,
    ): Outcome = withContext(ioDispatcher) {
        prune()
        val entry = accounts.producer.first() ?: return@withContext Outcome.NoOutgoingEntry
        val now = System.currentTimeMillis()
        if (live(store.all(), now).size >= MAX_LIVE_LINKS) return@withContext Outcome.LimitReached
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

    suspend fun revoke(broadcastId: String) = withContext(ioDispatcher) {
        val link = store.all().firstOrNull { it.broadcastId == broadcastId }
        store.remove(broadcastId)
        link?.tunnelAlias?.let(tunnelIdentities::deletePrepared)
    }

    /** Drops every record whose time passed or whose entry is gone, and every prepared key no record names. */
    suspend fun prune() = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val identities = accounts.entries.first().map { it.identity }.toSet()
        val (dead, kept) = store.all().partition { it.untilMs <= now || it.entryIdentity !in identities }
        dead.forEach { link ->
            store.remove(link.broadcastId)
            link.tunnelAlias?.let(tunnelIdentities::deletePrepared)
        }
        tunnelIdentities.sweepPrepared(kept.filterNot { it.consumed }.mapNotNull { it.tunnelAlias }.toSet())
    }

    private fun live(all: List<PreparedBroadcastLink>, now: Long) =
        all.filter { it.isLive(now) }.sortedBy { it.createdAtMs }

    private fun linkChoiceOf(choice: PreparedLinkChoice, customPassword: String?): BroadcastLinkChoice? =
        when (choice) {
            PreparedLinkChoice.GENERATED -> BroadcastLinkChoice.Generated
            PreparedLinkChoice.OPEN -> BroadcastLinkChoice.Open
            PreparedLinkChoice.CUSTOM -> customPassword
                ?.takeIf { BroadcastLinkPasswordRules.validate(it) == BroadcastLinkPasswordRules.Verdict.OK }
                ?.let(BroadcastLinkChoice::Custom)
        }

    companion object {
        const val MAX_LIVE_LINKS = 5
        const val DEFAULT_DAYS = 7
        const val MIN_DAYS = 1
        const val MAX_DAYS = 30
    }
}
