package com.sza.fastmediasorter.ui.share

import android.content.Context
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.link.LinkAutoDownloadCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast

/**
 * S0116 Phase 06 step 5: Robolectric-backed test for [LinkAutoDownloadResultPresenter].
 * Validates the toggle-driven branching for all new and existing Result variants.
 */
@RunWith(RobolectricTestRunner::class)
class LinkAutoDownloadResultPresenterTest {

    private val appContext: Context = ApplicationProvider.getApplicationContext()
    private val activity: AppCompatActivity =
        Robolectric.buildActivity(AppCompatActivity::class.java).create().get()

    private fun presenter(
        openInPlayer: Boolean,
        authSessionRepository: AuthSessionRepository = mockk(relaxed = true),
    ): LinkAutoDownloadResultPresenter {
        val settings = mockk<SettingsRepository>()
        coEvery { settings.getSettings() } returns flowOf(
            AppSettings(linkAutoDownloadOpenInPlayer = openInPlayer),
        )
        coEvery { authSessionRepository.isDismissedForHost(any()) } returns false
        coEvery { authSessionRepository.isDismissedForAccount(any(), any()) } returns false
        return LinkAutoDownloadResultPresenter(appContext, settings, authSessionRepository)
    }

    @After
    fun resetToasts() {
        ShadowToast.reset()
    }

    @Test
    fun `Saved + toggle on opens player intent`() = runTest {
        val uri = android.net.Uri.parse("file:///tmp/x.mp4")
        val result = LinkAutoDownloadCoordinator.Result.Saved(
            resourceLabel = "Resource", fileName = "x.mp4", mime = "video/mp4", openInPlayerUri = uri,
        )
        presenter(openInPlayer = true).present(result, activity)
        val started = shadowOf(activity).peekNextStartedActivity()
        assertNotNull("expected player intent", started)
        assertEquals("Player intent should target the saved URI", uri, started?.data)
    }

    @Test
    fun `Saved + toggle off shows saved_to_resource toast`() = runTest {
        val result = LinkAutoDownloadCoordinator.Result.Saved(
            resourceLabel = "Resource", fileName = "x.mp4", mime = "video/mp4", openInPlayerUri = null,
        )
        presenter(openInPlayer = false).present(result, activity)
        assertEquals(
            appContext.getString(R.string.s0116_toast_saved_to_resource, "x.mp4"),
            ShadowToast.getTextOfLatestToast(),
        )
        assertNull("no player intent when toggle off", shadowOf(activity).peekNextStartedActivity())
    }

    @Test
    fun `MuxFailed + toggle on shows mux_failed toast`() = runTest {
        val result = LinkAutoDownloadCoordinator.Result.Failed.MuxFailed(codec = "video/opus")
        presenter(openInPlayer = true).present(result, activity)
        assertEquals(
            appContext.getString(R.string.s0116_toast_mux_failed, "video/opus"),
            ShadowToast.getTextOfLatestToast(),
        )
    }

    @Test
    fun `MuxFailed + toggle off shows same mux_failed toast`() = runTest {
        val result = LinkAutoDownloadCoordinator.Result.Failed.MuxFailed(codec = "video/opus")
        presenter(openInPlayer = false).present(result, activity)
        assertEquals(
            appContext.getString(R.string.s0116_toast_mux_failed, "video/opus"),
            ShadowToast.getTextOfLatestToast(),
        )
    }

    @Test
    fun `DrmBlocked toast independent of toggle (toggle on)`() = runTest {
        presenter(openInPlayer = true).present(
            LinkAutoDownloadCoordinator.Result.Failed.DrmBlocked, activity,
        )
        assertEquals(
            appContext.getString(R.string.s0116_toast_drm_blocked),
            ShadowToast.getTextOfLatestToast(),
        )
    }

