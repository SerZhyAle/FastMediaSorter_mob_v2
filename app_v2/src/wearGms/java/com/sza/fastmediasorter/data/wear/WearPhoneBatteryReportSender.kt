package com.sza.fastmediasorter.data.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.domain.repository.PhoneBatteryReportSender
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.service.WearDataLayerPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val PERCENT_SCALE = 100
private const val UNKNOWN_BATTERY_FIELD = -1

/** The wire shape: the watch's PhoneBatteryReportCodec reads exactly these three keys (S3764). */
private data class PhoneBatteryPayload(
    @SerializedName("percent") val percent: Int,
    @SerializedName("isCharging") val isCharging: Boolean,
    @SerializedName("timestampMs") val timestampMs: Long
)

/**
 * S3764: publishes the paired phone's battery charge to the watch face on every change.
 *
 * Shaped after PushWearClockStyleUseCase and gated the same way: it does not wait for a connected
 * watch, because the report rides a Data Item the Data Layer delivers (or re-delivers on reconnect)
 * by itself. The payload carries its own timestamp, which is what lets the watch empty the bar when
 * the report goes stale instead of showing a charge the link can no longer vouch for.
 */
@Singleton
class WearPhoneBatteryReportSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wearableRepository: WearableDataLayerRepository,
    private val settingsRepository: SettingsRepository,
    private val gson: Gson
) : PhoneBatteryReportSender {

    /** Gated on enableWearCompanion; re-enabling it republishes the current charge at once. */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAndPush(scope: CoroutineScope): Job = scope.launch {
        settingsRepository.getSettings()
            .map { it.enableWearCompanion }
            .distinctUntilChanged()
            .flatMapLatest { enabled ->
                if (enabled) {
                    batteryReports()
                } else {
                    emptyFlow()
                }
            }
            // The collector lives for the whole process; a throw escaping it would reach the default
            // uncaught-exception handler, a process-wide surface this feature must not take down.
            .catch { Timber.w(it, "Wear phone battery: report stream failed") }
            .collectLatest { payload ->
                push(payload).onFailure { Timber.w(it, "Wear phone battery not pushed") }
            }
    }

    /**
     * The sticky ACTION_BATTERY_CHANGED broadcast hands back the current charge the moment the
     * receiver registers (PowerStateObserver's idiom), so a re-enabled companion pushes a fresh
     * report without waiting for the next battery tick. The distinctUntilChanged below is the
     * whole coalescing rule - at most one send per percent or charging change, no periodic timer
     * (strategic §3.2's AOD budget).
     */
    private fun batteryReports(): Flow<PhoneBatteryPayload> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                val payload = intent?.let { readPayload(it) }
                if (payload != null) trySend(payload)
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        sticky?.let { readPayload(it) }?.let { trySend(it) }
        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }.distinctUntilChanged()

    /** Null when the platform reports no usable level or scale, rather than a fabricated report. */
    private fun readPayload(intent: Intent): PhoneBatteryPayload? {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, UNKNOWN_BATTERY_FIELD)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, UNKNOWN_BATTERY_FIELD)
        if (level < 0 || scale <= 0) return null
        return PhoneBatteryPayload(
            percent = level * PERCENT_SCALE / scale,
            isCharging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, UNKNOWN_BATTERY_FIELD) > 0,
            timestampMs = System.currentTimeMillis()
        )
    }

    private suspend fun push(payload: PhoneBatteryPayload): Result<Unit> = runCatching {
        val envelope = WearEventEnvelope(
            eventType = WearDataLayerPaths.EVENT_PHONE_BATTERY,
            sentAt = payload.timestampMs,
            data = gson.toJson(payload).toByteArray(Charsets.UTF_8)
        )
        Timber.d("S3764: phone battery sent - percent %s charging %s", payload.percent, payload.isCharging)
        wearableRepository.putEnvelopeDataItem(WearDataLayerPaths.PHONE_BATTERY, envelope)
    }.onFailure { it.rethrowIfCancellation() }
}
