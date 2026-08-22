package com.sza.fastmediasorter.ui.launcher.signal.source

import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignal
import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignalIcon
import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignalKind
import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignalSource
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.widget.AudioNowPlayingSnapshotStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/**
 * S1421 ADR-1: what this app is playing, taken from state it already writes.
 *
 * Reads the snapshot the playback service keeps for the Now Playing widget, so the strip needs no new
 * service, no new permission and no second source of truth about what is playing.
 */
class PlaybackLauncherSignalSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : LauncherSignalSource {

    /**
     * Polls, because the snapshot store is a `SharedPreferences` file with no change notification. On IO
     * for the same reason: the first read of that file touches disk, and a `StrictMode` violation is the
     * one thing a status strip must not cause.
     */
    override fun observe(): Flow<List<LauncherSignal>> = flow {
        while (true) {
            emit(read())
            delay(REFRESH_INTERVAL_MS)
        }
    }.flowOn(Dispatchers.IO)

    private fun read(): List<LauncherSignal> {
        val snapshot = AudioNowPlayingSnapshotStore.read(context)
        if (!snapshot.active) {
            return emptyList()
        }
        return listOf(
            LauncherSignal(
                id = SIGNAL_ID,
                kind = LauncherSignalKind.PLAYBACK,
                icon = LauncherSignalIcon.Resource(R.drawable.ic_music_note),
                label = snapshot.title.ifBlank { context.getString(R.string.launcher_signal_playback) },
                detail = snapshot.artist.ifBlank { null },
            ),
        )
    }

    /**
     * The same route the playback notification's `sessionActivity` takes: `MainActivity` reopens the exact
     * player screen from the ids it stored, even after the process was killed. Rebuilt here rather than
     * shared with `AudioPlaybackService`, which needs a `PendingIntent` and not a bare one.
     */
    override fun open(signal: LauncherSignal): Intent? {
        if (signal.id != SIGNAL_ID) {
            return null
        }
        return Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_RESUME_PLAYER
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    private companion object {
        const val SIGNAL_ID = "playback"
        const val REFRESH_INTERVAL_MS = 2_000L
    }
}
