package com.sza.fastmediasorter.ui.common.widget.dimclock

import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3256: Default launcher clock style fallback for flavors without launcherEnabled.
 */
@Singleton
class DefaultDimClockStyleProvider @Inject constructor() : DimClockStyleProvider {
    override fun getStyle(): DimClockStyle = DimClockStyle.DEFAULT
}
