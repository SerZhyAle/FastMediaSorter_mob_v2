package com.sza.fastmediasorter.domain.usecase.stopwatch

import com.sza.fastmediasorter.domain.model.stopwatch.MusicTrackOption
import com.sza.fastmediasorter.domain.repository.MusicTrackRepository
import javax.inject.Inject

/**
 * Loads the device's music for a chooser dialog (S2792).
 *
 * A use case rather than a repository call at the call site because the screen must not import a
 * repository (the ui-imports-repository gate), and the next consumer - the slideshow's music picker -
 * repeats exactly this call.
 */
class LoadMusicTracksUseCase @Inject constructor(
    private val repository: MusicTrackRepository,
) {

    suspend operator fun invoke(): List<MusicTrackOption> = repository.loadTracks()
}
