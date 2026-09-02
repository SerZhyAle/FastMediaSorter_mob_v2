package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.capture.CameraHardwareDataSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GatherSystemInfoUseCaseTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val useCase = GatherSystemInfoUseCase(
        context,
        emptySet(),
        GatherCameraDiagnosticsUseCase(context, CameraHardwareDataSource(context)),
    )

    @Test
    fun `invoke returns non-blank summary`() {
        val summary = useCase().maskedText

        assertTrue("summary must not be blank", summary.isNotBlank())
    }

    @Test
    fun `invoke contains localized section headers`() {
        val summary = useCase().maskedText

        listOf(
            R.string.sysinfo_section_device,
            R.string.sysinfo_section_user,
            R.string.sysinfo_section_os,
            R.string.sysinfo_section_app,
            R.string.sysinfo_section_memory,
            R.string.sysinfo_section_storage,
            R.string.sysinfo_section_display,
            R.string.sysinfo_section_locale,
            R.string.sysinfo_section_time,
        ).forEach { resId ->
            val header = context.getString(resId)
            assertTrue("summary must contain section header '$header'", summary.contains(header))
        }
    }

    @Test
    fun `invoke contains app version name`() {
        val summary = useCase().maskedText

        assertTrue(
            "summary must contain app version name",
            summary.contains(BuildConfig.VERSION_NAME),
        )
    }

    @Test
    fun `invoke with no contributors keeps masked equal to full`() {
        val report = useCase()

        assertTrue("masked must equal full when no contributor is present", report.maskedText == report.fullText)
        assertTrue("hasSensitive must be false when no contributor is present", !report.hasSensitive)
    }

    @Test
    fun `invoke benchmark section formats throughput without stray tilde brackets`() {
        val summary = useCase().maskedText

        assertFalse("summary must not contain stray tilde in brackets '(~)'", summary.contains("(~)"))
    }
}
