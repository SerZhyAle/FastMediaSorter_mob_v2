package com.sza.fastmediasorter.ui.streams.helpers

import android.graphics.Bitmap
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.streams.FaviconAtlasStore
import com.sza.fastmediasorter.domain.usecase.streams.ObserveStreamSourcesUseCase
import com.sza.fastmediasorter.ui.streams.FaviconAtlasSlicer
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * S1916 - turns the channel identity the picker hands back into everything a home-screen tile needs.
 *
 * It exists because the configuration activity may not hold these dependencies itself: a domain use case
 * or a repository injected straight into an Activity is exactly what CLAUDE.md Rule 3 forbids, and the
 * `activity-logic` gate refuses it. The activity keeps the Android plumbing - widget id, result code,
 * fragment - and delegates every lookup here.
 */
class StreamWidgetResolveManager @Inject constructor(
    private val observeStreams: ObserveStreamSourcesUseCase,
    private val faviconAtlasStore: FaviconAtlasStore,
) {

    private val faviconSlicer = FaviconAtlasSlicer { faviconAtlasStore.atlasFile() }

    /** The channel plus its tile bitmap, or null when the identity no longer names a catalog row. */
    data class ResolvedChannel(
        val source: StreamSourceEntity,
        val icon: Bitmap?,
    )

    suspend fun resolve(identityKey: String): ResolvedChannel? {
        val source = sourceFor(identityKey) ?: return null
        return ResolvedChannel(source = source, icon = iconFor(source))
    }

    private suspend fun sourceFor(identityKey: String): StreamSourceEntity? =
        observeStreams().first().firstOrNull { it.identityKey == identityKey }

    /**
     * A missing favicon is normal - the atlas is built by catalog import and may not have run - so the
     * tile falls back to the generic cast glyph rather than failing the whole configuration.
     */
    private suspend fun iconFor(source: StreamSourceEntity): Bitmap? {
        val index = faviconAtlasStore.coords()[source.url] ?: return null
        return faviconSlicer.tileFor(index)
    }
}
