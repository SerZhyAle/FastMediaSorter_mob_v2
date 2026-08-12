package com.sza.fastmediasorter.data.map

import android.content.Context
import android.os.SystemClock
import com.sza.fastmediasorter.domain.map.MapTileProvider
import com.sza.fastmediasorter.domain.model.map.MapPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.tan

/**
 * S1175: one static OpenStreetMap tile per cell, cached on disk.
 *
 * The disk cache is not an optimisation here - the OSM tile usage policy requires a minimum seven-day
 * cache and blocks by application, so a re-download on every redraw would get the whole app blocked,
 * not just this gadget (research 02 §3). Retention is therefore explicit and age-based rather than
 * delegated to Glide's LRU, whose eviction is not tied to age.
 *
 * Two ages, deliberately not one: [RETENTION_MS] says when a tile should be *re-downloaded*, while a
 * tile is only *deleted* once a newer one has arrived or the count budget forces it out. Collapsing
 * them deletes the offline fallback at exactly the moment it is needed - an expired tile plus no
 * network is precisely the case where the last picture is all the cell has.
 *
 * No prefetching: only the tile under the current position is ever requested.
 */
@Singleton
class OsmMapTileProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named(MAP_TILE_CLIENT) private val client: OkHttpClient,
) : MapTileProvider {

    /**
     * Set when the source answers 429 - the code the usage policy uses to say "slow down". Retrying
     * through a throttle is what turns it into a ban, and the ban is by application.
     *
     * Measured on [SystemClock.elapsedRealtime] rather than wall clock: a backwards NTP or manual
     * date correction would otherwise hold the cooldown for the whole delta.
     */
    @Volatile
    private var blockedUntilUptimeMs: Long = 0L

    override val attribution: String = "© OpenStreetMap contributors"

    override suspend fun tileFor(point: MapPoint, zoom: Int): String? = withContext(Dispatchers.IO) {
        val x = tileX(point.longitude, zoom)
        val y = tileY(point.latitude, zoom)
        val cached = File(cacheDir(), "tile_${zoom}_${x}_$y.png")
        when {
            cached.isFile && isWithinRetention(cached, System.currentTimeMillis()) -> cached.absolutePath
            isCoolingDown() -> reusable(cached)
            else -> download(zoom, x, y, cached)?.also { evict(keeping = cached) } ?: reusable(cached)
        }
    }

    /** An expired tile still shows the right streets, so it beats an empty cell while offline. */
    private fun reusable(cached: File): String? = cached.takeIf(File::isFile)?.absolutePath

    /**
     * Downloads into a private temp file and renames on success. Writing straight to the cache path
     * would truncate a usable tile before the first byte arrived, and a mid-transfer failure would
     * then leave a corrupt file whose timestamp is fresh - unusable, yet trusted for seven days.
     *
     * The temp name is unique per call rather than derived from the target: two callers downloading
     * the same tile would otherwise share one temp path and interleave their bytes. Nothing does that
     * today - the repository serialises every call - but this class cannot see that invariant.
     */
    private fun download(zoom: Int, x: Int, y: Int, target: File): String? {
        val request = Request.Builder()
            .url("$TILE_HOST/$zoom/$x/$y.png")
            .build()
        val directory = cacheDir().also { it.mkdirs() }
        var temp: File? = null
        return runCatching {
            temp = File.createTempFile(TEMP_PREFIX, TEMP_SUFFIX, directory)
            client.newCall(request).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) {
                    if (response.code == THROTTLED) startCooldown()
                    Timber.w("MapTiles: tile request failed with code %d", response.code)
                    return@use null
                }
                requireNotNull(temp) { "MapTiles: temp file lost between creation and write" }
                    .also { file -> file.outputStream().use { body.byteStream().copyTo(it) } }
                    .takeIf { it.renameTo(target) }
                    ?.let { target.absolutePath }
            }
        }.onFailure { error ->
            Timber.w(error, "MapTiles: tile download failed")
        }.also {
            temp?.delete()
        }.getOrNull()
    }

    private fun isCoolingDown(): Boolean = SystemClock.elapsedRealtime() < blockedUntilUptimeMs

    private fun startCooldown() {
        blockedUntilUptimeMs = SystemClock.elapsedRealtime() + COOLDOWN_MS
        Timber.w("MapTiles: source throttled us, pausing tile requests")
    }

    /**
     * Runs only after a tile has actually arrived, so the cache can never be emptied by a failing
     * network. [keeping] is the tile this call just published: it is the newest file and would
     * survive on age alone, but naming it makes that independent of how the budget is computed.
     *
     * Nothing else ever deletes a tile, and the file name is per position, so without this the
     * directory grows with distance travelled rather than with time - a road trip alone writes
     * hundreds of files that would otherwise be immortal (the defect S1294 recorded for
     * `UnifiedFileCache`).
     */
    private fun evict(keeping: File) {
        val now = System.currentTimeMillis()
        val candidates = cacheDir().listFiles().orEmpty().filterNot { it == keeping }
        val expired = candidates.filterNot { isWithinRetention(it, now) }
        val surplus = (candidates.size - expired.size + 1 - MAX_CACHED_TILES).coerceAtLeast(0)
        val oldestKept = candidates.filter { isWithinRetention(it, now) }
            .sortedBy(File::lastModified)
            .take(surplus)
        (expired + oldestKept).forEach { file ->
            if (!file.delete()) Timber.v("MapTiles: could not evict %s", file.name)
        }
    }

    private fun cacheDir(): File = File(context.cacheDir, CACHE_DIR_NAME)

    private fun isWithinRetention(file: File, nowMs: Long): Boolean =
        nowMs - file.lastModified() < RETENTION_MS

    /** Web Mercator, the tile addressing every OSM-compatible source shares. */
    private fun tileX(longitude: Double, zoom: Int): Int =
        clamp((longitude + HALF_TURN_DEGREES) / FULL_TURN_DEGREES * tileCount(zoom), zoom)

    private fun tileY(latitude: Double, zoom: Int): Int {
        val radians = latitude * PI / HALF_TURN_DEGREES
        val projected = asinh(tan(radians)) / PI
        return clamp((1.0 - projected) / 2.0 * tileCount(zoom), zoom)
    }

    /** The antimeridian and the poles land exactly on the edge, where the index is one past the last. */
    private fun clamp(raw: Double, zoom: Int): Int =
        floor(raw).toInt().coerceIn(0, tileCount(zoom).toInt() - 1)

    private fun tileCount(zoom: Int): Double = 2.0.pow(zoom)

    companion object {
        /** Wide enough to show the surrounding streets, narrow enough to stay one tile. */
        const val DEFAULT_ZOOM = 15

        private const val TILE_HOST = "https://tile.openstreetmap.org"
        private const val CACHE_DIR_NAME = "map_tiles"
        private const val TEMP_PREFIX = "tile"
        private const val TEMP_SUFFIX = ".part"
        private const val RETENTION_DAYS = 7L
        private const val COOLDOWN_HOURS = 6L
        private val RETENTION_MS = TimeUnit.DAYS.toMillis(RETENTION_DAYS)
        private val COOLDOWN_MS = TimeUnit.HOURS.toMillis(COOLDOWN_HOURS)

        /**
         * Only 429. A 403 is a false-positive magnet - captive portals, corporate proxies and some
         * ISPs answer it to anything - and a cooldown cannot be cleared by a success, because it
         * issues no request while it holds, so one hotel Wi-Fi join would cost six hours of no tiles.
         */
        private const val THROTTLED = 429

        /** One cell shows one tile; this many covers a long trip without unbounded growth. */
        private const val MAX_CACHED_TILES = 128
        private const val HALF_TURN_DEGREES = 180.0
        private const val FULL_TURN_DEGREES = 360.0
    }
}
