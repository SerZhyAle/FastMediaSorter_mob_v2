package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.repository.AlbumArtRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * S1689: decides whether a track's cover should be fetched from the network, and fetches it.
 *
 * The rule it owns - the file's own cover wins, the network is asked only when there is none and
 * the user asked for it - is a policy, not screen logic, which is why it lives here rather than in
 * the player's ViewModel. The Download album art switch had been offered on two screens, and on the
 * phone defaulted to on, while nothing anywhere read it; this is the reader.
 */
class ResolveAlbumArtUseCase @Inject constructor(
    private val albumArtRepository: AlbumArtRepository,
    private val preferencesRepository: WearPreferencesRepository
) {

    /** Returns a cover URL to display, or null to leave the file without one. */
    suspend operator fun invoke(file: WearMediaFile): String? {
        val artist = file.artist?.takeIf { it.isNotBlank() }
        val album = file.album?.takeIf { it.isNotBlank() }
        // A network listing knows a file name and nothing else, so it never reaches the lookup.
        val eligible = file.albumArt == null && artist != null && album != null
        return if (eligible && preferencesRepository.downloadAlbumArt.first()) {
            albumArtRepository.getAlbumArtUrl(artist, album).getOrNull()
        } else {
            null
        }
    }
}
