package com.sza.fastmediasorter.ui.settings.helpers

import android.content.DialogInterface
import android.view.ContextThemeWrapper
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.util.showBoundTo
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The disable confirmation must not persist a settings snapshot captured before the dialog opened. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class GeneralSettingsRemoteSourceToggleTest {

    private val viewModel = mockk<SettingsViewModel>(relaxed = true)
    private val fragment = mockk<Fragment>(relaxed = true)
    private val row = mockk<SettingsToggleRow>(relaxed = true)
    private var updating = false
    private lateinit var helper: GeneralSettingsViewSetupHelper

    private val transform: (AppSettings) -> AppSettings = { it }

    @Before
    fun setUp() {
        val smb = mockk<MediaResource> { every { type } returns ResourceType.SMB }
        every { fragment.requireContext() } returns
            ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_FastMediaSorter_App)
        every { viewModel.resources } returns MutableStateFlow(listOf(smb))
        helper = GeneralSettingsViewSetupHelper(
            hostContext = GeneralSettingsHostContext(mockk(relaxed = true), viewModel, fragment),
            isUpdatingSpinner = ::updating,
            actionHelpers = mockk(relaxed = true),
            ensureAllFilesPredefinedResourceUseCase = mockk(relaxed = true),
            remoteSourceAvailabilityGate = mockk(relaxed = true),
            languageSplitInstaller = mockk(relaxed = true),
        )
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `confirming the disable dialog applies the transform to the persisted settings`() {
        val positive = slot<DialogInterface.OnClickListener>()
        mockkConstructor(MaterialAlertDialogBuilder::class)
        every {
            anyConstructed<MaterialAlertDialogBuilder>().setTitle(any<Int>())
        } answers { self as MaterialAlertDialogBuilder }
        every {
            anyConstructed<MaterialAlertDialogBuilder>().setMessage(any<Int>())
        } answers { self as MaterialAlertDialogBuilder }
        every {
            anyConstructed<MaterialAlertDialogBuilder>().setCancelable(any())
        } answers { self as MaterialAlertDialogBuilder }
        every {
            anyConstructed<MaterialAlertDialogBuilder>().setPositiveButton(any<Int>(), capture(positive))
        } answers { self as MaterialAlertDialogBuilder }
        every {
            anyConstructed<MaterialAlertDialogBuilder>().setNegativeButton(any<Int>(), any())
        } answers { self as MaterialAlertDialogBuilder }
        mockkStatic("com.sza.fastmediasorter.util.LifecycleDialogExtKt")
        every { any<AlertDialog.Builder>().showBoundTo(any<Fragment>()) } returns null

        helper.applyRemoteSourceToggle(row, enabled = false, affectedTypes = listOf(ResourceType.SMB), transform)
        positive.captured.onClick(mockk(relaxed = true), DialogInterface.BUTTON_POSITIVE)

        assertPersistedThroughTransformOverload()
    }

    @Test
    fun `enabling a group applies the transform to the persisted settings`() {
        helper.applyRemoteSourceToggle(row, enabled = true, affectedTypes = listOf(ResourceType.SMB), transform)

        assertPersistedThroughTransformOverload()
    }

    private fun assertPersistedThroughTransformOverload() {
        val written = slot<(AppSettings) -> AppSettings>()
        verify(exactly = 1) { viewModel.updateSettings(capture(written)) }
        assertSame(transform, written.captured)
        verify(exactly = 0) { viewModel.settings }
    }
}
