package com.sza.fastmediasorter.ui.common.widget.dimclock

import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3366: the non-launcher dim overlay keeps inert chips - no tap opens anything.
 *
 * The launcher flavor binds the intent-routing implementation; the dim overlay's chip taps must
 * stay no-ops where there is no launcher strip whose open mechanics could be shared.
 */
@Singleton
class DefaultDimChipActionRouter @Inject constructor() : DimChipActionRouter {
    override fun openChip(chip: DimStatusChip) = Unit
    override fun openBatteryUsage() = Unit
}
