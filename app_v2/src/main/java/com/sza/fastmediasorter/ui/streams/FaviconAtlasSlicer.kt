package com.sza.fastmediasorter.ui.streams

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * S0668 / PHASE_04: turns `(atlasFile, faviconIndex)` into a 32 px tile [Bitmap] using the fixed-grid
 * contract shared with the offline packer (see PHASE_01).
 *
 * S1821: the decoded sheet and the extracted tiles live in [SharedFaviconAtlas], a process-wide cache,
 * not in this instance. Five owners construct a slicer of their own (Streams, the main panel, Browse,
 * now-playing, and the launcher label use case, which built one PER CALL), and per-instance state gave
 * each of them a private copy of the same sheet: the published atlas is 512x11488, which is 23,5 MB
 * decoded, so four live screens cost 94 MB and one launcher screen with N stream shortcuts paid N full
 * decodes of an 11 488 px PNG to crop one 4 KB tile. Sharing the state fixes both without touching a
 * single call site.
 *
 * The sheet is released after [SharedFaviconAtlas.SHEET_IDLE_RELEASE_MS] of no tile requests, while the
 * extracted tiles stay in a small byte-bounded cache. That is what keeps "one shared copy" from turning
 * into "one copy alive for the whole process": a scroll burst decodes once and then pays nothing, and an
 * idle app holds kilobytes of tiles instead of megabytes of sheet.
 *
 * The [atlasFileProvider] is re-read on every request, so a catalog import that replaced the file is
 * picked up by the file-identity key even without an explicit [invalidate].
 */
class FaviconAtlasSlicer(
    private val atlasFileProvider: () -> File?
) {

    /**
     * The pixel rect of tile [index] in the fixed grid. Pure (the single source of truth for the
     * index->rect math, mirrored by the offline packer in PHASE_01/PHASE_02). `col = index % COLS`,
     * `row = index / COLS`.
     */
    fun rectFor(index: Int): Rect = rectOf(index)

    /**
     * True only when [index] is non-negative and its tile rect fits inside an `atlasWidth x
     * atlasHeight` atlas. A stale/oversized index (e.g. an old sidecar pointing past a shrunk atlas)
     * is out of bounds -> no thumbnail, never a crash.
     */
    fun isInBounds(index: Int, atlasWidth: Int, atlasHeight: Int): Boolean =
        inBoundsOf(index, atlasWidth, atlasHeight)

    /**
     * The 32 px tile bitmap for [index], or null when the atlas is absent or the index is out of
     * bounds. Runs off the main thread. The returned bitmap is the cached instance and is never
     * recycled by this class, so a caller must not recycle it either.
     */
    suspend fun tileFor(index: Int): Bitmap? = withContext(Dispatchers.IO) {
        SharedFaviconAtlas.tileFor(atlasFileProvider, index)
    }

    /** Drops the shared sheet and every cached tile (post-import refresh). */
    suspend fun invalidate() = SharedFaviconAtlas.invalidate()

    companion object {
        // S0668: app-side half of the PHASE_01/PHASE_02 sprite-atlas contract. The offline packer must
        // emit indices for the SAME 32 px tile / 16-column grid, or the rects drift.
        const val TILE = 32
        const val COLS = 16

        internal fun rectOf(index: Int): Rect {
            val col = index % COLS
            val row = index / COLS
            val left = col * TILE
            val top = row * TILE
            return Rect(left, top, left + TILE, top + TILE)
        }

        internal fun inBoundsOf(index: Int, atlasWidth: Int, atlasHeight: Int): Boolean {
            val rect = rectOf(index)
            return index >= 0 && rect.right <= atlasWidth && rect.bottom <= atlasHeight
        }
    }
}

/**
 * S1821: the one decoded atlas in the process, plus the tiles cut from it.
 *
 * Every mutation and every read of the sheet happens under [mutex], including the crop. Holding the
 * lock across a 32x32 crop costs microseconds and removes the S1220 race outright, instead of catching
 * the recycled-source exception after the fact: nothing can recycle the sheet while a crop is running.
 */
private object SharedFaviconAtlas {

    /** Roughly 256 tiles at 4 KB each - more than one screen of channels, still under a megabyte. */
    private const val TILE_CACHE_BYTES = 1024 * 1024

    /** How long the decoded sheet survives with no tile requests before it is released. */
    const val SHEET_IDLE_RELEASE_MS = 5_000L

    private val mutex = Mutex()

    // Owns nothing but the release timer. Not GlobalScope (CLAUDE.md Rule 19) and not a caller's scope:
    // the sheet outlives any single screen, so the timer must not die with the screen that started it.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val tiles = object : LruCache<String, Bitmap>(TILE_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private var sheet: Bitmap? = null
    private var sheetKey: String? = null
    private var releaseJob: Job? = null

    suspend fun tileFor(atlasFileProvider: () -> File?, index: Int): Bitmap? = mutex.withLock {
        val file = atlasFileProvider()?.takeIf { it.isFile }
        when {
            index < 0 || file == null -> null
            else -> {
                val key = identityOf(file)
                tiles.get(tileKey(key, index)) ?: cutTile(file, key, index)
            }
        }
    }

    suspend fun invalidate() = mutex.withLock {
        releaseJob?.cancel()
        releaseJob = null
        releaseSheet()
        // Tiles handed out are live in ImageViews, so they are dropped, never recycled.
        tiles.evictAll()
    }

    private fun cutTile(file: File, identity: String, index: Int): Bitmap? {
        val atlas = sheetFor(file, identity)
        val tile = when {
            atlas == null || !inBoundsOfAtlas(atlas, index) -> null
            else -> cropOrNull(atlas, index)
        }
        if (tile != null) {
            tiles.put(tileKey(identity, index), tile)
        }
        scheduleSheetRelease()
        return tile
    }

    private fun cropOrNull(atlas: Bitmap, index: Int): Bitmap? {
        val rect = FaviconAtlasSlicer.rectOf(index)
        return try {
            // createBitmap copies the sub-region, so cropping does not mutate the cached sheet.
            Bitmap.createBitmap(atlas, rect.left, rect.top, FaviconAtlasSlicer.TILE, FaviconAtlasSlicer.TILE)
        } catch (e: IllegalArgumentException) {
            // A rect outside a re-downloaded, smaller sheet yields no tile rather than a crash.
            Timber.i(e, "Favicon atlas region rejected for index=%d", index)
            null
        }
    }

    private fun sheetFor(file: File, identity: String): Bitmap? {
        val cached = sheet
        return if (cached != null && !cached.isRecycled && sheetKey == identity) {
            cached
        } else {
            releaseSheet()
            sheetKey = identity
            sheet = BitmapFactory.decodeFile(file.path)
            sheet
        }
    }

    private fun scheduleSheetRelease() {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            delay(SHEET_IDLE_RELEASE_MS)
            mutex.withLock { releaseSheet() }
        }
    }

    private fun releaseSheet() {
        sheet?.recycle()
        sheet = null
        sheetKey = null
    }

    private fun inBoundsOfAtlas(atlas: Bitmap, index: Int): Boolean =
        FaviconAtlasSlicer.inBoundsOf(index, atlas.width, atlas.height)

    /** File identity, so a re-imported atlas at the same path invalidates itself without a call. */
    private fun identityOf(file: File): String = "${file.path}|${file.lastModified()}|${file.length()}"

    private fun tileKey(identity: String, index: Int): String = "$identity#$index"
}
