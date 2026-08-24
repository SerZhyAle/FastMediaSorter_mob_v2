package com.sza.fastmediasorter.data.networkmonitor

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.domain.networkmonitor.HostProbe
import com.sza.fastmediasorter.domain.networkmonitor.HostProbeResult
import com.sza.fastmediasorter.domain.networkmonitor.HostProbeUnavailability
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import kotlin.math.ceil

/**
 * S1617: the primary reachability mechanism - the system `ping` binary, run as a child process.
 *
 * Measured 2026-08-18 from the app's own SELinux domain (strategic §6.1): this works, and it is the
 * only way an app that may not open a raw socket gets a real ICMP echo, because the capability
 * belongs to the binary rather than to the calling process. The same measurement showed it is
 * firmware-dependent, which is why every launch failure becomes
 * [HostProbeUnavailability.MECHANISM_UNAVAILABLE] and never "the host did not answer".
 */
class SystemPingHostProbe @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : HostProbe {

    private sealed interface Launch {
        data class Started(val process: Process) : Launch
        data class Refused(val detail: String) : Launch
    }

    override suspend fun probe(host: String, timeoutMillis: Long, ttl: Int?): HostProbeResult =
        withContext(ioDispatcher) {
            when (val launch = launch(buildCommand(host, timeoutMillis, ttl))) {
                is Launch.Refused ->
                    HostProbeResult.NotMeasurable(HostProbeUnavailability.MECHANISM_UNAVAILABLE, launch.detail)
                is Launch.Started -> readAnswer(launch.process, timeoutMillis)
            }
        }

    private fun launch(command: List<String>): Launch =
        try {
            Launch.Started(ProcessBuilder(command).redirectErrorStream(true).start())
        } catch (e: IOException) {
            Timber.w(e, "ping binary could not be started")
            Launch.Refused(e.javaClass.simpleName)
        } catch (e: SecurityException) {
            Timber.w(e, "ping refused by policy")
            Launch.Refused(e.javaClass.simpleName)
        }

    /**
     * The read is interruptible so cancellation reaches a blocking stream read, and the process is
     * destroyed on every exit path: a ping that outlives its caller keeps a file descriptor open and
     * goes on waking the radio for the rest of its count.
     */
    private suspend fun readAnswer(process: Process, timeoutMillis: Long): HostProbeResult =
        try {
            val output = withTimeoutOrNull(timeoutMillis + PROCESS_GRACE_MILLIS) {
                runInterruptible { process.inputStream.bufferedReader().use { reader -> reader.readText() } }
            }
            output?.let { PingOutputParser.parse(it) }
                ?: HostProbeResult.NotMeasurable(
                    HostProbeUnavailability.MECHANISM_UNAVAILABLE,
                    "ping did not finish within the budget",
                )
        } finally {
            process.destroy()
        }

    private fun buildCommand(host: String, timeoutMillis: Long, ttl: Int?): List<String> {
        val deadlineSeconds = ceil(timeoutMillis / MILLIS_PER_SECOND).toInt().coerceAtLeast(1)
        val command = mutableListOf(binaryFor(host), "-c", "1", "-W", deadlineSeconds.toString())
        if (ttl != null) {
            command += listOf("-t", ttl.toString())
        }
        command += host
        return command
    }

    /** A literal IPv6 address carries colons and the IPv4 binary refuses it outright. */
    private fun binaryFor(host: String): String =
        if (host.contains(':')) PING6_BINARY else PING_BINARY

    private companion object {
        const val PING_BINARY = "/system/bin/ping"
        const val PING6_BINARY = "/system/bin/ping6"
        const val MILLIS_PER_SECOND = 1000.0

        /** `-W` bounds the wait for a reply; this covers process start and teardown around it. */
        const val PROCESS_GRACE_MILLIS = 1500L
    }
}
