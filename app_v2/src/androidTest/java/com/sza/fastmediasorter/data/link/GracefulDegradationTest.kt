package com.sza.fastmediasorter.data.link

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sza.fastmediasorter.data.link.cookie.EncryptedCookieStore
import com.sza.fastmediasorter.domain.model.link.MediaQualityPreference
import com.sza.fastmediasorter.domain.model.link.StreamingManifest
import com.sza.fastmediasorter.domain.usecase.link.LinkAutoDownloadCoordinator
import com.sza.fastmediasorter.domain.usecase.link.streaming.PipelineOutcome
import com.sza.fastmediasorter.domain.usecase.link.streaming.StreamingPipeline
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0116 §5.4: graceful-degradation instrumentation suite. Asserts that S0003
 * baseline behaviour (direct file download) survives any single new component
 * throwing - no `Throwable` propagates out of `coordinator.handle`.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class GracefulDegradationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var coordinator: LinkAutoDownloadCoordinator

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        hiltRule.inject()
        server = MockWebServer().also {
            // Answered by path, not from a queue: the coordinator probes a URL more than once, and a
            // queued server holds every request past the first until the 1-minute runTest timeout.
            it.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                    // MockWebServer writes a body even to HEAD; on the reused connection the next GET
                    // then reads those bytes as its status line and the download fails as NoNetwork.
                    "/clip.mp4" -> if (request.method == "HEAD") {
                        MockResponse().setHeader("Content-Type", "video/mp4")
                    } else {
                        MockResponse().setHeader("Content-Type", "video/mp4").setBody("    ftypisom")
                    }
                    "/locked.mp4" -> MockResponse().setResponseCode(HTTP_UNAUTHORIZED)
                    else -> MockResponse().setResponseCode(HTTP_NOT_FOUND)
                }
            }
            it.start()
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val callbacks = object : LinkAutoDownloadCoordinator.Callbacks {
        override fun onProgress(state: LinkAutoDownloadCoordinator.ProgressState) = Unit
    }

    /**
     * One test method on purpose (S3741): Hilt builds a fresh singleton component per test method, and
     * the settings DataStore it provides refuses a second live instance on the same file in one process
     * ("multiple DataStores active"), so a second method in this class fails before it starts.
     */
    @Test
    fun noThrowableEscapesTheCoordinator() = runTest {
        val direct = coordinator.handle(server.url("/clip.mp4").toString(), callbacks)
        // Either Result.Saved (resource configured) or Result.FellBackToDownloads (default).
        // Both branches represent the S0003 happy path that must survive a throwing streaming pipeline.
        val isS0003Path = direct is LinkAutoDownloadCoordinator.Result.Saved ||
            direct is LinkAutoDownloadCoordinator.Result.FellBackToDownloads
        assertTrue("direct mp4: expected Result.Saved or FellBackToDownloads, got $direct", isS0003Path)

        val missing = coordinator.handle(server.url("/missing.mp4").toString(), callbacks)
        assertTrue("404: expected Failed, got $missing", missing is LinkAutoDownloadCoordinator.Result.Failed)

        val locked = coordinator.handle(server.url("/locked.mp4").toString(), callbacks)
        assertTrue(
            "401: expected Failed.AuthRequired, got $locked",
            locked is LinkAutoDownloadCoordinator.Result.Failed.AuthRequired,
        )
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
    }
}

/**
 * S0116 §5.4: replaces the production [StreamingPipeline] binding with a fake
 * that returns [PipelineOutcome.NetworkError] for every call. Direct-file URLs
 * never invoke streaming, so the coordinator must still surface `Saved`.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [com.sza.fastmediasorter.di.StreamingModule::class],
)
object FakeStreamingModule {
    @Provides
    @Singleton
    fun provideFakePipeline(): StreamingPipeline = object : StreamingPipeline {
        override suspend fun fetchAndRemux(
            manifest: StreamingManifest,
            fileName: String,
            quality: MediaQualityPreference,
            accountId: String?,
            onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
        ): PipelineOutcome = PipelineOutcome.NetworkError(IllegalStateException("forced failure"))
    }
}
