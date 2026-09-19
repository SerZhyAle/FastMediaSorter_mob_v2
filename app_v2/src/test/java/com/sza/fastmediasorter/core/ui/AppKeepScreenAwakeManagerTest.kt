package com.sza.fastmediasorter.core.ui

import android.app.Activity
import android.view.Window
import android.view.WindowManager
import androidx.viewbinding.ViewBinding
import com.sza.fastmediasorter.core.util.AnimationPolicy
import com.sza.fastmediasorter.core.util.PowerPolicyDecision
import com.sza.fastmediasorter.core.util.PowerPolicyLevel
import com.sza.fastmediasorter.core.util.PowerPolicyReason
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.testing.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * S3285: the hold applied to the hosts that do not inherit BaseActivity - the transparent launch
 * panel among them. The dispatcher is unconfined so the settings collector in the manager's init
 * block has already run by the time a test calls a lifecycle callback.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppKeepScreenAwakeManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val settings = MutableStateFlow(AppSettings(preventSleep = true))

    private val repository: SettingsRepository = mockk {
        every { getSettings() } returns settings
    }

    private val window: Window = mockk(relaxed = true)

    private val activity: Activity = mockk(relaxed = true) {
        every { this@mockk.window } returns this@AppKeepScreenAwakeManagerTest.window
    }

    // S2748: the manager's settings collector lives for the life of this scope, so the scope is
    // joined in @After rather than left to outlive the test body.
    private val scope = CoroutineScope(testDispatcher)

    private fun manager() = AppKeepScreenAwakeManager(scope, repository)

    @After
    fun resetPowerLevelAndScope() {
        AnimationPolicy.update(PowerPolicyDecision(PowerPolicyLevel.NORMAL, PowerPolicyReason.NONE))
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
    }

    @Test
    fun `a resumed plain host gets the flag while the setting is on`() {
        manager().onActivityResumed(activity)

        verify { window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    @Test
    fun `the setting off clears the flag instead`() {
        settings.value = AppSettings(preventSleep = false)

        manager().onActivityResumed(activity)

        verify { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    @Test
    fun `the saving level clears the flag even with the setting on`() {
        AnimationPolicy.update(
            PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.LOW_BATTERY)
        )

        manager().onActivityResumed(activity)

        verify { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    @Test
    fun `turning the setting off reaches the host already on screen`() {
        manager().onActivityResumed(activity)

        settings.value = AppSettings(preventSleep = false)

        verify { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    @Test
    fun `a paused host is not touched by a later settings change`() {
        val manager = manager()
        manager.onActivityResumed(activity)
        manager.onActivityPaused(activity)

        settings.value = AppSettings(preventSleep = false)

        verify(exactly = 0) { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    @Test
    fun `a BaseActivity host keeps its own window`() {
        val baseActivity: BaseActivity<ViewBinding> = mockk(relaxed = true)
        every { baseActivity.window } returns window

        manager().onActivityResumed(baseActivity)

        verify(exactly = 0) { window.addFlags(any()) }
        verify(exactly = 0) { window.clearFlags(any()) }
    }
}
