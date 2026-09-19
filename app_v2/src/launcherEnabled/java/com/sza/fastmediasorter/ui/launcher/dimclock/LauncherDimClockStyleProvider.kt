package com.sza.fastmediasorter.ui.launcher.dimclock

import com.sza.fastmediasorter.ui.common.widget.dimclock.DimClockStyle
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimClockStyleProvider
import com.sza.fastmediasorter.ui.launcher.gadget.ClockGadgetStateStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3256: Dim clock style provider bound to launcher ClockGadgetStateStore in launcherEnabled flavors.
 */
@Singleton
class LauncherDimClockStyleProvider @Inject constructor(
    private val clockGadgetStateStore: ClockGadgetStateStore
) : DimClockStyleProvider {

    override fun getStyle(): DimClockStyle {
        val displayState = clockGadgetStateStore.read()
        return DimClockStyle(
            dialColor = displayState.dialColor,
            dialTypeface = displayState.dialTypefaceName,
            secondsVisible = displayState.secondsVisible
        )
    }

    override val secondsVisible: Boolean
        get() = clockGadgetStateStore.read().secondsVisible
}
