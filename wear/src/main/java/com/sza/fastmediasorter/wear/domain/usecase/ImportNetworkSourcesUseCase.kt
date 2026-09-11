package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.ImportResult
import com.sza.fastmediasorter.wear.domain.model.NetworkBasePath
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceMerge
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearEndpoint
import com.sza.fastmediasorter.wear.domain.model.WearNetworkSourcePayload
import com.sza.fastmediasorter.wear.domain.model.WearRecordMergeResolver
import com.sza.fastmediasorter.wear.domain.model.WearSourceTombstonePayload
import com.sza.fastmediasorter.wear.domain.model.WearSyncPayload
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import timber.log.Timber
import javax.inject.Inject

private const val STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000L
private const val MIN_PORT = 1
private const val MAX_PORT = 65535

/** S2887: what `WearNetworkSourcePayload.basePath` claimed as its Kotlin default. */
private const val DEFAULT_BASE_PATH = "/"

/** S2502: what the import did with one incoming record. */
private enum class ImportOutcome { ADDED, UPDATED, SKIPPED }

class ImportNetworkSourcesUseCase @Inject constructor(
    private val repository: NetworkSourceRepository,
    private val requestWearTileRefreshUseCase: RequestWearTileRefreshUseCase
) {
    /**
     * @param receivedAtEpochMillis when this watch took delivery, in its own time base. Together with
     *   the payload's `sentAt` it measures the two devices' clock offset, so the skew never has to be
     *   known in advance (S2502).
     */
    suspend operator fun invoke(
        payload: WearSyncPayload,
        receivedAtEpochMillis: Long = System.currentTimeMillis()
    ): ImportResult {
        if (receivedAtEpochMillis - payload.sentAt > STALE_THRESHOLD_MS) {
            Timber.w("Sync payload is stale (sentAt=${payload.sentAt}) - ignoring")
            return ImportResult(added = 0, updated = 0, skipped = 0)
        }

        Timber.d("S2502: watch import leg entered, skew=${receivedAtEpochMillis - payload.sentAt} ms")
        val skewMillis = receivedAtEpochMillis - payload.sentAt
        // S2507: a tombstone always states the moment of deletion, so a comparison involving one is
        // always stamped on the sender's side. Judging it through the batch resolver below would let
        // an unstamped record from an older phone resurrect a source deleted here on purpose.
        val deletionResolver = WearRecordMergeResolver(senderCarriesStamps = true, skewMillis = skewMillis)
        // Applied before any ordinary record of the same batch, so a record is ranked against the
        // tombstone this batch just accepted rather than against the source it is about to replace.
        val deleted = applyIncomingTombstones(payload.tombstones, deletionResolver)
        // S2882: after the deletions and before the records, so the set this batch's own records are
        // ranked against is the one the phone's withdrawals already left behind.
        // S2932: both kinds of disappearance share one counter - the phone reports either as "Removed
        // N source(s)". No id is counted twice, because the withdrawals re-read what is still here.
        val removed = deleted + applyDeselections(payload.deselectedIds)
        Timber.d("S2932: watch ack removed=$removed, of which tombstone deletions applied=$deleted")

        val stored = repository.getAllSources().toMutableList()
        val localTombstones = repository.getTombstones().associateBy { it.id }
        // S2502: a batch-level statement, not a per-record one. A phone that predates this ticket
        // stamps nothing at all, and its records are then applied exactly as they were before.
        val resolver = WearRecordMergeResolver(
            senderCarriesStamps = payload.sources.any { it.lastEditedAt != null },
            skewMillis = skewMillis
        )
        var added = 0
        var updated = 0
        var skipped = 0

        for (item in payload.sources) {
            when (applyOne(item, resolver, deletionResolver, localTombstones, stored)) {
                ImportOutcome.ADDED -> added++
                ImportOutcome.UPDATED -> updated++
                ImportOutcome.SKIPPED -> skipped++
            }
        }

        Timber.i("Import complete: added=$added updated=$updated skipped=$skipped removed=$removed")
        requestWearTileRefreshUseCase(WearTileKind.RESOURCE)
        return ImportResult(added, updated, skipped, removed)
    }

    /**
     * S2882: deletes the sources the phone declared unwanted here, and returns how many went.
     *
     * A null list is a phone that predates the field, and it means "the sender said nothing about
     * exclusions" rather than "exclude nothing" - Gson leaves an absent field null whatever the Kotlin
     * default says, so reading null as an empty declaration is the one mistake that would make an
     * older phone wipe this watch.
     *
     * No tombstone is written. A withdrawn source is not a deleted resource: the phone still has it,
     * and a tombstone would travel back on this watch's own export and delete it there - the very loss
     * the unticked box exists to avoid.
     */
    private suspend fun applyDeselections(ids: List<String>?): Int {
        if (ids.isNullOrEmpty()) {
            return 0
        }
        Timber.d("S2882: watch import received ${ids.size} withdrawn id(s)")
        val present = repository.getAllSources().map { it.id }.toSet()
        var removed = 0
        for (id in ids.filter { it in present }) {
            repository.deleteSource(id)
            removed++
        }
        return removed
    }

    /**
     * S2507: applies the phone's deletions before any ordinary record of the same batch.
     *
     * An accepted tombstone is stored in this watch's own time base, so a later exchange ranks it
     * against local edits without measuring the skew again. A rejected one is not stored: the local
     * edit that beat it wins again every time, and keeping the losing event would only hand it a
     * second chance it already lost.
     *
     * @return S2932: how many sources this batch actually took off the watch. A rejected tombstone
     *   deleted nothing, and an accepted one for an id this watch never held is still stored - so the
     *   source cannot come back later - yet removed nothing either. Neither is counted.
     */
    private suspend fun applyIncomingTombstones(
        incoming: List<WearSourceTombstonePayload>?,
        deletionResolver: WearRecordMergeResolver
    ): Int {
        // S2885: the guard sits here rather than at the call site so a second caller inherits it. Null
        // is a phone that ships no deletions at all, which for this list means the same as declaring
        // none - unlike `deselectedIds`, where null and empty must stay distinguishable.
        if (incoming.isNullOrEmpty()) {
            return 0
        }
        Timber.d("S2507: watch import leg received ${incoming.size} tombstone(s) from the phone")
        val stored = repository.getAllSources().associateBy { it.id }
        var deleted = 0
        for (tombstone in incoming) {
            val decision = deletionResolver.resolve(tombstone.deletedAt, stored[tombstone.id]?.lastEditedAt)
            if (!decision.apply) {
                Timber.d("Keeping source ${tombstone.id} - it was edited here after the phone deleted it")
                continue
            }
            repository.deleteSource(tombstone.id)
            repository.recordTombstone(
                tombstone.copy(deletedAt = decision.stampEpochMillis ?: tombstone.deletedAt)
            )
            if (tombstone.id in stored) {
                deleted++
            }
        }
        return deleted
    }

    /**
     * Applies one incoming record and says what it did to it. Written as a function returning an
     * outcome rather than as branches inside the loop, so the loop carries no jump statements and the
     * three counters cannot drift from what was actually written.
     */
    private suspend fun applyOne(
        item: WearNetworkSourcePayload,
        resolver: WearRecordMergeResolver,
        deletionResolver: WearRecordMergeResolver,
        localTombstones: Map<String, WearSourceTombstonePayload>,
        stored: MutableList<NetworkSource>
    ): ImportOutcome {
        val tombstone = localTombstones[item.id]
        if (tombstone != null) {
            if (!deletionResolver.resolve(item.lastEditedAt, tombstone.deletedAt).apply) {
                // The deletion is the later event, so the source stays gone and its tombstone stays.
                return ImportOutcome.SKIPPED
            }
            // The phone edited it after the deletion, so the source comes back and the tombstone must
            // go - left in place it would delete the record again on the very next exchange.
            repository.removeTombstone(item.id)
        }
        val type = parseType(item.type)
        return if (type == null) {
            Timber.w(
                "Type ${item.type} is outside the phone/watch source contract - no client here can " +
                    "open it, so source ${item.id} is skipped"
            )
            ImportOutcome.SKIPPED
        } else {
            val incoming = toSource(item, type)
            val index = NetworkSourceMerge.indexOfMatch(stored, incoming)
            val decision = resolver.resolve(
                incomingStamp = item.lastEditedAt,
                localStamp = if (index == -1) null else stored[index].lastEditedAt
            )
            if (!decision.apply) {
                // S2502: the stored record was edited later on this watch. Leaving it alone is the
                // whole point - the phone will receive this side's version on the next exchange.
                Timber.d("Keeping locally edited source ${incoming.name} - phone copy is older")
                ImportOutcome.SKIPPED
            } else {
                store(incoming.copy(lastEditedAt = decision.stampEpochMillis), index, stored)
            }
        }
    }

    private suspend fun store(
        applied: NetworkSource,
        index: Int,
        stored: MutableList<NetworkSource>
    ): ImportOutcome {
        repository.upsertSource(applied)
        return if (index == -1) {
            stored.add(applied)
            ImportOutcome.ADDED
        } else {
            stored[index] = applied
            ImportOutcome.UPDATED
        }
    }

    /**
     * S2641: these three branches are one half of the phone/watch source contract; the other half is
     * `ResourceType.WATCH_TRANSFERABLE` on the phone, which decides what is put on the wire. The pair
     * is compared by `scripts/quality/assert-wear-wire-vocabulary-parity.ps1`, so a branch added here
     * without the matching member there, or the reverse, fails that gate. Written as an explicit
     * `when` rather than `NetworkSourceType.valueOf` on purpose: this watch may declare a type it can
     * open by hand but must never accept over the wire, and only a listed branch says which is which.
     */
    private fun parseType(raw: String): NetworkSourceType? = when (raw.uppercase()) {
        "SMB" -> NetworkSourceType.SMB
        "FTP" -> NetworkSourceType.FTP
        "SFTP" -> NetworkSourceType.SFTP
        else -> null
    }

    private fun toSource(item: WearNetworkSourcePayload, type: NetworkSourceType) = NetworkSource(
        id = item.id,
        type = type,
        name = item.name,
        server = item.server,
        port = item.port,
        username = item.username,
        password = item.password,
        shareName = item.shareName,
        // S1556: the phone ships a full URL; every watch client wants the path below the
        // connection it opens, so the conversion happens once, here.
        // S2887: the payload's defaults live here now - Gson never ran the ones on the declarations.
        basePath = NetworkBasePath.normalize(item.basePath ?: DEFAULT_BASE_PATH, type, item.shareName),
        domain = item.domain.orEmpty(),
        sshPrivateKey = item.sshPrivateKey,
        hostKeyFingerprint = item.hostKeyFingerprint,
        // S2129: stored opaque. Resolution happens at draw time, so an id this build does
        // not know still imports and simply falls back to the type glyph.
        iconId = item.iconId,
        // S2487: store allowed types and allFiles configuration sent from phone
        supportedMediaTypes = item.supportedMediaTypes,
        allFiles = item.allFiles ?: false,
        // S2488: entries are filtered one by one, so one malformed endpoint costs its own
        // entry rather than the source or the whole exchange.
        endpoints = item.endpoints
            ?.filter { it.host.isNotBlank() && it.port in MIN_PORT..MAX_PORT }
            ?.map { WearEndpoint(it.host, it.port) }
            ?.ifEmpty { null }
    )
}
