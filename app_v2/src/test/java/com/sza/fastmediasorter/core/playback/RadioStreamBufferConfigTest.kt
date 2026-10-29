package com.sza.fastmediasorter.core.playback

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * The smart-buffering loader policy must tell a lost link from the server's verdict: a 404/410 from a
 * dead channel used to count as connectivity and was retried forever, so the player never reached its
 * error path.
 */
@RunWith(RobolectricTestRunner::class)
// A bare Application: the real one's startup copies the stored setting (off) into the same
// SharedPreferences mirror asynchronously, which raced the smart flag this test sets.
@Config(sdk = [34], application = Application::class)
class RadioStreamBufferConfigTest {

    private lateinit var context: Context
    private lateinit var policy: LoadErrorHandlingPolicy
    private val dataSpec = DataSpec(Uri.parse("https://radio.example/stream"))

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        RadioStreamBufferConfig.syncMirror(context, smart = true)
        policy = RadioStreamBufferConfig.createLoadErrorHandlingPolicy(context)
    }

    @Test
    fun `a 404 gives up after the stock threshold`() {
        assertGivesUpAfterStockThreshold(httpStatus(404))
    }

    @Test
    fun `a 410 gives up after the stock threshold`() {
        assertGivesUpAfterStockThreshold(httpStatus(410))
    }

    @Test
    fun `a 4xx wrapped in another exception still gives up`() {
        assertGivesUpAfterStockThreshold(IOException("wrapped", httpStatus(404)))
    }

    @Test
    fun `a 503 keeps the silent reconnect past the stock threshold`() {
        assertKeepsReconnecting(httpStatus(503))
    }

    @Test
    fun `a 408 keeps the silent reconnect`() {
        assertKeepsReconnecting(httpStatus(408))
    }

    @Test
    fun `a 429 keeps the silent reconnect`() {
        assertKeepsReconnecting(httpStatus(429))
    }

    @Test
    fun `a dropped connection keeps the silent reconnect`() {
        val dropped = HttpDataSource.HttpDataSourceException(
            SocketTimeoutException("read timed out"),
            dataSpec,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            HttpDataSource.HttpDataSourceException.TYPE_READ,
        )
        assertKeepsReconnecting(dropped)
    }

    @Test
    fun `the loader may retry indefinitely only in smart mode`() {
        assertEquals(Int.MAX_VALUE, policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA))

        RadioStreamBufferConfig.syncMirror(context, smart = false)

        assertNotEquals(Int.MAX_VALUE, policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA))
    }

    private fun assertGivesUpAfterStockThreshold(error: IOException) {
        val threshold = DefaultLoadErrorHandlingPolicy.DEFAULT_MIN_LOADABLE_RETRY_COUNT
        for (attempt in 1..threshold) {
            assertNotEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(errorInfo(error, attempt)))
        }
        assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(errorInfo(error, threshold + 1)))
    }

    private fun assertKeepsReconnecting(error: IOException) {
        assertEquals(
            RadioStreamBufferConfig.BASE_RETRY_DELAY_MS,
            policy.getRetryDelayMsFor(errorInfo(error, 1)),
        )
        assertEquals(
            RadioStreamBufferConfig.MAX_RETRY_DELAY_MS,
            policy.getRetryDelayMsFor(errorInfo(error, FAR_PAST_THRESHOLD)),
        )
    }

    private fun httpStatus(code: Int): HttpDataSource.InvalidResponseCodeException =
        HttpDataSource.InvalidResponseCodeException(code, null, null, emptyMap(), dataSpec, ByteArray(0))

    private fun errorInfo(error: IOException, errorCount: Int) = LoadErrorHandlingPolicy.LoadErrorInfo(
        LoadEventInfo(LoadEventInfo.getNewId(), dataSpec, 0L),
        MediaLoadData(C.DATA_TYPE_MEDIA),
        error,
        errorCount,
    )

    private companion object {
        const val FAR_PAST_THRESHOLD = 50
    }
}
