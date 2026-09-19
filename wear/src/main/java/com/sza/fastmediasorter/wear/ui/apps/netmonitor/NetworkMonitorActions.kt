package com.sza.fastmediasorter.wear.ui.apps.netmonitor

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import timber.log.Timber

/**
 * System settings and interaction helpers for the Wear Network Monitor.
 */
object NetworkMonitorActions {

    fun openWifiSettings(context: Context) {
        openSettingsIntent(context, Settings.ACTION_WIFI_SETTINGS)
    }

    fun openBluetoothSettings(context: Context) {
        openSettingsIntent(context, Settings.ACTION_BLUETOOTH_SETTINGS)
    }

    fun openLocationSettings(context: Context) {
        openSettingsIntent(context, Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    }

    fun openWirelessSettings(context: Context) {
        openSettingsIntent(context, Settings.ACTION_WIRELESS_SETTINGS)
    }

    private fun openSettingsIntent(context: Context, action: String) {
        Timber.d("S3199: openSettingsIntent %s", action)
        try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "Failed to launch settings intent: %s", action)
        } catch (e: SecurityException) {
            Timber.w(e, "Settings intent refused: %s", action)
        }
    }
}
