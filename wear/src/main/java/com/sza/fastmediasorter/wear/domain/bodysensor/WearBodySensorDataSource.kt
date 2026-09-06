package com.sza.fastmediasorter.wear.domain.bodysensor

import kotlinx.coroutines.flow.Flow

/**
 * One foreground heart-rate measurement, and the reason when there cannot be one.
 *
 * The name deliberately differs from `WearHealthDataSource`, which means the health of the DEVICE -
 * thermal state, battery detail, uptime, why the process last died - and carries no body reading at all.
 * S2457 §3.2 fixes the distinction as a naming constraint rather than leaving it to the reader, because
 * the two would otherwise be told apart only by their package.
 *
 * Implementations live one per flavor source set with a `@Binds` module beside each
 * (`dev/FLAVOR_DEVELOPMENT_RULES.md` Rule 8): `standard` withholds the capability, `noLegal` measures
 * through Health Services. Nothing in this file may reference the Health Services library - it is on the
 * `noLegal` classpath alone, so a shared file that named one of its types would not compile in
 * `standard`. That is why the contract speaks in [BodySensorReading] rather than in library types.
 */
interface WearBodySensorDataSource {

    /**
     * Whether a measurement could start right now, asked without starting one.
     *
     * Returns [BodySensorReading.Idle] when the path is open, or [BodySensorReading.Unavailable] with the
     * reason. Never returns a reading: answering "can I" must not cost the user a sensor session.
     */
    suspend fun availability(): BodySensorReading

    /**
     * The measurement itself, as a COLD flow: nothing is registered until somebody collects, and the
     * sensor is released when the last collector leaves - cancellation included.
     *
     * That teardown is a property of this contract, not a habit of one caller, because S2457 §11
     * criterion 2 requires the measurement to end when the diagnostic is closed and §3.2 confines the
     * subscription to an open screen. A caller therefore never needs a stop() and there is none to forget.
     *
     * Emits [BodySensorReading.Measuring] once registration is accepted, then a
     * [BodySensorReading.HeartRate] per sample, or a terminal [BodySensorReading.Unavailable].
     */
    fun measure(): Flow<BodySensorReading>
}
