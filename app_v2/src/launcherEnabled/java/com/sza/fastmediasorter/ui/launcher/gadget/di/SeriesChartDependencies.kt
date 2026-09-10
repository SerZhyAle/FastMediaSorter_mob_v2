package com.sza.fastmediasorter.ui.launcher.gadget.di

import com.sza.fastmediasorter.core.format.QuantityFormatter
import com.sza.fastmediasorter.domain.repository.SensorAvailabilityRepository
import com.sza.fastmediasorter.domain.unit.UnitSystemProvider
import com.sza.fastmediasorter.domain.usecase.sensors.ObserveMotionUseCase
import com.sza.fastmediasorter.domain.usecase.sensors.ObserveSensorSeriesUseCase
import com.sza.fastmediasorter.domain.usecase.sensors.RecordSensorSeriesPointUseCase
import com.sza.fastmediasorter.domain.usecase.sensors.ResetSensorSeriesUseCase
import javax.inject.Inject

/**
 * S1179: what every chart tile needs, so the provider that builds them takes one parameter instead
 * of five. Without it the `@SensorGadgets` provider grows a parameter per collaborator and per tile
 * at once, and reaches detekt's `LongParameterList` threshold as soon as a fourth gadget joins.
 *
 * A holder rather than a factory: the two charts differ only in their key, label, series and whether
 * the second line is drawn, and those four belong at the call site where the difference is visible.
 */
class SeriesChartDependencies @Inject constructor(
    val availability: SensorAvailabilityRepository,
    val observeSeries: ObserveSensorSeriesUseCase,
    val observeMotion: ObserveMotionUseCase,
    val recordPoint: RecordSensorSeriesPointUseCase,
    val resetSeries: ResetSensorSeriesUseCase,
    // S2795: the chart labels a value with a unit, so the seam and the current system belong to the
    // same holder - passing them separately would put the provider past detekt's parameter threshold.
    val quantityFormatter: QuantityFormatter,
    val unitSystemProvider: UnitSystemProvider,
)
