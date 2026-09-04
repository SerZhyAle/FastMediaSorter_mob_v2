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
        applyIncomingTombstones(payload.tombstones, deletionResolver)

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

        Timber.i("Import complete: added=$added updated=$updated skipped=$skipped")
        requestWearTileRefreshUseCase(WearTileKind.RESOURCE)
        return ImportResult(added, updated, skipped)
    }

    /**
     * Applies one incoming record and says what it did to it. Written as a function returning an
     * outcome rather than as branches inside the loop, so the loop carries no jump statements and the
     * three counters cannot drift from what was actually written.
     */
    /**
     * S2507: applies the phone's deletions before any ordinary record of the same batch.
     *
     * An accepted tombstone is stored in this watch's own time base, so a later exchange ranks it
     * against local edits without measuring the skew again. A rejected one is not stored: the local
     * edit that beat it wins again every time, and keeping the losing event would only hand it a
     * second chance it already lost.
     */
    private suspend fun applyIncomingTombstones(
        incoming: List<WearSourceTombstonePayload>,
        deletionResolver: WearRecordMergeResolver
    ) {
        if (incoming.isEmpty()) {
            return
        }
        Timber.d("S2507: watch import leg received ${incoming.size} tombstone(s) from the phone")
        val stored = repository.getAllSources().associateBy { it.id }
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
        }
    }

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
            Timber.w("Unknown type ${item.type} - skipping")
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
        basePath = NetworkBasePath.normalize(item.basePath, type, item.shareName),
        domain = item.domain,
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
