package com.sza.fastmediasorter.domain.usecase

import com.google.gson.Gson
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.data.repository.wear.WearResourceIdAliasStore
import com.sza.fastmediasorter.data.repository.wear.WearResourceStampStore
import com.sza.fastmediasorter.data.repository.wear.WearResourceTombstoneStore
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.WearNetworkSourcePayload
import com.sza.fastmediasorter.domain.model.WearRecordMergeDecision
import com.sza.fastmediasorter.domain.model.WearRecordMergeResolver
import com.sza.fastmediasorter.domain.model.WearSourceTombstonePayload
import com.sza.fastmediasorter.domain.model.WearSourcesExportPayload
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * @param updated S2502: records this leg replaced because the watch edited them later. Before that
 *   ticket the leg could only add or skip, so a watch edit to a resource the phone already had was
 *   dropped in silence. Declared last with a default so the older two-value call sites still build.
 */
data class ImportWatchResult(val added: Int, val skipped: Int, val updated: Int = 0)

class ImportWatchSourcesUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val gson: Gson,
    // S2502: the phone's own record of when each resource was last edited here.
    private val wearResourceStampStore: WearResourceStampStore,
    // S2507: the deletions both sides have made, which rank against those edit times.
    private val wearResourceTombstoneStore: WearResourceTombstoneStore,
    // S2507 phase 04: which phone resource a watch-created source became, so a tombstone naming the
    // watch's own id still finds the row to delete.
    private val wearResourceIdAliasStore: WearResourceIdAliasStore
) {

    /**
     * @param receivedAtEpochMillis when this phone took delivery, in its own time base. With the
     *   payload's `sentAt` it measures the clock offset between the two devices, so the skew never has
     *   to be known in advance (S2502).
     */
    suspend operator fun invoke(
        payload: WearSourcesExportPayload,
        receivedAtEpochMillis: Long = System.currentTimeMillis()
    ): Result<ImportWatchResult> =
        runCatching {
            var added = 0
            var skipped = 0
            var updated = 0

            Timber.d("S2502: phone import leg entered, ${payload.sources.size} record(s), sentAt=${payload.sentAt}")
            val skewMillis = payload.sentAt?.let { receivedAtEpochMillis - it } ?: 0L
            // S2507: a tombstone always states the moment of deletion, so a comparison involving one
            // is always stamped on the sender's side, whatever the ordinary records alongside it
            // carry. Judging it through the batch resolver below would let an unstamped record from
            // an older watch resurrect a resource this phone deliberately deleted.
            val deletionResolver = WearRecordMergeResolver(senderCarriesStamps = true, skewMillis = skewMillis)
            // Applied first, so an ordinary record in the same batch is ranked against the tombstone
            // this batch just accepted rather than against the resource it is about to replace.
            applyIncomingTombstones(payload.tombstones, deletionResolver)

            val context = BatchContext(
                resources = resourceRepository.getAllResourcesSync(),
                stamps = wearResourceStampStore.readStamps(),
                tombstones = wearResourceTombstoneStore.read().associateBy { it.id },
                // A batch-level statement: a watch predating S2502 stamps nothing, and its records
                // are then applied exactly as they were before.
                resolver = WearRecordMergeResolver(
                    senderCarriesStamps = payload.sources.any { it.lastEditedAt != null },
                    skewMillis = skewMillis
                ),
                deletionResolver = deletionResolver
            )

            for (source in payload.sources) {
                when (importOne(source, context)) {
                    ImportOutcome.ADDED -> added++
                    ImportOutcome.UPDATED -> updated++
                    ImportOutcome.SKIPPED -> skipped++
                }
            }

            ImportWatchResult(added = added, skipped = skipped, updated = updated)
        }

    /** What one incoming record did to this phone's set, so the loop above only has to tally. */
    private enum class ImportOutcome { ADDED, UPDATED, SKIPPED }

    /** Everything one incoming record is ranked against, read once for the whole batch. */
    private class BatchContext(
        val resources: List<MediaResource>,
        val stamps: Map<String, Long>,
        val tombstones: Map<String, WearSourceTombstonePayload>,
        val resolver: WearRecordMergeResolver,
        val deletionResolver: WearRecordMergeResolver
    )

    /**
     * S2507: applies the watch's deletions before any ordinary record of the same batch.
     *
     * A tombstone the phone accepts is stored in the phone's own time base, so a later exchange ranks
     * it against local edits without measuring the skew a second time. A tombstone the phone rejects
     * is not stored at all - the local edit that beat it wins again on every later exchange, and
     * keeping the losing event would only give it a second chance it already lost.
     */
    private suspend fun applyIncomingTombstones(
        incoming: List<WearSourceTombstonePayload>,
        deletionResolver: WearRecordMergeResolver
    ) {
        if (incoming.isEmpty()) {
            return
        }
        Timber.d("S2507: phone import leg received ${incoming.size} tombstone(s) from the watch")
        val stamps = wearResourceStampStore.readStamps()
        for (tombstone in incoming) {
            // S2507 phase 04: a source the watch created itself lives here under an id this phone
            // issued, so both the local edit time and the row to delete are found under that id and
            // never under the watch's own. An ordinary record reaches it through S2502 ADR-3's
            // credential fallback, which a tombstone cannot use - it carries no address.
            val localId = wearResourceIdAliasStore.resolve(tombstone.id) ?: tombstone.id.toLongOrNull()
            val stampKey = localId?.toString() ?: tombstone.id
            val decision = deletionResolver.resolve(tombstone.deletedAt, stamps[stampKey])
            if (!decision.apply) {
                Timber.d("Keeping resource ${tombstone.id} - it was edited here after the watch deleted it")
                continue
            }
            localId?.let {
                resourceRepository.deleteResource(it)
                // The row it pointed at is gone, so the alias can only mislead a later exchange.
                wearResourceIdAliasStore.forget(tombstone.id)
            }
            wearResourceTombstoneStore.record(
                tombstone.copy(deletedAt = decision.stampEpochMillis ?: tombstone.deletedAt)
            )
        }
    }

    private suspend fun importOne(
        source: WearNetworkSourcePayload,
        context: BatchContext
    ): ImportOutcome {
        val resources = context.resources
        val stamps = context.stamps
        val resolver = context.resolver

        val tombstone = context.tombstones[source.id]
        if (tombstone != null) {
            if (!context.deletionResolver.resolve(source.lastEditedAt, tombstone.deletedAt).apply) {
                // The deletion is the later event, so the record stays gone and its tombstone stays.
                return ImportOutcome.SKIPPED
            }
            // The watch edited it after the deletion, so the resource comes back and the tombstone
            // must go - left in place it would delete the record again on the very next exchange.
            wearResourceTombstoneStore.forget(source.id)
        }

        // S2502 ADR-3: the phone ships its own resource id and the watch returns it unchanged, so an id
        // hit is the exact answer. The credential tuple stays as the fallback for a source the watch
        // created itself, which no phone id can name.
        val matchedById = source.id.toLongOrNull()?.let { id -> resources.firstOrNull { it.id == id } }
        val existingCredentials = if (matchedById != null) {
            null
        } else {
            credentialsRepository.getByTypeServerAndPort(
                type = source.type,
                server = source.server,
                port = source.port
            )
        }
        val matched = matchedById
            ?: existingCredentials?.let { creds ->
                resources.firstOrNull { it.credentialsId == creds.credentialId }
            }

        return when {
            // Credentials exist with no resource behind them. Inserting a second credential row for one
            // address is what the pre-S2502 skip avoided, and still is.
            matched == null && existingCredentials != null -> ImportOutcome.SKIPPED
            matched == null -> {
                addNewResource(source, resolver.resolve(source.lastEditedAt, null).stampEpochMillis)
                ImportOutcome.ADDED
            }
            else -> applyRanked(
                source = source,
                matched = matched,
                decision = resolver.resolve(source.lastEditedAt, stamps[matched.id.toString()])
            )
        }
    }

    private suspend fun applyRanked(
        source: WearNetworkSourcePayload,
        matched: MediaResource,
        decision: WearRecordMergeDecision
    ): ImportOutcome {
        if (!decision.apply) {
            // The phone's copy was edited later. Leaving it alone is the point - the watch receives
            // this side's version on the next exchange.
            Timber.d("Keeping locally edited resource ${matched.name} - watch copy is older")
            return ImportOutcome.SKIPPED
        }
        applyToExisting(source, matched, decision.stampEpochMillis)
        return ImportOutcome.UPDATED
    }

    private suspend fun addNewResource(source: WearNetworkSourcePayload, stampEpochMillis: Long?) {
        val credentialId = UUID.randomUUID().toString()
        credentialsRepository.insert(credentialsFrom(source, credentialId))
        val resource = MediaResource(
            name = source.name,
            path = source.basePath,
            type = resourceTypeOf(source),
            credentialsId = credentialId
        )
        val newId = resourceRepository.addResource(resource)
        // S2507 phase 04: only an id this phone did not issue is worth remembering - a numeric id is
        // already the key a later tombstone will name.
        if (source.id.toLongOrNull() == null) {
            Timber.d("S2507: aliasing watch-created source ${source.id} to phone resource $newId")
            wearResourceIdAliasStore.record(source.id, newId)
        }
        // S2502: addResource stamped this moment; the merged stamp is the watch's edit time corrected
        // into this phone's base, and overwriting it here is what keeps the next exchange honest.
        if (stampEpochMillis != null) {
            wearResourceStampStore.writeStamp(newId.toString(), stampEpochMillis)
        }
    }

    private suspend fun applyToExisting(
        source: WearNetworkSourcePayload,
        matched: MediaResource,
        stampEpochMillis: Long?
    ) {
        val credentialId = matched.credentialsId
        if (credentialId != null) {
            val stored = credentialsRepository.getByCredentialId(credentialId)
            if (stored != null) {
                credentialsRepository.update(credentialsFrom(source, credentialId, rowId = stored.id))
            }
        }
        resourceRepository.updateResource(
            matched.copy(name = source.name, path = source.basePath)
        )
        if (stampEpochMillis != null) {
            wearResourceStampStore.writeStamp(matched.id.toString(), stampEpochMillis)
        }
    }

    private fun credentialsFrom(
        source: WearNetworkSourcePayload,
        credentialId: String,
        rowId: Long = 0
    ) = NetworkCredentialsEntity.create(
        credentialId = credentialId,
        type = source.type,
        server = source.server,
        port = source.port,
        username = source.username,
        plaintextPassword = source.password,
        domain = source.domain,
        shareName = source.shareName,
        sshPrivateKey = source.sshPrivateKey,
        id = rowId
    )

    private fun resourceTypeOf(source: WearNetworkSourcePayload): ResourceType =
        runCatching { ResourceType.valueOf(source.type) }.getOrDefault(ResourceType.SMB)
}
