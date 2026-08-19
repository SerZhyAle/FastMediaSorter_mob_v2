package com.sza.fastmediasorter.ui.launcher.gadget.di

import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.sensors.SensorSeriesId
import com.sza.fastmediasorter.ui.launcher.gadget.AltitudeGadget
import com.sza.fastmediasorter.ui.launcher.gadget.CompassGadget
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadget
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadgetRegistry
import com.sza.fastmediasorter.ui.launcher.gadget.MapGadget
import com.sza.fastmediasorter.ui.launcher.gadget.SatellitesGadget
import com.sza.fastmediasorter.ui.launcher.gadget.SeriesChartGadget
import com.sza.fastmediasorter.ui.launcher.gadget.SpeedGadget
import com.sza.fastmediasorter.ui.launcher.gadget.StepsGadget
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * S1179: the sensor gadgets, supplied as ONE binding for the same reason [HomeWidgetGadgets] is -
 * `LauncherGadgetRegistry` would otherwise grow a constructor parameter per tile and pass detekt's
 * `constructorThreshold`. Every later sensor gadget joins this list rather than the constructor.
 *
 * Qualified because the payload is `List<LauncherGadget>`, the exact type the registry deals in; an
 * unqualified list binding would otherwise satisfy this injection point by accident.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SensorGadgets

@Module
@InstallIn(SingletonComponent::class)
object SensorGadgetModule {

    /**
     * The two charts are one class instantiated twice, differing only in which series they read and
     * whether the second line is drawn. A third chart is another entry here and no new view
     * (strategic §5.3).
     */
    @Provides
    @Singleton
    @SensorGadgets
    fun provideSensorGadgets(
        compass: CompassGadget,
        speed: SpeedGadget,
        altitude: AltitudeGadget,
        satellites: SatellitesGadget,
        steps: StepsGadget,
        // S1175: the map tile is a location reader like the compass and the speed tile, and it takes
        // the same permission row, so it belongs to this list rather than to a sixth qualifier.
        map: MapGadget,
        charts: SeriesChartDependencies,
    ): List<LauncherGadget> = listOf(
        altitude,
        speed,
        steps,
        compass,
        satellites,
        map,
        SeriesChartGadget(
            key = LauncherGadgetRegistry.KEY_SPEED_CHART,
            labelRes = R.string.launcher_gadget_speed_chart,
            seriesId = SensorSeriesId.SPEED,
            secondaryShown = false,
            availability = charts.availability,
            observeSeries = charts.observeSeries,
            observeMotion = charts.observeMotion,
            recordPoint = charts.recordPoint,
            resetSeries = charts.resetSeries,
        ),
        SeriesChartGadget(
            key = LauncherGadgetRegistry.KEY_ALTITUDE_CHART,
            labelRes = R.string.launcher_gadget_altitude_chart,
            seriesId = SensorSeriesId.ALTITUDE_DISTANCE,
            secondaryShown = true,
            availability = charts.availability,
            observeSeries = charts.observeSeries,
            observeMotion = charts.observeMotion,
            recordPoint = charts.recordPoint,
            resetSeries = charts.resetSeries,
        ),
    )
}
