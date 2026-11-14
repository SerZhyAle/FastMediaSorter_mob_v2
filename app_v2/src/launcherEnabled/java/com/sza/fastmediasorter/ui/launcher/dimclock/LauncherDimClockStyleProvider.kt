package com.sza.fastmediasorter.ui.launcher.dimclock

import android.content.Context
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimClockStyle
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimClockStyleProvider
import com.sza.fastmediasorter.ui.launcher.gadget.ClockDialTypeface
import com.sza.fastmediasorter.ui.launcher.gadget.ClockGadgetStateStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3256: Dim clock style provider bound to launcher ClockGadgetStateStore in launcherEnabled flavors.
 */
@Singleton
class LauncherDimClockStyleProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clockGadgetStateStore: ClockGadgetStateStore
) : DimClockStyleProvider {

    override fun getStyle(): DimClockStyle {
        val displayState = clockGadgetStateStore.read()
        return DimClockStyle(
            dialColor = displayState.dialColor,
            dialTypeface = displayState.dialTypefaceName,
            // S4562: the dim time draws the desktop clock's bundled font, which only this source set knows.
            timeTypeface = ClockDialTypeface
                .fromPersistedName(displayState.dialTypefaceName)
                .resolveTypeface(context),
            secondsVisible = displayState.secondsVisible
        )
    }

    override val secondsVisible: Boolean
        get() = clockGadgetStateStore.read().secondsVisible
}
