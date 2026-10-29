package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorParser
import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointCodec
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.domain.repository.BroadcastPasswordStore
import com.sza.fastmediasorter.domain.repository.BroadcastPendingStore
import com.sza.fastmediasorter.domain.usecase.streams.AddStreamSourceUseCase.AddResult
import timber.log.Timber
import javax.inject.Inject

class ImportStreamBroadcastUseCase @Inject constructor(
    private val parser: BroadcastDescriptorParser,
    private val addStreamSourceUseCase: AddStreamSourceUseCase,
    private val repository: StreamSourceRepository,
    private val passwordStore: BroadcastPasswordStore,
    private val pendingStore: BroadcastPendingStore,
) {
    /** [password] is what the person typed for a protected descriptor; null keeps the one already stored. */
    suspend operator fun invoke(rawPayload: String, password: String? = null): ImportResult =
        when (val outcome = parser.parseDetailed(rawPayload)) {
            is BroadcastDescriptorParser.ParseOutcome.Valid -> resolveImport(outcome.dto, password)
            BroadcastDescriptorParser.ParseOutcome.UnsupportedVersion ->
                ImportResult.UnsupportedVersion
            BroadcastDescriptorParser.ParseOutcome.Malformed -> ImportResult.InvalidDescriptor
        }

    /**
     * True when [rawPayload] is a protected descriptor this device holds no password for: it carries `auth`, no own
     * pair (item BF), and no row already keeps a password for the same user - so the person is asked once.
     */
    suspend fun needsPassword(rawPayload: String): Boolean {
        val dto = (parser.parseDetailed(rawPayload) as? BroadcastDescriptorParser.ParseOutcome.Valid)?.dto
        val auth = dto?.auth?.takeIf { it.isPasswordV1 }
        if (auth == null || dto.credentials != null) return false
        val known = dto.sourceId?.trim()?.takeIf { it.isNotEmpty() }?.let { repository.getBySourceDeviceId(it) }
            ?: repository.getByUrl(dto.url.trim())
        val kept = known?.let { passwordStore.get(it.id) }
        return kept == null || kept.user != auth.user || kept.password == null
    }

    /**
     * Replaces the password of an already imported protected stream after the broadcast refused the stored one or
     * had none. False when the row has no `auth` user on record or the secret store refused the write.
     */
    suspend fun replacePassword(streamId: String, password: String): Boolean {
        val user = passwordStore.get(streamId)?.user ?: return false
        return passwordStore.put(streamId, user, password)
    }

    /**
     * S4247, LIVE-BROADCAST item S: a descriptor of a broadcast that has not started yet keeps a waiting row until
     * its `until`; one past it stores nothing. A live descriptor of the same row ends its wait.
     */
    private suspend fun resolveImport(dto: BroadcastDescriptorDto, password: String?): ImportResult {
        val untilMs = dto.pending?.let { it.untilMs() ?: EXPIRED }
        if (untilMs != null && untilMs <= System.currentTimeMillis()) return ImportResult.Expired
        val result = keepRow(dto, password)
        val streamId = if (result in KEPT_ROW) rowIdOf(dto) else null
        return when {
            streamId == null -> result
            untilMs == null -> result.also { pendingStore.clear(streamId) }
            else -> ImportResult.Waiting(streamId, untilMs).also { pendingStore.put(streamId, untilMs) }
        }
    }

    private suspend fun rowIdOf(dto: BroadcastDescriptorDto): String? =
        dto.sourceId?.trim()?.takeIf { it.isNotEmpty() }?.let { repository.getBySourceDeviceId(it)?.id }
            ?: repository.getByUrl(dto.url.trim())?.id

    private suspend fun keepRow(dto: BroadcastDescriptorDto, password: String?): ImportResult {
        val offered = dto.title?.trim()?.takeIf { it.isNotEmpty() }
        val deviceId = dto.sourceId?.trim()?.takeIf { it.isNotEmpty() }
        // S4160: the whole list is kept, so a broadcast reachable through an exchange server plays outside the
        // broadcaster's LAN; a descriptor with no explicit list leaves the column null and plays its url.
        val endpoints = BroadcastEndpointCodec.encode(dto.endpoints)
        val known = deviceId?.let { repository.getBySourceDeviceId(it) }
            ?: endpoints?.let { repository.getByUrl(dto.url.trim()) }
        val kind = kindOf(dto.mode, dto.url)
        val result = if (known == null) {
            addNew(dto.url, offered ?: FALLBACK_TITLE, sourceDeviceId = deviceId, endpoints = endpoints, kind = kind)
        } else {
            refresh(known, Offer(dto.url, offered, endpoints), kind)
        }
        if (result in KEPT_ROW) rememberPair(dto, known?.id, password)
        return result
    }

    /**
     * LIVE-BROADCAST item M: the row of a protected descriptor keeps its pair in the secret store - the user from
     * `auth` and the password typed once. A new link user (a renewed broadcast) drops a password typed for the old one.
     */
    private suspend fun rememberPair(dto: BroadcastDescriptorDto, knownId: String?, typed: String?) {
        val auth = dto.auth?.takeIf { it.isPasswordV1 } ?: return
        val streamId = knownId ?: repository.getByUrl(dto.url.trim())?.id ?: return
        // Item BF: an own broadcast's record carries the account pair, which answers with no question.
        val own = dto.credentials
        val user = own?.user ?: auth.user
        val kept = passwordStore.get(streamId)?.takeIf { it.user == user }?.password
        if (!passwordStore.put(streamId, user, typed ?: own?.password ?: kept)) {
            Timber.w("ImportStreamBroadcastUseCase: the secret store refused the broadcast password")
        }
    }

    private class Offer(val url: String, val title: String?, val endpoints: String?)

    /**
     * S4289: the descriptor's `mode` states what the stream carries, so it decides the row's kind - the url of a
     * relay or LAN endpoint has no extension the url classifier could read. RTSP stays by scheme because the
     * player chooses its source from it. Null for a mode this app does not know: a new row then falls back to the
     * url classification and a known row keeps the kind it has.
     */
    private fun kindOf(mode: String, url: String): String? = when (mode.trim().uppercase()) {
        BroadcastMode.AUDIO_ONLY.name -> KIND_AUDIO
        BroadcastMode.VIDEO_AUDIO.name, BroadcastMode.VIDEO_ONLY.name ->
            if (url.trim().startsWith(RTSP_SCHEME, ignoreCase = true)) KIND_RTSP else KIND_VIDEO
        else -> null
    }

    /**
     * S2813: one broadcasting device owns one row, so a device whose address moved refreshes that row
     * instead of adding a second one. Without it the entry the user had already pinned and launched
     * from keeps pointing at the previous session's dead address, which is the reported defect.
     *
     * S2868: the same visit may also carry a better name. A row created before the watch named itself
     * through the node still reads its model code, so it is renamed - but only while its title is one
     * this code generated; a title the user typed is never overwritten.
     *
     * S4160: a source's next broadcast carries a new `broadcastId`, so its endpoint list replaces the
     * stored one silently - the descriptor is the grant, and one hand-over reaches one broadcast (item AF).
     *
     * S4289: the kind follows the descriptor's mode in both directions, and a type typed in the edit dialog is
     * overwritten - the broadcaster states what it sends, and a stale kind plays video as inline audio. It goes
     * through the edit dialog's own write, after the address move so the durable user state is already carried.
     */
    private suspend fun refresh(known: StreamSourceEntity, offer: Offer, offeredKind: String?): ImportResult {
        val trimmed = offer.url.trim()
        val moved = trimmed != known.url
        val renamed = offer.title != null && offer.title != known.title && isGeneratedTitle(known.title)
        val rerouted = offer.endpoints != known.broadcastEndpoints
        val rekind = offeredKind?.takeIf { it != known.mediaKind }
        if (moved) repository.refreshSourceAddress(id = known.id, url = trimmed)
        if (renamed) repository.renameSource(id = known.id, title = requireNotNull(offer.title))
        if (rerouted) repository.refreshBroadcastEndpoints(id = known.id, endpoints = offer.endpoints)
        if (rekind != null) {
            repository.updateUserFields(
                id = known.id,
                url = trimmed,
                title = offer.title.takeIf { renamed } ?: known.title,
                mediaKind = rekind,
            )
        }
        val changed = moved || renamed || rerouted
        return if (changed || rekind != null) ImportResult.Updated else ImportResult.Duplicate
    }

    private fun isGeneratedTitle(title: String): Boolean {
        val stored = title.trim()
        return stored.isEmpty() || stored == FALLBACK_TITLE || MODEL_CODE.matches(stored)
    }

    private suspend fun addNew(
        url: String,
        title: String,
        sourceDeviceId: String?,
        endpoints: String?,
        kind: String?,
    ): ImportResult =
        when (
            addStreamSourceUseCase(
                url = url,
                title = title,
                sourceDeviceId = sourceDeviceId,
                broadcastEndpoints = endpoints,
                mediaKind = kind,
            )
        ) {
            AddResult.Success -> ImportResult.Success
            AddResult.Duplicate -> ImportResult.Duplicate
            AddResult.InvalidUrl -> ImportResult.InvalidUrl
        }

    sealed interface ImportResult {
        data object Success : ImportResult

        /** S2813: a known device came back on a new address; its existing entry now points at it. */
        data object Updated : ImportResult
        data object Duplicate : ImportResult
        data object InvalidUrl : ImportResult
        data object InvalidDescriptor : ImportResult
        data object UnsupportedVersion : ImportResult

        /** S4247: the row is kept and waits for the broadcast to start until [untilMs]. */
        data class Waiting(val streamId: String, val untilMs: Long) : ImportResult

        /** S4247: the link was made for a broadcast that never started before its `until`. */
        data object Expired : ImportResult
    }

    private companion object {
        const val FALLBACK_TITLE = "Audio Broadcast"
        const val KIND_AUDIO = "AUDIO"
        const val KIND_VIDEO = "VIDEO"
        const val KIND_RTSP = "RTSP"
        const val RTSP_SCHEME = "rtsp://"

        // A pending marker whose `until` is not an instant cannot be waited for, so it reads as expired.
        const val EXPIRED = 0L
        val KEPT_ROW = setOf(ImportResult.Success, ImportResult.Updated, ImportResult.Duplicate)

        // A manufacturer model code as Build.MODEL reports it (SM-L310, SMR870): one token of capitals
        // and digits with at least one digit. A human name has a space or lowercase letters.
        val MODEL_CODE = Regex("^(?=.*\\d)[A-Z][A-Z0-9]*(-[A-Z0-9]+)*$")
    }
}
