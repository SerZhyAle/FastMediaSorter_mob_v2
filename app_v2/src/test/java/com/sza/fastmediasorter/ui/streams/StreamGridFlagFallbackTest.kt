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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S2650: the grid tile's terminal artwork tier. The tile used to end at the media-kind glyph, so every
 * channel the atlases do not cover rendered identically; the country flag is the same last resort the
 * list row has carried since S0785.
 *
 * Mirrors [StreamSourceAdapterFaviconTest]'s setup: fake loaders instead of a real atlas, an unconfined
 * scope so the async tiers resolve inline within bind(), Robolectric inflating the real cell binding.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamGridFlagFallbackTest {

    private val themedContext = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_FastMediaSorter)
    private val parent = FrameLayout(themedContext)
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    // AUDIO keeps the tile off the capture path, so the chain under test is logo -> favicon -> flag.
    private fun entity(url: String, country: String? = null) = StreamSourceEntity(
        id = url,
        url = url,
        title = "T",
        mediaKind = "AUDIO",
        sourceOrigin = "CATALOG",
        sortIndex = 0,
        addedAt = 0L,
        country = country,
    )

    private fun adapter(
        faviconIndex: (String) -> Int? = { null },
        favicon: suspend (Int) -> Bitmap? = { null },
        logo: suspend (String) -> Bitmap? = { null },
    ) = StreamGridAdapter(
        onPlay = {}, onPin = {}, onRemove = {}, onAddShortcut = {}, onEdit = {}, onShareLink = {},
        frameProvider = { null },
        requestCapture = {},
        faviconResolver = faviconIndex,
        faviconTileLoader = favicon,
        logoTileLoader = logo,
        faviconScope = scope,
    )

    private fun bindAt(
        adapter: StreamGridAdapter,
        items: List<StreamSourceEntity>,
        position: Int,
    ): StreamGridAdapter.VH {
        adapter.submitList(items)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, position)
        return holder
    }

    private fun flagView(holder: StreamGridAdapter.VH): View = holder.itemView.findViewById(R.id.tvTileFlag)

    @Test
    fun `no artwork but a country shows the flag instead of the kind glyph`() {
        val holder = bindAt(adapter(), listOf(entity("u1", country = "UA")), 0)
        assertEquals(View.VISIBLE, flagView(holder).visibility)
    }

    @Test
    fun `no artwork and no country keeps the kind glyph`() {
        val holder = bindAt(adapter(), listOf(entity("u1")), 0)
        assertEquals(View.GONE, flagView(holder).visibility)
    }

    @Test
    fun `a blank country string is treated as no country`() {
        val holder = bindAt(adapter(), listOf(entity("u1", country = "   ")), 0)
        assertEquals(View.GONE, flagView(holder).visibility)
    }

    @Test
    fun `a country code that maps to no flag keeps the kind glyph`() {
        val holder = bindAt(adapter(), listOf(entity("u1", country = "123")), 0)
        assertEquals(View.GONE, flagView(holder).visibility)
    }

    @Test
    fun `a resolved logo hides the flag`() {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val holder = bindAt(adapter(logo = { bitmap }), listOf(entity("u1", country = "UA")), 0)
        assertEquals(View.GONE, flagView(holder).visibility)
    }

    @Test
    fun `a resolved favicon hides the flag`() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val adapter = adapter(faviconIndex = { 0 }, favicon = { bitmap })
        val holder = bindAt(adapter, listOf(entity("u1", country = "UA")), 0)
        assertEquals(View.GONE, flagView(holder).visibility)
    }

    @Test
    fun `a recycled holder does not keep the previous tile's flag`() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val adapter = adapter(
            faviconIndex = { url -> if (url == "u2") 0 else null },
            favicon = { bitmap },
        )
        val items = listOf(entity("u1", country = "UA"), entity("u2", country = "UA"))
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.submitList(items)

        adapter.onBindViewHolder(holder, 0)
        assertEquals(View.VISIBLE, flagView(holder).visibility)

        adapter.onBindViewHolder(holder, 1)
        assertEquals("a rebound tile must not keep the previous row's flag", View.GONE, flagView(holder).visibility)
    }
}
