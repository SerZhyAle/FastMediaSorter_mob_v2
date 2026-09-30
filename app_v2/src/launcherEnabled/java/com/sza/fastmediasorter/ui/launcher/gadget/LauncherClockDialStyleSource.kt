package com.sza.fastmediasorter.ui.launcher.gadget

import com.sza.fastmediasorter.domain.repository.ClockDialStyle
import com.sza.fastmediasorter.domain.repository.ClockDialStyleSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** S3557: exposes the launcher clock gadget's persisted choices to the watch-face publisher. */
class LauncherClockDialStyleSource @Inject constructor(
    private val store: ClockGadgetStateStore,
) : ClockDialStyleSource {

    override fun observe(): Flow<ClockDialStyle> = store.changes().map { state ->
        ClockDialStyle(
            secondsVisible = state.secondsVisible,
            dialColor = state.dialColor,
            typefaceName = state.dialTypefaceName,
        )
    }
}
