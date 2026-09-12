package com.sza.fastmediasorter.wear.bodysensor

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.HealthServicesException
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.getCapabilities
import com.sza.fastmediasorter.wear.domain.bodysensor.BodySensorReading
import com.sza.fastmediasorter.wear.domain.bodysensor.BodySensorUnavailableReason
import com.sza.fastmediasorter.wear.domain.bodysensor.WearBodySensorDataSource
import com.sza.fastmediasorter.wear.domain.bodysensor.heartRatePermission
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * One foreground heart-rate reading through the public Health Services `MeasureClient`, and a named
 * reason whenever there cannot be one.
 *
 * Only the public API is used. S2457 §3.2 rules out Samsung's signature-only ECG, BIA and raw sensor
 * types: they are unreachable to a third-party build, and a diagnostic that appeared to reach them would
 * be lying about the very thing it exists to report.
 *
 * The class lives in `noLegal` alone because the library and both permissions do (S2457 ADR-1). It is
 * still compiled against this module's floor of API 28 while Health Services declares 30 - the `noLegal`
 * manifest carries `tools:overrideLibrary` rather than raising the floor - so every entry point checks
 * `SDK_INT` before a single Health Services type is touched, and API 28 and 29 get
 * [BodySensorUnavailableReason.PLATFORM_TOO_OLD] instead of a `NoClassDefFoundError`.
 */
class HealthServicesBodySensorDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) : WearBodySensorDataSource {

    override suspend fun availability(): BodySensorReading {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            unavailable(BodySensorUnavailableReason.PLATFORM_TOO_OLD)
        } else {
            supportedAvailability()
        }
    }

    override fun measure(): Flow<BodySensorReading> = flow {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            emit(unavailable(BodySensorUnavailableReason.PLATFORM_TOO_OLD))
        } else {
            emitAll(supportedMeasure())
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun supportedAvailability(): BodySensorReading {
        val reason = blockingReason()
        return if (reason == null) BodySensorReading.Idle else unavailable(reason)
    }

    /**
     * The preconditions are re-checked here rather than trusted from an earlier [availability] call: the
     * user may grant or revoke the permission between opening the screen and pressing the button, and
     * this flow is cold, so "earlier" can be arbitrarily long ago.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun supportedMeasure(): Flow<BodySensorReading> = flow {
        val reason = blockingReason()
        if (reason == null) {
            emitAll(samples())
        } else {
            emit(unavailable(reason))
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun blockingReason(): BodySensorUnavailableReason? = when {
        !isHeartRatePermissionGranted() -> BodySensorUnavailableReason.PERMISSION_DENIED
        else -> capabilityReason()
    }

    /**
     * Asks the watch, rather than assuming from the model, whether it measures heart rate at all. A watch
     * that does not list the data type has no sensor to wait on, so the screen says so immediately
     * instead of running the measurement window out.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun capabilityReason(): BodySensorUnavailableReason? = try {
        val supported = HealthServices.getClient(context).measureClient
            .getCapabilities()
            .supportedDataTypesMeasure
        if (DataType.HEART_RATE_BPM in supported) {
            null
        } else {
            BodySensorUnavailableReason.NO_HARDWARE
        }
    } catch (e: HealthServicesException) {
        Timber.w(e, "Heart-rate capabilities could not be read from Health Services")
        BodySensorUnavailableReason.MEASUREMENT_FAILED
    }

    /**
     * The measurement proper. Registration happens on collection and the callback is unregistered in
     * [awaitClose], so the sensor is released the moment the diagnostic is closed or the collecting scope
     * is cancelled - S2457 §11 criterion 2, made a property of the flow rather than of its caller.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun samples(): Flow<BodySensorReading> = callbackFlow {
        val measureClient = HealthServices.getClient(context).measureClient
        val answered = AtomicBoolean(false)
        val callback = measureCallback(answered)

        // registerMeasureCallback reports an ordinary refusal through onRegistrationFailed, but it still
        // throws when the service itself cannot be reached. Uncaught, that breaks the collector instead of
        // producing a reason - which would make this flow violate the contract its own KDoc states.
        val registered = try {
            measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
            true
        } catch (e: HealthServicesException) {
            Timber.w(e, "Health Services could not be reached to start a heart-rate measurement")
            trySend(unavailable(BodySensorUnavailableReason.MEASUREMENT_FAILED))
            close()
            false
        }

        // Guards SILENCE only. A registration that was accepted and then delivered nothing leaves the
        // screen on "measuring" forever, which reads as a hung app rather than as a failed reading - but a
        // watch that already said it is off the wrist has answered, and must not have that answer
        // overwritten 30 seconds later by a vaguer one.
        val timeout = launch {
            delay(MEASUREMENT_TIMEOUT_MS)
            if (!answered.get()) {
                trySend(unavailable(BodySensorUnavailableReason.MEASUREMENT_TIMED_OUT))
                close()
            }
        }

        awaitClose {
            timeout.cancel()
            if (registered) {
                // The async form, not the suspend extension: awaitClose's block is not a suspend context,
                // and the unregister must be issued during teardown rather than deferred to a later scope.
                measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback)
            }
        }
    }

    /**
     * [answered] records that the user has been told something actionable - a reading, or the fact that
     * the watch is off the wrist. It is the timeout's only reason to stay silent, so both writers set it.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun ProducerScope<BodySensorReading>.measureCallback(
        answered: AtomicBoolean
    ): MeasureCallback = object : MeasureCallback {

        override fun onRegistered() {
            trySend(BodySensorReading.Measuring)
        }

        override fun onRegistrationFailed(throwable: Throwable) {
            Timber.w(throwable, "Health Services refused the heart-rate registration")
            trySend(unavailable(BodySensorUnavailableReason.MEASUREMENT_FAILED))
            close()
        }

        /**
         * Only off-body is reported. `ACQUIRING` and a transient `UNAVAILABLE` are the normal opening
         * seconds of any reading and recover on their own; treating them as refusals would replace a
         * working measurement with an error message a second before the first sample arrives.
         */
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            if (availability == DataTypeAvailability.UNAVAILABLE_DEVICE_OFF_BODY) {
                answered.set(true)
                trySend(unavailable(BodySensorUnavailableReason.SENSOR_OFF_BODY))
            }
        }

        override fun onDataReceived(data: DataPointContainer) {
            data.getData(DataType.HEART_RATE_BPM).forEach { point ->
                answered.set(true)
                trySend(BodySensorReading.HeartRate(point.value.roundToInt()))
            }
        }
    }

    private fun isHeartRatePermissionGranted(): Boolean = ContextCompat.checkSelfPermission(
        context,
        heartRatePermission()
    ) == PackageManager.PERMISSION_GRANTED

    private fun unavailable(reason: BodySensorUnavailableReason): BodySensorReading =
        BodySensorReading.Unavailable(reason)

    private companion object {
        const val MEASUREMENT_TIMEOUT_MS = 30_000L
    }
}
