package com.sza.fastmediasorter.domain.model.networkmonitor

/** S1433: which manual operation produced a history row. */
enum class NetworkMeasurementKind {
    SPEED_TEST,
    RESOURCE_CHECK,
    EXTERNAL_IP,
    SUBNET_SCAN,
    PING,
    TRACEROUTE,
}

/**
 * S1433: one finished manual measurement, as the rest of the app sees it.
 *
 * Nothing observed automatically reaches this type. Strategic §3.2 keeps the live charts and the satellite
 * list in memory for the lifetime of the open screen, so only an operation the user started by hand
 * produces one of these.
 *
 * [resultText] is the whole human-readable outcome and the only free-form field, which is what keeps the
 * privacy promise checkable: an external-IP check records that it succeeded and never the address it saw,
 * and there is no field anywhere here that could hold a secret.
 *
 * The three numeric fields are null for a kind that does not measure them - a subnet scan has no
 * throughput, and zero would read as a measured zero rather than "not part of this measurement".
 *
 * [id] is 0 for a measurement not yet stored; the store assigns the real one.
 */
data class NetworkMeasurement(
    val id: Long = 0L,
    val takenAtMillis: Long,
    val kind: NetworkMeasurementKind,
    val networkLabel: String,
    val downMbps: Double? = null,
    val upMbps: Double? = null,
    val latencyMs: Long? = null,
    val resultText: String,
    val succeeded: Boolean,
)
