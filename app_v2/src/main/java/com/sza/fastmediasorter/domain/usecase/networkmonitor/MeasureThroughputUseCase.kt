package com.sza.fastmediasorter.domain.usecase.networkmonitor

import android.content.Context
import android.net.ConnectivityManager
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMeasurement
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMeasurementKind
import com.sza.fastmediasorter.domain.repository.NetworkMeasurementHistoryRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.NetworkSpeedTestUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlin.system.measureTimeMillis

/** S1433: which side of the app the throughput question is being asked about. */
sealed interface ThroughputMode {

    /** Against a public endpoint, to judge the internet link itself. */
    data object Internet : ThroughputMode

    /** Against one saved resource, to judge the path the app actually uses for that resource. */
    data class Resource(val resourceId: Long) : ThroughputMode
}

/** S1433: how a throughput measurement is progressing, and how it ended. */
sealed interface ThroughputState {

    /**
     * The active network charges for traffic and the caller did not say to go ahead. Strategic §3.2 makes
     * this a state rather than a silent skip: a speed test spends real money on a mobile plan, so the
     * decision belongs to the user, before any bytes move.
     */
    data object MeteredNetwork : ThroughputState

    data class Progress(val fraction: Float) : ThroughputState

    data class Complete(val downMbps: Double, val upMbps: Double?) : ThroughputState

    data object ResourceMissing : ThroughputState

    data class Failed(val reason: String) : ThroughputState
}

/**
 * S1433: measures download and upload throughput in the two modes strategic §6.6 asks for.
 *
 * Resource mode delegates to [NetworkSpeedTestUseCase] rather than repeating it - that class already knows
 * how to talk to every supported protocol, and a second implementation would drift from it silently.
 *
 * Internet mode talks to Cloudflare's key-free endpoint over [HttpURLConnection] rather than the shared
 * OkHttp client, for the same reason the other Monitor probes do: the debug Chucker interceptor buffers
 * every response body into its database, and here that body is a multi-megabyte payload.
 *
 * Both modes are bounded twice over, by bytes and by time, so a slow link ends the test instead of running
 * until the user gives up, and both stop promptly when the flow is cancelled.
 */
class MeasureThroughputUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val networkSpeedTestUseCase: NetworkSpeedTestUseCase,
    private val resourceRepository: ResourceRepository,
    private val historyRepository: NetworkMeasurementHistoryRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * [allowMetered] is the caller's explicit go-ahead after it showed the warning; without it a metered
     * network ends the flow at [ThroughputState.MeteredNetwork] and no traffic is generated.
     */
    operator fun invoke(
        mode: ThroughputMode,
        networkLabel: String,
        allowMetered: Boolean = false,
        byteBudget: Long = DEFAULT_BYTE_BUDGET,
        timeBudgetMs: Long = DEFAULT_TIME_BUDGET_MS,
    ): Flow<ThroughputState> = flow {
        if (isMetered() && !allowMetered) {
            emit(ThroughputState.MeteredNetwork)
            return@flow
        }
        val state = when (mode) {
            is ThroughputMode.Internet -> measureInternet(byteBudget, timeBudgetMs) { emit(it) }
            is ThroughputMode.Resource -> measureResource(mode.resourceId)
        }
        historyRepository.record(state.toMeasurement(mode, networkLabel))
        emit(state)
    }.flowOn(ioDispatcher)

    private fun isMetered(): Boolean =
        context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: false

    private suspend fun measureInternet(
        byteBudget: Long,
        timeBudgetMs: Long,
        onProgress: suspend (ThroughputState) -> Unit,
    ): ThroughputState = try {
        onProgress(ThroughputState.Progress(0f))
        val down = measureDownload(byteBudget, timeBudgetMs)
        onProgress(ThroughputState.Progress(HALFWAY))
        val up = measureUpload(byteBudget, timeBudgetMs)
        ThroughputState.Complete(down, up)
    } catch (e: IOException) {
        Timber.d("Throughput: internet test failed (%s)", e.javaClass.simpleName)
        ThroughputState.Failed(e.message.orEmpty())
    }

    /** Reads until either budget runs out, so a slow link reports what it managed rather than failing. */
    private suspend fun measureDownload(byteBudget: Long, timeBudgetMs: Long): Double {
        val url = URL("https://$SPEED_TEST_HOST/__down?bytes=$byteBudget")
        var read = 0L
        val elapsed = measureTimeMillis {
            val connection = (url.openConnection() as HttpURLConnection).applyBudgets(timeBudgetMs)
            connection.useConnection { open ->
                val buffer = ByteArray(CHUNK_BYTES)
                val deadline = System.currentTimeMillis() + timeBudgetMs
                open.inputStream.use { stream ->
                    var count = stream.read(buffer)
                    while (count >= 0 && read < byteBudget && System.currentTimeMillis() < deadline) {
                        currentCoroutineContext().ensureActive()
                        read += count
                        count = stream.read(buffer)
                    }
                }
            }
        }
        return megabitsPerSecond(read, elapsed)
    }

    private suspend fun measureUpload(byteBudget: Long, timeBudgetMs: Long): Double {
        val url = URL("https://$SPEED_TEST_HOST/__up")
        val payload = ByteArray(CHUNK_BYTES)
        var written = 0L
        val elapsed = measureTimeMillis {
            val connection = (url.openConnection() as HttpURLConnection).applyBudgets(timeBudgetMs).apply {
                requestMethod = "POST"
                doOutput = true
                setFixedLengthStreamingMode(byteBudget)
            }
            connection.useConnection { open ->
                val deadline = System.currentTimeMillis() + timeBudgetMs
                open.outputStream.use { stream ->
                    while (written < byteBudget && System.currentTimeMillis() < deadline) {
                        currentCoroutineContext().ensureActive()
                        val size = minOf(payload.size.toLong(), byteBudget - written).toInt()
                        stream.write(payload, 0, size)
                        written += size
                    }
                }
                open.responseCode
            }
        }
        return megabitsPerSecond(written, elapsed)
    }

    /**
     * Resource mode owns no measurement logic of its own - it maps the existing use case's terminal status
     * onto this flow's vocabulary and nothing else.
     */
    private suspend fun measureResource(resourceId: Long): ThroughputState {
        val resource = resourceRepository.getResourceById(resourceId)
            ?: return ThroughputState.ResourceMissing
        var outcome: ThroughputState = ThroughputState.Failed("")
        networkSpeedTestUseCase.runSpeedTest(resource).collect { status ->
            outcome = when (status) {
                is NetworkSpeedTestUseCase.SpeedTestStatus.Complete ->
                    ThroughputState.Complete(status.result.readSpeedMbps, status.result.writeSpeedMbps)
                is NetworkSpeedTestUseCase.SpeedTestStatus.MeasurementUnavailable ->
                    ThroughputState.Failed(status.reason)
                is NetworkSpeedTestUseCase.SpeedTestStatus.Error -> ThroughputState.Failed(status.message)
                is NetworkSpeedTestUseCase.SpeedTestStatus.Progress -> outcome
            }
        }
        return outcome
    }

    private fun ThroughputState.toMeasurement(
        mode: ThroughputMode,
        networkLabel: String,
    ): NetworkMeasurement {
        val complete = this as? ThroughputState.Complete
        return NetworkMeasurement(
            takenAtMillis = System.currentTimeMillis(),
            kind = NetworkMeasurementKind.SPEED_TEST,
            networkLabel = networkLabel,
            downMbps = complete?.downMbps,
            upMbps = complete?.upMbps,
            resultText = when (this) {
                is ThroughputState.Complete -> "${mode.label}: measured"
                is ThroughputState.ResourceMissing -> "${mode.label}: resource no longer exists"
                is ThroughputState.Failed -> "${mode.label}: ${reason.ifEmpty { "no measurement" }}"
                else -> mode.label
            },
            succeeded = complete != null,
        )
    }

    private companion object {

        /** Cloudflare's public speed endpoint - no API key, no account, no per-request quota. */
        const val SPEED_TEST_HOST = "speed.cloudflare.com"

        const val DEFAULT_BYTE_BUDGET = 10L * 1024 * 1024
        const val DEFAULT_TIME_BUDGET_MS = 15_000L
        const val CHUNK_BYTES = 32 * 1024
        const val HALFWAY = 0.5f
        const val BITS_PER_BYTE = 8.0
        const val BITS_PER_MEGABIT = 1_000_000.0
        const val MILLIS_PER_SECOND = 1_000.0

        val ThroughputMode.label: String
            get() = when (this) {
                is ThroughputMode.Internet -> "Internet"
                is ThroughputMode.Resource -> "Resource"
            }

        fun megabitsPerSecond(bytes: Long, elapsedMs: Long): Double = if (elapsedMs <= 0L) {
            0.0
        } else {
            bytes * BITS_PER_BYTE / BITS_PER_MEGABIT / (elapsedMs / MILLIS_PER_SECOND)
        }

        fun HttpURLConnection.applyBudgets(timeBudgetMs: Long): HttpURLConnection = apply {
            connectTimeout = timeBudgetMs.toInt()
            readTimeout = timeBudgetMs.toInt()
        }

        inline fun <T> HttpURLConnection.useConnection(block: (HttpURLConnection) -> T): T = try {
            block(this)
        } finally {
            disconnect()
        }
    }
}
