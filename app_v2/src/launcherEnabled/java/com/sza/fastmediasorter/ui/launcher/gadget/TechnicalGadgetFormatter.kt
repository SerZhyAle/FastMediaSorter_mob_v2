package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.devicestatus.BatteryStatus
import com.sza.fastmediasorter.domain.model.devicestatus.MemoryStatus
import com.sza.fastmediasorter.domain.model.devicestatus.MetricValue
import com.sza.fastmediasorter.domain.model.devicestatus.NetworkStatus
import com.sza.fastmediasorter.domain.model.devicestatus.NetworkTransport
import com.sza.fastmediasorter.domain.model.devicestatus.StorageStatus

/**
 * S1178: what one technical gadget puts on screen.
 *
 * [fill] is the bar's fraction and is null whenever there is nothing honest to fill it with. The bar is
 * never the only carrier of a value - [value] stays on screen in every state (strategic 3.2).
 */
data class TechnicalGadgetContent(
    val value: String,
    val caption: String,
    val fill: Float?,
    val description: String
)

/**
 * S1178: turns a device metric into the three strings the shared gadget draws.
 *
 * Sizes go through [Formatter.formatFileSize] and durations through [DateUtils], so units and their
 * order come from the system locale instead of being concatenated here (strategic 3.2). Formatting lives
 * in the view layer on purpose (ADR-1): the metrics stay values another surface can render differently.
 *
 * The transport words are the taskbar tray's `launcher_tray_network_*` keys, deliberately shared rather
 * than duplicated - one transport vocabulary ([NetworkTransport]) with two sets of words would drift.
 */
class TechnicalGadgetFormatter(private val context: Context) {

    private val unknown: String get() = context.getString(R.string.launcher_gadget_technical_unknown)

    /**
     * A fifth metric registered without a branch here prints [unknown] rather than crashing the desktop -
     * strategic 5.3 expects new metrics to arrive as registrations, and a home screen must survive one
     * that arrived half-wired.
     */
    fun format(status: Any): TechnicalGadgetContent = when (status) {
        is NetworkStatus -> network(status)
        is BatteryStatus -> battery(status)
        is StorageStatus -> storage(status)
        is MemoryStatus -> resources(status)
        else -> TechnicalGadgetContent(unknown, "", null, unknown)
    }

    private fun network(status: NetworkStatus): TechnicalGadgetContent {
        val transportLabel = context.getString(transportLabelRes(status.transport))
        val value = (status.networkName as? MetricValue.Known)?.value ?: transportLabel
        val captionRes = if (status.isOnline) {
            R.string.launcher_gadget_network_online
        } else {
            R.string.launcher_gadget_network_offline
        }
        val caption = context.getString(captionRes, transportLabel)
        return TechnicalGadgetContent(
            value = value,
            caption = caption,
            fill = null,
            description = context.getString(R.string.launcher_gadget_network_description, value, caption)
        )
    }

    private fun battery(status: BatteryStatus): TechnicalGadgetContent {
        val percent = (status.percent as? MetricValue.Known)?.value
        val value = percent?.let { context.getString(R.string.launcher_gadget_battery_value, it) } ?: unknown
        return TechnicalGadgetContent(
            value = value,
            caption = batteryCaption(status),
            fill = percent?.let { it.toFloat() / FULL_PERCENT },
            description = context.getString(
                R.string.launcher_gadget_battery_description,
                value,
                batteryCaption(status)
            )
        )
    }

    /** Charging outranks the estimate: a rising charge makes any remaining-time figure meaningless. */
    private fun batteryCaption(status: BatteryStatus): String {
        val remaining = duration(status.remainingMillis)
        val remainingRes = if (status.isEstimateApproximate) {
            R.string.launcher_gadget_battery_remaining_approx
        } else {
            R.string.launcher_gadget_battery_remaining
        }
        return when {
            status.isCharging -> context.getString(R.string.launcher_gadget_battery_charging)
            remaining != null -> context.getString(remainingRes, remaining)
            else -> unknown
        }
    }

    private fun storage(status: StorageStatus): TechnicalGadgetContent {
        val free = bytes(status.internalAvailableBytes)
        val internalLine = context.getString(
            R.string.launcher_gadget_storage_free,
            free,
            bytes(status.internalTotalBytes)
        )
        // No card mounted means no card line at all - a row of zeroes would read as "a full card".
        val cardLine = status.card?.let {
            context.getString(
                R.string.launcher_gadget_storage_card,
                bytes(it.availableBytes),
                bytes(it.totalBytes)
            )
        }
        val caption = listOfNotNull(internalLine, cardLine).joinToString(LINE_BREAK)
        return TechnicalGadgetContent(
            value = free,
            caption = caption,
            fill = fillOf(status.internalTotalBytes, status.internalAvailableBytes),
            description = context.getString(
                R.string.launcher_gadget_storage_description,
                internalLine,
                cardLine.orEmpty()
            )
        )
    }

    private fun resources(status: MemoryStatus): TechnicalGadgetContent {
        val free = bytes(status.availableRamBytes)
        val uptime = duration(status.uptimeMillis)
        val caption = uptime?.let { context.getString(R.string.launcher_gadget_resources_uptime, it) } ?: unknown
        return TechnicalGadgetContent(
            value = free,
            caption = caption,
            fill = fillOf(status.totalRamBytes, status.availableRamBytes),
            description = context.getString(
                R.string.launcher_gadget_resources_description,
                context.getString(R.string.launcher_gadget_resources_ram, free),
                caption
            )
        )
    }

    private fun bytes(value: MetricValue<Long>): String =
        (value as? MetricValue.Known)?.let { Formatter.formatFileSize(context, it.value) } ?: unknown

    private fun duration(value: MetricValue<Long>): String? =
        (value as? MetricValue.Known)?.let { DateUtils.formatElapsedTime(it.value / MILLIS_PER_SECOND) }

    /**
     * Both figures must be known before a bar is drawn: a known total with an unknown free space would
     * otherwise render as a completely full device, which is a statement the app never measured.
     */
    private fun fillOf(total: MetricValue<Long>, available: MetricValue<Long>): Float? {
        val totalBytes = (total as? MetricValue.Known)?.value?.takeIf { it > 0L }
        val availableBytes = (available as? MetricValue.Known)?.value
        return if (totalBytes != null && availableBytes != null) {
            ((totalBytes - availableBytes).toFloat() / totalBytes).coerceIn(EMPTY_FILL, FULL_FILL)
        } else {
            null
        }
    }

    private fun transportLabelRes(transport: NetworkTransport): Int = when (transport) {
        NetworkTransport.WIFI -> R.string.launcher_tray_network_wifi
        NetworkTransport.CELLULAR -> R.string.launcher_tray_network_cellular
        NetworkTransport.ETHERNET -> R.string.launcher_tray_network_ethernet
        NetworkTransport.NONE -> R.string.launcher_tray_network_none
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val FULL_PERCENT = 100f
        const val EMPTY_FILL = 0f
        const val FULL_FILL = 1f
        const val LINE_BREAK = "\n"
    }
}
