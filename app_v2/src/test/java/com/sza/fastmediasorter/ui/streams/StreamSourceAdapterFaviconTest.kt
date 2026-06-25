package com.sza.fastmediasorter.ui.streams

import android.graphics.Bitmap
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S0668 / PHASE_05: the adapter favicon bind logic. A fake resolver + tile loader (no real atlas)
 * keeps the test deterministic; an unconfined scope makes the async tile load resolve inline within
 * bind(). Robolectric inflates the real row binding.
 *
 * Cases: (a) a null-index url leaves ivFavicon GONE (empty slot); (b) a present index + a 32 px tile
 * shows ivFavicon VISIBLE with a drawable; (c) a rebind to a null-favicon url after a present one
 * returns to GONE (no stale tile).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamSourceAdapterFaviconTest {

    // The row layout resolves ?attr/ theme references, so inflation needs a Material-themed context.
    private val themedContext = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_FastMediaSorter)
    private val parent = FrameLayout(themedContext)
    // Unconfined so the favicon launch runs eagerly and the tile loader (already-ready bitmap) applies
    // synchronously within bind() - no looper pumping needed.
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun entity(url: String) = StreamSourceEntity(
        id = url, url = url, title = "T", mediaKind = "VIDEO",
        sourceOrigin = "CATALOG", sortIndex = 0, addedAt = 0L,
    )

    private fun adapter(
        resolver: (String) -> Int?,
        tile: suspend (Int) -> Bitmap?,
    ) = StreamSourceAdapter(
        onPlay = {}, onPin = {}, onRemove = {}, onAddShortcut = {}, onEdit = {}, onShareLink = {},
        faviconResolver = resolver,
        faviconTileLoader = tile,
        faviconScope = scope,
    )

    private fun bindAt(adapter: StreamSourceAdapter, items: List<StreamSourceEntity>, position: Int): StreamSourceAdapter.VH {
        // First submit from an empty list dispatches synchronously (AsyncListDiffer fast path).
        adapter.submitList(items)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, position)
        return holder
    }

    private fun faviconView(holder: StreamSourceAdapter.VH): View =
        holder.itemView.findViewById(R.id.ivFavicon)

    @Test
    fun `null index leaves favicon gone`() {
        val adapter = adapter(resolver = { null }, tile = { error("must not load for a null index") })
        val holder = bindAt(adapter, listOf(entity("u1")), 0)
        assertEquals(View.GONE, faviconView(holder).visibility)
    }

    @Test
    fun `present index shows a visible tile`() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val adapter = adapter(resolver = { 0 }, tile = { bitmap })
        val holder = bindAt(adapter, listOf(entity("u1")), 0)

        val iv = faviconView(holder) as android.widget.ImageView
        assertEquals(View.VISIBLE, iv.visibility)
        assertNotNull("a tile bitmap must be set", iv.drawable)
    }

    @Test
    fun `rebind to a null-favicon url returns to gone (no stale tile)`() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        // u1 has a favicon, u2 does not.
        val adapter = adapter(
            resolver = { url -> if (url == "u1") 0 else null },
            tile = { bitmap },
        )
        val items = listOf(entity("u1"), entity("u2"))
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.submitList(items)

        // Bind the favicon row first.
        adapter.onBindViewHolder(holder, 0)
        assertEquals(View.VISIBLE, faviconView(holder).visibility)

        // Recycle + rebind the SAME holder to the favicon-less row.
        adapter.onViewRecycled(holder)
        adapter.onBindViewHolder(holder, 1)
        assertEquals("a recycled holder must not keep the previous row's tile", View.GONE, faviconView(holder).visibility)
        assertNull((faviconView(holder) as android.widget.ImageView).drawable)
    }
}