    @Test
    fun `AuthRequired + toggle off shows auth_required toast with host`() = runTest {
        val result = LinkAutoDownloadCoordinator.Result.Failed.AuthRequired(
            host = "example.com", originalUrl = "https://example.com/video",
        )
        presenter(openInPlayer = false).present(result, activity)
        assertEquals(
            appContext.getString(R.string.s0116_toast_auth_required, "example.com"),
            ShadowToast.getTextOfLatestToast(),
        )
    }

    @Test
    fun `AuthRequired + toggle on triggers retry callback`() = runTest {
        var retryUrl: String? = null
        val result = LinkAutoDownloadCoordinator.Result.Failed.AuthRequired(
            host = "example.com", originalUrl = "https://example.com/video",
        )
        presenter(openInPlayer = true).present(
            result = result,
            hostActivity = activity,
            onAuthRetryRequested = { url -> retryUrl = url },
        )
        assertEquals("https://example.com/video", retryUrl)
        // Toast must NOT fire when toggle is on (dialog/retry path takes over).
        assertNull("no toast when openInPlayer=true", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `BatchCompleted without failures shows saved-count toast`() = runTest {
        val result = LinkAutoDownloadCoordinator.Result.BatchCompleted(
            summary = LinkAutoDownloadCoordinator.Result.BatchSummary(
                label = "Batch",
                totalItems = 3,
                successCount = 3,
                failures = emptyList(),
            ),
        )

        presenter(openInPlayer = true).present(result, activity)

        assertEquals(
            appContext.getString(R.string.s0117_toast_batch_saved, 3),
            ShadowToast.getTextOfLatestToast(),
        )
        assertNull("batch success should not auto-open player", shadowOf(activity).peekNextStartedActivity())
    }

    @Test
    fun `BatchCompleted with failures shows summary dialog`() = runTest {
        val result = LinkAutoDownloadCoordinator.Result.BatchCompleted(
            summary = LinkAutoDownloadCoordinator.Result.BatchSummary(
                label = "Batch",
                totalItems = 2,
                successCount = 1,
                failures = listOf(
                    LinkAutoDownloadCoordinator.Result.BatchFailure(
                        title = "Broken item",
                        failure = LinkAutoDownloadCoordinator.Result.Failed.NoMediaFound,
                    ),
                ),
            ),
        )

        presenter(openInPlayer = false).present(result, activity)

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("summary dialog expected for partial batch failure", dialog)
        assertEquals(
            appContext.getString(R.string.s0117_batch_dialog_title),
            shadowOf(dialog).title,
        )
    }

    @Test
    fun `S0118 friendly-copy regression - generic Failed Other still shows resource-backed toast`() = runTest {
        val cause = RuntimeException("synthetic")
        presenter(openInPlayer = false).present(
            result = LinkAutoDownloadCoordinator.Result.Failed.Other(cause),
            hostActivity = activity,
        )
        // The presenter must still produce a non-blank toast — the friendly-copy revision
        // should not regress the share fallback path into silence.
        val toast = ShadowToast.getTextOfLatestToast()
        assertNotNull("friendly-copy fallback toast must be shown", toast)
        assertEquals(false, toast.isNullOrBlank())
    }

    @Test
    fun `SocialPreviewOnly negative action stores account-scoped dismissal when accountId is available`() = runTest {
        val authRepository = mockk<AuthSessionRepository>(relaxed = true)
        coEvery { authRepository.isDismissedForHost(any()) } returns false
        coEvery { authRepository.isDismissedForAccount(any(), any()) } returns false
        val result = LinkAutoDownloadCoordinator.Result.Failed.SocialPreviewOnly(
            originalUrl = "https://instagram.com/p/example",
            host = "instagram.com",
            hadExistingSession = true,
            accountId = "acct-42",
            accountDisplayName = "@named_account",
        )

        presenter(openInPlayer = false, authSessionRepository = authRepository).present(result, activity)

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("reactive reauth dialog expected", dialog)
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        coVerify(exactly = 1) {
            authRepository.markDismissedForAccount("instagram.com", "acct-42", "@named_account")
        }
        coVerify(exactly = 0) { authRepository.markDismissed(any()) }
    }
}
