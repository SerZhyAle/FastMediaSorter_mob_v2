package com.sza.fastmediasorter.data.repository

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.sza.fastmediasorter.domain.model.stopwatch.MusicTrackOption
import com.sza.fastmediasorter.domain.repository.MusicTrackRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * MediaStore-backed [MusicTrackRepository] (S2792).
 *
 * Reads only the external audio collection through the projection a picker row needs, off the
 * calling frame. A query that returns rows proves the read access a media-manager app already
 * holds, which is also why the picked uri needs no persistable grant the way a SAF uri did.
 */
class MediaStoreMusicTrackRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : MusicTrackRepository {

    override suspend fun loadTracks(): List<MusicTrackOption> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<MusicTrackOption>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            PROJECTION,
            null,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC",
        )?.use { cursor ->
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            while (cursor.moveToNext()) {
                val title = cursor.getString(titleIndex)?.trim().orEmpty()
                if (title.isEmpty()) continue
                tracks.add(
                    MusicTrackOption(
                        uri = Uri.withAppendedPath(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            cursor.getLong(idIndex).toString(),
                        ),
                        title = title,
                        // The media store reports "<unknown>" for a missing artist rather than null.
                        artist = cursor.getString(artistIndex)?.trim()?.takeIf { it.isNotEmpty() },
                        durationMillis = cursor.getLong(durationIndex).coerceAtLeast(0L),
                    ),
                )
            }
        }
        tracks
    }

    override suspend fun titleOf(uri: Uri): String? = withContext(Dispatchers.IO) {
        context.contentResolver.query(
            uri,
            PROJECTION,
            null,
            null,
            null,
        )?.use { cursor ->
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            if (cursor.moveToFirst()) cursor.getString(titleIndex)?.trim()?.takeIf { it.isNotEmpty() } else null
        }
    }

    private companion object {
        val PROJECTION = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
        )
    }
}
