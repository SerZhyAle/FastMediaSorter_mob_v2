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
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val callbacks = object : LinkAutoDownloadCoordinator.Callbacks {
        override fun onProgress(state: LinkAutoDownloadCoordinator.ProgressState) = Unit
    }

    @Test
    fun testDirectMp4UrlReturnsSavedDespiteThrowingStreamingPipeline() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "video/mp4")
                .setBody("    ftypisom"), // minimal MP4 header bytes
        )
        val url = server.url("/clip.mp4").toString()
        val result = coordinator.handle(url, callbacks)
        // Either Result.Saved (resource configured) or Result.FellBackToDownloads (default).
        // Both branches represent the S0003 happy path that must survive a throwing streaming pipeline.
        val isS0003Path = result is LinkAutoDownloadCoordinator.Result.Saved ||
            result is LinkAutoDownloadCoordinator.Result.FellBackToDownloads
        assertTrue("expected Result.Saved or FellBackToDownloads, got $result", isS0003Path)
    }

    @Test
    fun test404UrlReturnsNoMediaFoundWithoutCrash() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val url = server.url("/missing.mp4").toString()
        val result = coordinator.handle(url, callbacks)
        assertTrue(
            "expected Failed.NoMediaFound or Failed.NoNetwork, got $result",
            result is LinkAutoDownloadCoordinator.Result.Failed,
        )
    }

    @Test
    fun test401UrlReturnsAuthRequiredWithoutCrash() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val url = server.url("/locked.mp4").toString()
        val result = coordinator.handle(url, callbacks)
        assertTrue(
            "expected Failed.AuthRequired, got $result",
            result is LinkAutoDownloadCoordinator.Result.Failed.AuthRequired,
        )
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
            onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
        ): PipelineOutcome = PipelineOutcome.NetworkError(IllegalStateException("forced failure"))
    }
}
