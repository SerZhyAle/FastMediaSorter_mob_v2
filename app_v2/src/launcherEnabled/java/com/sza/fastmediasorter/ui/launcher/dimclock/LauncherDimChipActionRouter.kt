package com.sza.fastmediasorter.ui.launcher.dimclock

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.sza.fastmediasorter.core.panel.OsShortcutCatalog
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimChipActionRouter
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimStatusChip
import com.sza.fastmediasorter.ui.launcher.signal.source.ForeignNotificationSignalSource
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3366: turns a dim-screen tap into the intent its chip promised - a notification chip opens its
 * application's launch intent, the battery box opens the system battery-usage screen.
 *
 * The open mechanics mirror the launcher strip's signal source on purpose (launch intent with
 * NEW_TASK and CLEAR_TOP, a silent no-op when an application declares no screen): one behaviour, two
 * surfaces, so a chip cannot open differently depending on which row it sits in. The dim overlay's
 * exit hook has already fired by the time [openChip] starts - ADR-2's ordering lives at the call site.
 */
@Singleton
class LauncherDimChipActionRouter @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DimChipActionRouter {

    override fun openChip(chip: DimStatusChip) {
        when {
            chip.isNotification -> openForeignNotification(chip)
            else -> openStatusChip(chip)
        }
    }

    /**
     * The tray's own pairings (S2025/S2027): bluetooth to its settings, wifi to the wifi screen,
     * every other transport, the SIM slots and the speed readout (S3475) to wireless settings, and
     * tethering to the screen the tray routes it to. An unknown id or
     * an unresolvable target is a logged no-op, never a crash on a tap.
     */
    private fun openStatusChip(chip: DimStatusChip) {
        val intent = when (chip.id) {
            ConnectivityDimStatusSource.ID_BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            ConnectivityDimStatusSource.ID_NETWORK_WIFI -> Intent(Settings.ACTION_WIFI_SETTINGS)
            ConnectivityDimStatusSource.ID_NETWORK_OTHER,
            ConnectivityDimStatusSource.ID_SIM1,
            ConnectivityDimStatusSource.ID_SIM2,
            ConnectivityDimStatusSource.ID_SPEED_RX,
            ConnectivityDimStatusSource.ID_SPEED_TX,
            -> Intent(Settings.ACTION_WIRELESS_SETTINGS)
            ConnectivityDimStatusSource.ID_TETHERING ->
                OsShortcutCatalog.byKey(OsShortcutCatalog.KEY_TETHERING)?.intent(context)

            else -> null
        }
        start(intent)
    }

    override fun openBatteryUsage() {
        start(OsShortcutCatalog.byKey(OsShortcutCatalog.KEY_BATTERY)?.intent(context))
    }

    private fun openForeignNotification(chip: DimStatusChip) {
        if (!chip.id.startsWith(ForeignNotificationSignalSource.SIGNAL_ID_PREFIX)) {
            Timber.d("Dim chip tap: %s is not a foreign notification id", chip.id)
            return
        }
        val packageName = chip.id.removePrefix(ForeignNotificationSignalSource.SIGNAL_ID_PREFIX)
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        if (intent == null) {
            Timber.w("Dim chip tap: %s declares no launch intent", packageName)
            return
        }
        start(intent)
    }

    private fun start(intent: Intent?) {
        if (intent == null) {
            Timber.w("Dim chip tap: target intent missing or unresolvable")
            return
        }
        // The router holds the application context: without NEW_TASK a start from it throws before
        // any activity appears. Idempotent for the launch intents that already carry the flag.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "Dim chip tap: target refused to open") }
    }
}
