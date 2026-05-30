package com.sza.fastmediasorter.data.link.nolegal

import android.content.Context
import com.sza.fastmediasorter.data.link.DirectFileExtractionStrategy
import com.sza.fastmediasorter.data.link.cookie.LinkDownloadSessionContext
import com.sza.fastmediasorter.domain.usecase.link.OpenResult
import com.sza.fastmediasorter.domain.usecase.link.ProbeResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * S0300 Phase 07: JVM-reachable branches of [YtDlpExtractionStrategy].
 *
 * The format-selection core runs entirely behind the Chaquopy Python bridge
 * (Python.getInstance / PyObject) and is not JVM-testable. The reachable surface is:
 * the probe extension short-circuit (pure [MediaMimeWhitelist] lookup) and both the probe
 * and open early-returns when the Chaquopy runtime reports unavailable. All collaborators
 * are mocked; the Python runtime is never started.
 */
class YtDlpExtractionStrategyTest {

    private lateinit var context: Context
    private lateinit var runtimeHolder: ChaquopyRuntimeHolder
    private lateinit var cookieWriter: CookieFileWriter
    private lateinit var direct: DirectFileExtractionStrategy
    private lateinit var sessionContext: LinkDownloadSessionContext
    private lateinit var strategy: YtDlpExtractionStrategy
    private val noProgress: (Long, Long?) -> Unit = { _, _ -> }

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        runtimeHolder = mockk()
        cookieWriter = mockk(relaxed = true)
        direct = mockk()
        sessionContext = mockk(relaxed = true)
        strategy = YtDlpExtractionStrategy(context, runtimeHolder, cookieWriter, direct, sessionContext)
    }

    @Test
    fun probe_directMediaExtension_notApplicable_withoutTouchingRuntime() = runTest {
        // .mp4 maps via MediaMimeWhitelist → short-circuit before runtime init.
        val result = strategy.probe("https://cdn.example/clip.mp4")
        assertEquals(ProbeResult.NotApplicable, result)
        verify(exactly = 0) { runtimeHolder.ensureInitialized() }
    }

    @Test
    fun probe_imageExtension_notApplicable() = runTest {
        val result = strategy.probe("https://cdn.example/pic.jpg")
        assertEquals(ProbeResult.NotApplicable, result)
        verify(exactly = 0) { runtimeHolder.ensureInitialized() }
    }

    @Test
    fun probe_nonMediaUrl_runtimeUnavailable_notApplicable() = runTest {
        every { runtimeHolder.ensureInitialized() } returns false
        val result = strategy.probe("https://youtube.com/watch?v=abc")
        assertEquals(ProbeResult.NotApplicable, result)
        verify { runtimeHolder.ensureInitialized() }
    }

    @Test
    fun open_runtimeUnavailable_notFound() = runTest {
        every { runtimeHolder.ensureInitialized() } returns false
        val result = strategy.open("https://youtube.com/watch?v=abc", noProgress)
        assertEquals("ytdlp_runtime_unavailable", (result as OpenResult.NotFound).reason)
    }

    @Test
    fun id_isYtdlp() {
        assertEquals("ytdlp", strategy.id)
    }
}
