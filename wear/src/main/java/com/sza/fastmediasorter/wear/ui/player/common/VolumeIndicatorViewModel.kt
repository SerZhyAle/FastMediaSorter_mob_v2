package com.sza.fastmediasorter.wear.ui.player.common

import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject

/**
 * S2140: owns the media level the [VolumeIndicatorBar] draws, for any screen that wants to warn how
 * loud the next tap will be.
 *
 * Deliberately its own holder rather than a field on the screen's view model. The reading belongs to
 * the indicator, not to whatever list happens to host it, so a second screen adopting the bar needs one
 * `hiltViewModel()` call and no edit to its own state holder - which is the extension point strategic
 * section 5.3 asks for. It also keeps `BrowseViewModel`'s constructor off detekt's parameter ceiling,
 * where an injected `Context` for one system read had pushed it.
 *
 * Read once, in `init`, from the same `STREAM_MUSIC` both players read and write. No observer and no
 * receiver: strategic section 2 rules live tracking out, and a permanently registered listener is a
 * poor trade for a warning that need not follow a change made elsewhere while the screen sits open.
 */
@HiltViewModel
class VolumeIndicatorViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _readout = MutableStateFlow(VolumeReadout())
    val readout: StateFlow<VolumeReadout> = _readout.asStateFlow()

    init {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null) {
            _readout.value = VolumeReadout(
                level = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
                max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            )
        }
        Timber.d("S2140: file list volume readout ${_readout.value.level}/${_readout.value.max}")
    }
}
