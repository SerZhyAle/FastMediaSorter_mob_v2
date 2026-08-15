package com.sza.fastmediasorter.ui.player

import android.graphics.Color
import android.graphics.Typeface
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.sza.fastmediasorter.domain.models.TranslationFontFamily
import com.sza.fastmediasorter.domain.models.TranslationFontSize
import com.sza.fastmediasorter.domain.usecase.streams.StreamTrackPreferenceUseCase
import timber.log.Timber

/**
 * Manages audio/subtitle track selection and subtitle styling for ExoPlayer.
 * Extracted from VideoPlayerManager to reduce its size.
 */
class VideoTrackSelectionManager(
    private val getPlayer: () -> ExoPlayer?,
    private val getPlayerView: () -> PlayerView?
) {

    data class TrackInfo(
        val groupIndex: Int,
        val trackIndex: Int,
        val label: String,
        val isSelected: Boolean,
        // S1144 (ADR-8): kept as a field so the write-back can persist the picked language without
        // re-walking Tracks - it is already read here to build [label].
        val language: String? = null
    )

    /**
     * S1144 (ADR-4): the playing channel's remembered track languages, seated by the stream-start path
     * and cleared on every new item. Null means "no per-channel preference" and the global default wins.
     * Held as a property rather than a parameter because [applyTrackSelection] is reached from several
     * call sites that know nothing about streams.
     */
    var channelPreference: StreamTrackPreferenceUseCase.TrackPreference? = null

    /**
     * S1144 (phase 04): the global stream defaults from `AppSettings`, seated by the stream-start path.
     * They sit between the per-channel preference (wins) and the generic player settings (fallback), so a
     * user who set "Russian audio for streams" gets it on every channel that has no pick of its own.
     */
    var streamDefaults: StreamDefaults? = null

    /** Global stream track defaults; null members mean "no stream-specific default, use player settings". */
    data class StreamDefaults(val audioIso: String?, val subtitleIso: String?)

    fun applyTrackSelection(
        player: ExoPlayer,
        settings: PlayerSettings,
        appLanguage: String
    ) {
        var paramsBuilder = player.trackSelectionParameters.buildUpon()
        val preference = channelPreference

        val preferredAudioLang = preference?.audioLang
            ?: streamDefaults?.audioIso
            ?: when (settings.audioLanguage) {
            PlayerSettings.LanguageOption.DEFAULT -> appLanguage
            PlayerSettings.LanguageOption.ENGLISH -> "en"
            PlayerSettings.LanguageOption.RUSSIAN -> "ru"
            PlayerSettings.LanguageOption.UKRAINIAN -> "uk"
        }
        paramsBuilder = paramsBuilder.setPreferredAudioLanguage(preferredAudioLang)
        Timber.d("VideoTrackSelectionManager: Set preferred audio language to $preferredAudioLang")

        if (preference?.subtitlesEnabled ?: settings.showSubtitles) {
            val preferredSubtitleLang = preference?.subtitleLang
                ?: streamDefaults?.subtitleIso
                ?: when (settings.subtitleLanguage) {
                PlayerSettings.LanguageOption.DEFAULT -> appLanguage
                PlayerSettings.LanguageOption.ENGLISH -> "en"
                PlayerSettings.LanguageOption.RUSSIAN -> "ru"
                PlayerSettings.LanguageOption.UKRAINIAN -> "uk"
            }
            paramsBuilder = paramsBuilder
                .setPreferredTextLanguage(preferredSubtitleLang)
                .setIgnoredTextSelectionFlags(0)
            Timber.d("VideoTrackSelectionManager: Set preferred subtitle language to $preferredSubtitleLang")
        } else {
            paramsBuilder = paramsBuilder
                .setIgnoredTextSelectionFlags(
                    C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_AUTOSELECT or C.SELECTION_FLAG_FORCED
                )
            Timber.d("VideoTrackSelectionManager: Subtitles disabled")
        }

        player.trackSelectionParameters = paramsBuilder.build()
    }

    fun applySubtitleStyle(fontSize: TranslationFontSize, fontFamily: TranslationFontFamily) {
        val subtitleView = getPlayerView()?.subtitleView ?: run {
            Timber.w("VideoTrackSelectionManager: No subtitle view available")
            return
        }

        val textSizeSp = if (fontSize == TranslationFontSize.AUTO) {
            subtitleView.setFractionalTextSize(0.0533f)
            null
        } else {
            val baseSizeSp = 24f
            baseSizeSp * fontSize.multiplier
        }

        val typeface = when (fontFamily) {
            TranslationFontFamily.DEFAULT -> Typeface.DEFAULT
            TranslationFontFamily.SERIF -> Typeface.SERIF
            TranslationFontFamily.MONOSPACE -> Typeface.MONOSPACE
        }

        val captionStyle = CaptionStyleCompat(
            Color.WHITE,
            Color.argb(200, 0, 0, 0),
            Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            Color.BLACK,
            typeface
        )

        subtitleView.setStyle(captionStyle)

        if (textSizeSp != null) {
            subtitleView.setFixedTextSize(
                android.util.TypedValue.COMPLEX_UNIT_SP,
                textSizeSp
            )
        }

        Timber.d("VideoTrackSelectionManager: Applied subtitle style - fontSize=${fontSize.name}, fontFamily=${fontFamily.name}")
    }

    fun getAvailableAudioTracks(): List<TrackInfo> {
        val player = getPlayer() ?: return emptyList()
        val tracks = player.currentTracks
        val result = mutableListOf<TrackInfo>()
        var trackNumber = 1

        for ((groupIndex, group) in tracks.groups.withIndex()) {
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val lang = format.language?.uppercase() ?: ""
                val codec = format.sampleMimeType?.substringAfter("/") ?: ""
                val channels = when (format.channelCount) {
                    1 -> "Mono"
                    2 -> "Stereo"
                    6 -> "5.1"
                    8 -> "7.1"
                    else -> if (format.channelCount > 0) "${format.channelCount}ch" else ""
                }
                val parts = listOfNotNull(
                    lang.ifEmpty { null },
                    codec.ifEmpty { null },
                    channels.ifEmpty { null }
                )
                // Mark tracks that the device cannot decode so the user is informed before selecting
                val isSupported = group.getTrackSupport(trackIndex) == C.FORMAT_HANDLED
                val baseLabel = if (parts.isNotEmpty()) {
                    "Track $trackNumber (${parts.joinToString(", ")})"
                } else {
                    "Track $trackNumber"
                }
                val label = if (isSupported) baseLabel else "$baseLabel ⚠ Unsupported"
                result.add(
                    TrackInfo(
                        groupIndex,
                        trackIndex,
                        label,
                        group.isTrackSelected(trackIndex),
                        format.language
                    )
                )
                trackNumber++
            }
        }
        return result
    }

    fun getAvailableSubtitleTracks(): List<TrackInfo> {
        val player = getPlayer() ?: return emptyList()
        val tracks = player.currentTracks
        val result = mutableListOf<TrackInfo>()
        var trackNumber = 1

        for ((groupIndex, group) in tracks.groups.withIndex()) {
            if (group.type != C.TRACK_TYPE_TEXT) continue
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val lang = format.language?.let { java.util.Locale(it).displayLanguage } ?: ""
                val label = if (lang.isNotEmpty()) {
                    "$lang (Track $trackNumber)"
                } else {
                    "Track $trackNumber"
                }
                result.add(
                    TrackInfo(
                        groupIndex,
                        trackIndex,
                        label,
                        group.isTrackSelected(trackIndex),
                        format.language
                    )
                )
                trackNumber++
            }
        }
        return result
    }

    fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {
        val player = getPlayer() ?: return
        val tracks = player.currentTracks
        if (groupIndex >= tracks.groups.size) return

        val group = tracks.groups[groupIndex]
        val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex))
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(override)
            .build()
        Timber.d("VideoTrackSelectionManager: Selected audio track group=$groupIndex, track=$trackIndex")
    }

    fun selectSubtitleTrack(groupIndex: Int, trackIndex: Int) {
        val player = getPlayer() ?: return

        if (trackIndex < 0) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            Timber.d("VideoTrackSelectionManager: Subtitles disabled")
            return
        }

        val tracks = player.currentTracks
        if (groupIndex >= tracks.groups.size) return

        val group = tracks.groups[groupIndex]
        val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex))
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(override)
            .build()
        Timber.d("VideoTrackSelectionManager: Selected subtitle track group=$groupIndex, track=$trackIndex")
    }

    fun hasMultipleAudioTracks(): Boolean = getAvailableAudioTracks().size > 1

    fun hasSubtitleTracks(): Boolean = getAvailableSubtitleTracks().isNotEmpty()
}
