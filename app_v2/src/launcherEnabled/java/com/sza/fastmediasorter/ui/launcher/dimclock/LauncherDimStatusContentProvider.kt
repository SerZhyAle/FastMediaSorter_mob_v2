package com.sza.fastmediasorter.ui.launcher.dimclock

import com.sza.fastmediasorter.domain.model.devicestatus.MetricValue
import com.sza.fastmediasorter.domain.usecase.devicestatus.GetBatteryStatusUseCase
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimStatusChip
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimStatusContentProvider
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimStatusSnapshot
import com.sza.fastmediasorter.ui.launcher.signal.ForeignNotificationCounts
import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignal
import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignalIcon
import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignalKind
import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignalRegistry
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3256: Dim status content provider combining battery status, foreign notification counts,
 * and launcher signals for the dim screen overlay in launcherEnabled flavors.
 */
@Singleton
class LauncherDimStatusContentProvider @Inject constructor(
    private val getBatteryStatusUseCase: GetBatteryStatusUseCase,
    private val launcherSignalRegistry: LauncherSignalRegistry,
    private val foreignNotificationCounts: ForeignNotificationCounts,
    private val connectivitySource: ConnectivityDimStatusSource,
) : DimStatusContentProvider {

    private fun batteryFlow(): Flow<Pair<Int, Boolean>> = flow {
        while (currentCoroutineContext().isActive) {
            val battery = getBatteryStatusUseCase.read()
            val percent = when (val p = battery.percent) {
                is MetricValue.Known -> p.value
                else -> DEFAULT_BATTERY_PERCENT
            }
            emit(Pair(percent, battery.isCharging))
            delay(BATTERY_POLL_INTERVAL_MS)
        }
    }

    override fun observeStatus(): Flow<DimStatusSnapshot> = combine(
        batteryFlow(),
        launcherSignalRegistry.observe(),
        foreignNotificationCounts.counts,
        connectivitySource.inputs(),
    ) { (percent, isCharging), signals, notifCounts, connectivity ->
        // S3366 / S3475: the network read rides whichever tick produced this snapshot, so the dim
        // screen never wakes to ask the network a question on a timer of its own.
        val chips = mapSignalsToChips(signals, notifCounts) +
            connectivitySource.chips(connectivity, connectivitySource.readNetwork())
        Timber.d("S3475: dim status chips=${chips.filterNot { it.isNotification }.map { it.id }}")
        DimStatusSnapshot(
            batteryPercent = percent,
            isCharging = isCharging,
            chips = chips
        )
    }

    private fun mapSignalsToChips(
        signals: List<LauncherSignal>,
        notifCounts: Map<String, Int>
    ): List<DimStatusChip> = signals.map { signal ->
        val isNotif = signal.kind == LauncherSignalKind.FOREIGN_NOTIFICATION
        val count = if (isNotif) {
            when (val icon = signal.icon) {
                is LauncherSignalIcon.Application -> notifCounts[icon.packageName] ?: 1
                else -> 1
            }
        } else {
            0
        }
        val (iconRes, pkgName) = when (val icon = signal.icon) {
            is LauncherSignalIcon.Resource -> Pair(icon.res, null)
            is LauncherSignalIcon.Application -> Pair(icon.fallbackRes, icon.packageName)
        }
        DimStatusChip(
            id = signal.id,
            iconResId = iconRes,
            packageName = pkgName,
            count = count,
            isNotification = isNotif,
            // S3366: the application name is the chip's accessibility label and its tap meaning.
            contentDescription = signal.label
        )
    }

    companion object {
        private const val DEFAULT_BATTERY_PERCENT = 100
        private const val BATTERY_POLL_INTERVAL_MS = 15_000L
    }
}
