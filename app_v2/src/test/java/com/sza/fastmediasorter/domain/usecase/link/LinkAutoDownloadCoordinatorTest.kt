package com.sza.fastmediasorter.domain.usecase.link

import com.sza.fastmediasorter.data.link.LinkDownloadWriter
import com.sza.fastmediasorter.data.link.cookie.EncryptedCookieStore
import com.sza.fastmediasorter.data.link.cookie.LinkDownloadSessionContext
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.link.streaming.StreamingPipeline
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.net.HttpCookie
import java.time.Instant

/**
 * S0176: Focused coordinator tests for eTLD+1 session-host resolution and markLastUsed
 * bookkeeping. Tests go through the public [LinkAutoDownloadCoordinator.handle] API and
 * observe side effects on [LinkDownloadSessionContext] and [AuthSessionRepository].
 */
class LinkAutoDownloadCoordinatorTest {

    private val settingsRepository: SettingsRepository = mockk()
    private val registry: LinkExtractionRegistry = mockk()
    private val writer: LinkDownloadWriter = mockk()
    private val streamingPipeline: StreamingPipeline = mockk()
    private val authSessionRepository: AuthSessionRepository = mockk(relaxed = true)
    private val sessionContext: LinkDownloadSessionContext = mockk(relaxed = true)
    private val cookieStore: EncryptedCookieStore = mockk()

    private val noOpCallbacks = object : LinkAutoDownloadCoordinator.Callbacks {
        override fun onProgress(state: LinkAutoDownloadCoordinator.ProgressState) = Unit
    }

    private lateinit var coordinator: LinkAutoDownloadCoordinator

    @Before
    fun setUp() {
        every { settingsRepository.getSettings() } returns flowOf(AppSettings())
        every { registry.ordered() } returns emptyList()
        coordinator = LinkAutoDownloadCoordinator(
            settingsRepository = settingsRepository,
            registry = registry,
            writer = writer,
            streamingPipeline = streamingPipeline,
            authSessionRepository = authSessionRepository,
            sessionContext = sessionContext,
            cookieStore = cookieStore,
        )
    }

    @Test
    fun exact_match_precedes_registrable_fallback() = runTest {
        val cookies = listOf(HttpCookie("sessionid", "exactcookie"))
        val candidateEntry = EncryptedCookieStore.AccountEntry(
            accountId = "acc1", displayName = "Test",
            savedAt = Instant.now(), lastUsedAt = null, cookieCount = 3,
        )
        // Exact host has cookies — must win without reaching listAllAccounts.
        every { cookieStore.loadForAccount("instagram.com", "acc1") } returns cookies
        every { cookieStore.listAllAccounts() } returns listOf("www.instagram.com" to candidateEntry)

        coordinator.handle("https://instagram.com/p/test", noOpCallbacks, "acc1")

        // sessionContext set with exact host, not any eTLD+1 fallback host.
        verify { sessionContext.set("instagram.com", cookies) }
    }

    @Test
    fun registrable_fallback_binds_persisted_base_host() = runTest {
        val cookies = listOf(HttpCookie("sessionid", "basecookie"))
        val baseEntry = EncryptedCookieStore.AccountEntry(
            accountId = "acc1", displayName = "Test",
            savedAt = Instant.now(), lastUsedAt = null, cookieCount = 5,
        )
        // Exact www-host has no cookies.
        every { cookieStore.loadForAccount("www.instagram.com", "acc1") } returns emptyList()
        // eTLD+1 fallback finds the base-domain stored account.
        every { cookieStore.listAllAccounts() } returns listOf("instagram.com" to baseEntry)
        // Cookies loaded from the resolved stored host.
        every { cookieStore.loadForAccount("instagram.com", "acc1") } returns cookies

        coordinator.handle("https://www.instagram.com/p/test", noOpCallbacks, "acc1")

        // sessionContext must receive the resolved stored host, not the original URL host.
        verify { sessionContext.set("instagram.com", cookies) }
    }

    @Test
    fun successful_account_bound_download_marks_last_used_for_matched_host() = runTest {
        val cookies = listOf(HttpCookie("sessionid", "basecookie"))
        val baseEntry = EncryptedCookieStore.AccountEntry(
            accountId = "acc1", displayName = "Test",
            savedAt = Instant.now(), lastUsedAt = null, cookieCount = 5,
        )
        val strategy = mockk<UrlExtractionStrategy>()
        val streamBody = mockk<InputStream>(relaxed = true)
        // eTLD+1 fallback resolves "instagram.com" for request host "www.instagram.com".
        every { cookieStore.loadForAccount("www.instagram.com", "acc1") } returns emptyList()
        every { cookieStore.listAllAccounts() } returns listOf("instagram.com" to baseEntry)
        every { cookieStore.loadForAccount("instagram.com", "acc1") } returns cookies
        // Strategy pipeline returns a downloadable stream.
        every { strategy.id } returns "direct"
        every { registry.ordered() } returns listOf(strategy)
        coEvery { strategy.probe(any()) } returns ProbeResult.Applicable(
            tentativeMime = "video/mp4", tentativeSizeBytes = 1000L,
        )
        coEvery { strategy.open(any(), any()) } returns OpenResult.Stream(
            body = streamBody,
            contentLength = 1000L,
            mime = "video/mp4",
            fileName = "test.mp4",
            close = {},
        )
        coEvery { writer.writeFromStream(any(), any(), any(), any(), any()) } returns
            LinkDownloadWriter.WriteResult.Saved(
                resourceLabel = "Downloads",
                fileName = "test.mp4",
                destinationUri = null,
            )

        coordinator.handle("https://www.instagram.com/p/test", noOpCallbacks, "acc1")

        // markLastUsed must use the resolved stored host, not the original URL host.
        coVerify { authSessionRepository.markLastUsed("instagram.com", "acc1") }
    }
}
