package com.sza.fastmediasorter.domain.model.devicestatus

/**
 * S1178: one device metric's value, or the explicit absence of one.
 *
 * A metric the device refuses to report is [Unknown], never a zero. "No free memory" and "free memory
 * could not be read" are different facts about the device, and a gadget that renders them the same way
 * states the first while meaning the second.
 */
sealed interface MetricValue<out T> {

    data class Known<T>(val value: T) : MetricValue<T>

    data object Unknown : MetricValue<Nothing>
}
