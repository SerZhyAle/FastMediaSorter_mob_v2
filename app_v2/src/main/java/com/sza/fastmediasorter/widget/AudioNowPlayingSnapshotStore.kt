package com.sza.fastmediasorter.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.annotation.VisibleForTesting
import com.sza.fastmediasorter.data.SyncStorageCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lightweight persistent state for the home-screen Audio Now Playing widget.
 *
 * The playback service owns writes. Widget providers only read this snapshot and render it.
 */
object AudioNowPlayingSnapshotStore {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Keeps two writes from reaching the file out of the order their callers issued them in. */
    private val persistLock = Mutex()

    /**
     * S3239: the playback service publishes a snapshot from a thread that runs under a StrictMode
     * policy detecting disk reads, and both [read] and [write] used to open the preferences file
     * there. The last written value lives here instead, so only the very first read touches disk.
     */
    @Volatile
    private var cached: Snapshot? = null

    private const val PREFS = "audio_now_playing_widget"
    private const val KEY_ACTIVE = "active"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_ARTWORK_URI = "artwork_uri"
    private const val KEY_IS_PLAYING = "is_playing"
    private const val KEY_MEDIA_URI = "media_uri"
    private const val KEY_RESOURCE_ID = "resource_id"
    private const val KEY_SIZE = "size"
    private const val KEY_DATE_MODIFIED = "date_modified"
    private const val KEY_IS_FAVORITE = "is_favorite"

    data class Snapshot(
        val active: Boolean = false,
        val title: String = "",
        val artist: String = "",
        val artworkUri: String = "",
        val isPlaying: Boolean = false,
        val mediaUri: String = "",
        val resourceId: Long = -1L,
        val size: Long = 0L,
        val dateModified: Long = 0L,
        val isFavorite: Boolean = false
    ) {
        val canToggleFavorite: Boolean
            get() = active && mediaUri.isNotBlank() && resourceId > 0L
    }

    fun read(context: Context): Snapshot = cached ?: readFromDisk(context).also { cached = it }

    private fun readFromDisk(context: Context): Snapshot {
        val prefs = SyncStorageCompat.getSyncPreferences(context, PREFS)
        return Snapshot(
            active = prefs.getBoolean(KEY_ACTIVE, false),
            title = prefs.getString(KEY_TITLE, "").orEmpty(),
            artist = prefs.getString(KEY_ARTIST, "").orEmpty(),
            artworkUri = prefs.getString(KEY_ARTWORK_URI, "").orEmpty(),
            isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false),
            mediaUri = prefs.getString(KEY_MEDIA_URI, "").orEmpty(),
            resourceId = prefs.getLong(KEY_RESOURCE_ID, -1L),
            size = prefs.getLong(KEY_SIZE, 0L),
            dateModified = prefs.getLong(KEY_DATE_MODIFIED, 0L),
            isFavorite = prefs.getBoolean(KEY_IS_FAVORITE, false)
        )
    }

    /**
     * Publishes [snapshot] at once for every in-process reader and persists it on [Dispatchers.IO],
     * because the callers are the playback service and a widget broadcast, neither of which may block
     * on the preferences file (S3239).
     */
    fun write(context: Context, snapshot: Snapshot) {
        cached = snapshot
        val appContext = context.applicationContext
        ioScope.launch {
            persistLock.withLock { persist(appContext, snapshot) }
            updateWidgets(appContext)
        }
    }

    @VisibleForTesting
    internal fun persist(context: Context, snapshot: Snapshot) {
        SyncStorageCompat.getSyncPreferences(context, PREFS)
            .edit()
            .putBoolean(KEY_ACTIVE, snapshot.active)
            .putString(KEY_TITLE, snapshot.title)
            .putString(KEY_ARTIST, snapshot.artist)
            .putString(KEY_ARTWORK_URI, snapshot.artworkUri)
            .putBoolean(KEY_IS_PLAYING, snapshot.isPlaying)
            .putString(KEY_MEDIA_URI, snapshot.mediaUri)
            .putLong(KEY_RESOURCE_ID, snapshot.resourceId)
            .putLong(KEY_SIZE, snapshot.size)
            .putLong(KEY_DATE_MODIFIED, snapshot.dateModified)
            .putBoolean(KEY_IS_FAVORITE, snapshot.isFavorite)
            .apply()
    }

    /** The cache outlives a test method because the owner is an object, so a suite drops it here. */
    @VisibleForTesting
    internal fun dropCacheForTest() {
        cached = null
    }

    fun clear(context: Context) {
        write(context, Snapshot())
    }

    fun updateFavoriteState(context: Context, isFavorite: Boolean) {
        write(context, read(context).copy(isFavorite = isFavorite))
    }

    fun updateWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, AudioNowPlayingWidgetProvider::class.java)
        )
        ids.forEach { id ->
            AudioNowPlayingWidgetProvider.updateAppWidget(context, manager, id)
        }
    }
}
