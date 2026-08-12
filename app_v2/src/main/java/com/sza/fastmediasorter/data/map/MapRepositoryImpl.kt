package com.sza.fastmediasorter.data.map

import android.content.Context
import android.content.SharedPreferences
import com.sza.fastmediasorter.domain.location.DeviceLocationSource
import com.sza.fastmediasorter.domain.map.MapPlaceLabelProvider
import com.sza.fastmediasorter.domain.map.MapTileProvider
import com.sza.fastmediasorter.domain.model.map.MapPoint
import com.sza.fastmediasorter.domain.model.map.MapSnapshot
import com.sza.fastmediasorter.domain.repository.MapRepository
import com.sza.fastmediasorter.domain.repository.MapResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1175: decides what the map cell shows, including when there is no permission, no fix, or no network.
 *
 * The degradation order mirrors `WeatherRepositoryImpl` (S0426) because strategic §5.2 asks for the
 * same promise: a source failure must not blank what is already on screen. The disk mirror is part of
 * that promise rather than an optimisation - without it, the first read after the launcher process is
 * killed has nothing to fall back on and the cell comes up empty, which §11.4 forbids.
 */
@Singleton
class MapRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationSource: DeviceLocationSource,
    private val tileProvider: MapTileProvider,
    private val labelProvider: MapPlaceLabelProvider,
) : MapRepository {

    // Lazy, not eager: the whole graph is built on the main thread during desktop bind, and opening
    // SharedPreferences there is a StrictMode disk read (the S1153 fix recorded in UnifiedFileCache).
    // Every use below is already on Dispatchers.IO.
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Read and written only under [mutex]; the lock's happens-before is what makes a plain var safe.
    private var cached: MapSnapshot? = null

    // Two map cells on one desktop wake on the same tick; without this they would each fetch a tile.
    private val mutex = Mutex()

    /**
     * The address lookup deliberately sits outside the lock. It is the one leg with no timeout the app
     * controls - the platform geocoder is a synchronous binder call into an OEM service, and a
     * `withTimeout` around it would not interrupt it, only mark the coroutine cancelled once it
     * finally returned. Holding the shared mutex across that would stall every other map cell for as
     * long as the OEM service hangs. The caption is best-effort anyway: the snapshot is complete with
     * coordinates, and the address only replaces them if it arrives.
     */
    override suspend fun current(): MapResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val result = mutex.withLock { currentLocked(now) }
        addressed(result, now)
    }

    private suspend fun currentLocked(now: Long): MapResult {
        val previous = (cached ?: readFromDisk())?.takeIf { File(it.imagePath).isFile }
        cached = previous
        val fresh = MapCachePolicy.freshOrNull(previous, now)
        return when {
            !locationSource.hasPermission() -> MapResult.PermissionMissing(previous)
            fresh != null -> MapResult.Fresh(fresh)
            else -> refreshed(previous, now)
        }
    }

    private suspend fun refreshed(previous: MapSnapshot?, now: Long): MapResult {
        val fetched = fetch(now)
        if (fetched != null) {
            cached = fetched
            writeToDisk(fetched)
        }
        return MapCachePolicy.resultFor(previous, fetched, now)
    }

    /**
     * A stale tile file is reused when the download fails but the point is unchanged: the picture of
     * where the user still is stays true for far longer than the tile cache's own retention.
     */
    private suspend fun fetch(now: Long): MapSnapshot? {
        val point = locationSource.lastKnownPoint()
        val imagePath = point?.let {
            tileProvider.tileFor(it, OsmMapTileProvider.DEFAULT_ZOOM) ?: reusableTileFor(it)
        }
        return if (point == null || imagePath == null) {
            null
        } else {
            MapSnapshot(
                point = point,
                label = point.formatted(),
                imagePath = imagePath,
                attribution = tileProvider.attribution,
                capturedAt = now,
            )
        }
    }

    /**
     * Fills in the address off the lock, and only for a snapshot this very call just fetched.
     *
     * The `capturedAt == now` test is the rate limit. Geocoding on every read would fire on every
     * return to the home screen - a fresh cache short-circuits the tile work but not this - and on a
     * device whose geocoder answers nothing it would never stop, one unbounded binder call per visit
     * per cell, now concurrent because they no longer queue behind the mutex. Tied to a fetch, it can
     * happen at most once per cache window.
     *
     * It also never runs for [MapResult.PermissionMissing]: revoking the location grant means stop
     * using my position, and resolving the stored coordinates anyway would answer the opposite.
     */
    private suspend fun addressed(result: MapResult, now: Long): MapResult {
        val snapshot = (result as? MapResult.Fresh)?.snapshot
            ?.takeIf { it.capturedAt == now && it.label == it.point.formatted() }
        val address = snapshot?.let { labelProvider.labelFor(it.point) }
        return if (snapshot == null || address == null) {
            result
        } else {
            captioned(result, snapshot.copy(label = address), snapshot.capturedAt)
        }
    }

    /**
     * Only if the world has not moved on: a fetch that landed while the geocoder was thinking owns
     * the cache, and its eviction may already have taken this snapshot's tile with it.
     */
    private suspend fun captioned(
        result: MapResult,
        updated: MapSnapshot,
        expectedCapturedAt: Long,
    ): MapResult = mutex.withLock {
        val stillOurs = cached?.capturedAt == expectedCapturedAt && File(updated.imagePath).isFile
        if (stillOurs) {
            cached = updated
            writeToDisk(updated)
            MapResult.Fresh(updated)
        } else {
            result
        }
    }

    private fun reusableTileFor(point: MapPoint): String? = cached
        ?.takeIf { it.point == point && File(it.imagePath).isFile }
        ?.imagePath

    /** A mirrored snapshot whose tile file is gone is worthless, so it is dropped rather than shown. */
    private fun readFromDisk(): MapSnapshot? {
        val imagePath = prefs.getString(KEY_IMAGE, null) ?: return null
        val attribution = prefs.getString(KEY_ATTRIBUTION, null)
        val capturedAt = prefs.getLong(KEY_TIME, 0L)
        val latitude = readCoordinate(KEY_LATITUDE)
        val longitude = readCoordinate(KEY_LONGITUDE)
        // Attribution is checked like the rest: showing a tile without its credit is a licence breach,
        // not a cosmetic gap, so a mirror missing it is discarded rather than defaulted to blank.
        val incomplete = capturedAt == 0L || latitude.isNaN() || longitude.isNaN() ||
            attribution.isNullOrEmpty()
        return if (incomplete || !File(imagePath).isFile) {
            null
        } else {
            MapSnapshot(
                point = MapPoint(latitude, longitude),
                label = prefs.getString(KEY_LABEL, null).orEmpty(),
                imagePath = imagePath,
                attribution = attribution,
                capturedAt = capturedAt,
            )
        }
    }

    private fun writeToDisk(snapshot: MapSnapshot) {
        prefs.edit()
            .putLong(KEY_TIME, snapshot.capturedAt)
            .putLong(KEY_LATITUDE, snapshot.point.latitude.toRawBits())
            .putLong(KEY_LONGITUDE, snapshot.point.longitude.toRawBits())
            .putString(KEY_LABEL, snapshot.label)
            .putString(KEY_IMAGE, snapshot.imagePath)
            .putString(KEY_ATTRIBUTION, snapshot.attribution)
            .apply()
    }

    /**
     * Stored as raw bits rather than as a float, which the weather mirror can afford and this one
     * cannot: a float round-trip moves a coordinate by about a metre, and that is enough to make the
     * restored point unequal to the next fix, which is what decides whether the cached tile is reused.
     */
    private fun readCoordinate(key: String): Double =
        Double.fromBits(prefs.getLong(key, Double.NaN.toRawBits()))

    internal companion object {
        // Internal so the test asserts against the same keys production writes, instead of its own copy.
        const val PREFS_NAME = "launcher_map_prefs"
        const val KEY_TIME = "captured_at"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_LABEL = "label"
        const val KEY_IMAGE = "image_path"
        const val KEY_ATTRIBUTION = "attribution"
    }
}
