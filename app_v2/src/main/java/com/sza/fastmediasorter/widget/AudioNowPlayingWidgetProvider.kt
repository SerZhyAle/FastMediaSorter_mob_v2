package com.sza.fastmediasorter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.db.AppDatabase
import com.sza.fastmediasorter.data.local.db.FavoritesEntity
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.ui.player.AudioPlaybackService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class AudioNowPlayingWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            updateAppWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE_FAVORITE) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    toggleFavorite(context.applicationContext)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        private const val ACTION_TOGGLE_FAVORITE =
            "com.sza.fastmediasorter.action.AUDIO_WIDGET_TOGGLE_FAVORITE"
        private const val REQUEST_OPEN_APP = 10
        private const val REQUEST_PREVIOUS = 11
        private const val REQUEST_PLAY_PAUSE = 12
        private const val REQUEST_NEXT = 13
        private const val REQUEST_FAVORITE = 14

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val snapshot = AudioNowPlayingSnapshotStore.read(context)
            val views = RemoteViews(context.packageName, R.layout.widget_audio_now_playing)

            views.setTextViewText(
                R.id.widget_audio_now_playing_title,
                snapshot.title.takeIf { snapshot.active && it.isNotBlank() }
                    ?: context.getString(R.string.widget_audio_now_playing_empty_title)
            )
            views.setTextViewText(
                R.id.widget_audio_now_playing_artist,
                snapshot.artist.takeIf { snapshot.active && it.isNotBlank() }
                    ?: if (snapshot.active) {
                        context.getString(R.string.widget_audio_now_playing_artist_unknown)
                    } else {
                        context.getString(R.string.widget_audio_now_playing_empty_subtitle)
                    }
            )
            views.setImageViewResource(
                R.id.widget_audio_now_playing_play_pause,
                if (snapshot.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
            views.setImageViewResource(
                R.id.widget_audio_now_playing_favorite,
                if (snapshot.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
            bindArtwork(views, snapshot)
            bindActions(context, views, snapshot)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun bindArtwork(
            views: RemoteViews,
            snapshot: AudioNowPlayingSnapshotStore.Snapshot
        ) {
            val artworkUri = snapshot.artworkUri.takeIf { snapshot.active && it.isNotBlank() }
            if (artworkUri != null) {
                views.setImageViewUri(R.id.widget_audio_now_playing_artwork, Uri.parse(artworkUri))
            } else {
                views.setImageViewResource(R.id.widget_audio_now_playing_artwork, R.drawable.ic_music_note)
            }
        }

        private fun bindActions(
            context: Context,
            views: RemoteViews,
            snapshot: AudioNowPlayingSnapshotStore.Snapshot
        ) {
            views.setOnClickPendingIntent(
                R.id.widget_audio_now_playing_container,
                openAppPendingIntent(context)
            )
            if (!snapshot.active) return

            views.setOnClickPendingIntent(
                R.id.widget_audio_now_playing_previous,
                serviceCommandPendingIntent(context, REQUEST_PREVIOUS, AudioPlaybackService.WIDGET_COMMAND_PREVIOUS)
            )
            views.setOnClickPendingIntent(
                R.id.widget_audio_now_playing_play_pause,
                serviceCommandPendingIntent(context, REQUEST_PLAY_PAUSE, AudioPlaybackService.WIDGET_COMMAND_PLAY_PAUSE)
            )
            views.setOnClickPendingIntent(
                R.id.widget_audio_now_playing_next,
                serviceCommandPendingIntent(context, REQUEST_NEXT, AudioPlaybackService.WIDGET_COMMAND_NEXT)
            )
            if (snapshot.canToggleFavorite) {
                views.setOnClickPendingIntent(
                    R.id.widget_audio_now_playing_favorite,
                    favoritePendingIntent(context)
                )
            }
        }

        private fun openAppPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                REQUEST_OPEN_APP,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun serviceCommandPendingIntent(
            context: Context,
            requestCode: Int,
            command: String
        ): PendingIntent {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                action = AudioPlaybackService.ACTION_WIDGET_COMMAND
                putExtra(AudioPlaybackService.EXTRA_WIDGET_COMMAND, command)
            }
            return PendingIntent.getService(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun favoritePendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, AudioNowPlayingWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_FAVORITE
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_FAVORITE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private suspend fun toggleFavorite(context: Context) {
            val snapshot = AudioNowPlayingSnapshotStore.read(context)
            if (!snapshot.canToggleFavorite) return

            try {
                val database = EntryPointAccessors
                    .fromApplication(context, AudioNowPlayingWidgetEntryPoint::class.java)
                    .database()
                val dao = database.favoritesDao()
                val isFavorite = dao.isFavoriteSync(snapshot.mediaUri)
                if (isFavorite) {
                    dao.deleteByUri(snapshot.mediaUri)
                } else {
                    dao.insert(
                        FavoritesEntity(
                            uri = snapshot.mediaUri,
                            resourceId = snapshot.resourceId,
                            displayName = snapshot.title.ifBlank {
                                snapshot.mediaUri.substringAfterLast('/')
                            },
                            mediaType = MediaType.AUDIO.ordinal,
                            size = snapshot.size,
                            lastKnownPath = snapshot.mediaUri,
                            dateModified = snapshot.dateModified.takeIf { it > 0L }
                                ?: System.currentTimeMillis()
                        )
                    )
                }
                AudioNowPlayingSnapshotStore.updateFavoriteState(context, !isFavorite)
            } catch (e: Exception) {
                Timber.e(e, "AudioNowPlayingWidgetProvider: failed to toggle favorite")
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AudioNowPlayingWidgetEntryPoint {
    fun database(): AppDatabase
}
