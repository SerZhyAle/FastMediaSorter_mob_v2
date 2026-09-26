package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import android.os.Environment
import com.sza.fastmediasorter.wear.data.wear.WearDataLayerPaths
import com.sza.fastmediasorter.wear.domain.model.WearBackground
import com.sza.fastmediasorter.wear.domain.model.WearBackgroundMode
import com.sza.fastmediasorter.wear.domain.model.WearClockStyle
import com.sza.fastmediasorter.wear.domain.repository.WearClockStyleRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * S2000: the fallback in strategic 3.3.8 is the whole point of this resolver, and every way it can
 * fire - never delivered, deleted since, delivery cut off half way - is a state a device reaches only
 * by unplugging a watch at the right moment. So the rule is pinned here rather than on hardware.
 */
class ResolveWearBackgroundUseCaseTest {

    @get:Rule
    val incoming = TemporaryFolder()

    private val preferences: WearPreferencesRepository = mockk()
    private val context: Context = mockk()
    private val clockStyles: WearClockStyleRepository = mockk()
    private var clockStyle: WearClockStyle = WearClockStyle.DEFAULT

    @Test
    fun `branded animation stays branded even when a frame is sitting there`() {
        runTest {
            writeFrame(bytes = 1)
            every { preferences.backgroundMode } returns flowOf(WearBackgroundMode.BRANDED_ANIMATION)

            assertEquals(WearBackground.BrandedAnimation, background().first())
        }
    }

    @Test
    fun `branded still yields branded still even when a frame is sitting there`() {
        runTest {
            writeFrame(bytes = 1)
            every { preferences.backgroundMode } returns flowOf(WearBackgroundMode.BRANDED_STILL)

            assertEquals(WearBackground.BrandedStill, background().first())
        }
    }

    @Test
    fun `image mode with a readable frame yields that frame`() {
        runTest {
            val frame = writeFrame(bytes = 1)
            every { preferences.backgroundMode } returns flowOf(WearBackgroundMode.IMAGE)

            assertEquals(WearBackground.Image(frame), background().first())
        }
    }

    @Test
    fun `image mode with no frame falls back to the branded animation`() {
        runTest {
            every { preferences.backgroundMode } returns flowOf(WearBackgroundMode.IMAGE)

            assertEquals(WearBackground.BrandedAnimation, background().first())
        }
    }

    @Test
    fun `image mode with a zero byte frame falls back to the branded animation`() {
        runTest {
            writeFrame(bytes = 0)
            every { preferences.backgroundMode } returns flowOf(WearBackgroundMode.IMAGE)

            assertEquals(WearBackground.BrandedAnimation, background().first())
        }
    }

    @Test
    fun `none mode yields WearBackground None`() {
        runTest {
            every { preferences.backgroundMode } returns flowOf(WearBackgroundMode.NONE)

            assertEquals(WearBackground.None, background().first())
        }
    }

    @Test
    fun `follow phone with no phone style yet draws the branded animation`() {
        runTest {
            every { preferences.backgroundMode } returns flowOf(WearBackgroundMode.FOLLOW_PHONE)

            assertEquals(WearBackground.BrandedAnimation, background().first())
        }
    }

    @Test
    fun `follow phone takes the launcher backdrop the phone published`() {
        runTest {
            every { preferences.backgroundMode } returns flowOf(WearBackgroundMode.FOLLOW_PHONE)
            clockStyle = WearClockStyle.DEFAULT.copy(launcherBackdrop = WearBackgroundMode.BRANDED_STILL)
            assertEquals(WearBackground.BrandedStill, background().first())

            clockStyle = WearClockStyle.DEFAULT.copy(launcherBackdrop = WearBackgroundMode.NONE)
            assertEquals(WearBackground.None, background().first())
        }
    }

    @Test
    fun `follow phone on a launcher photo draws the frame the companion delivered`() {
        runTest {
            val frame = writeFrame(bytes = 1)
            every { preferences.backgroundMode } returns flowOf(WearBackgroundMode.FOLLOW_PHONE)
            clockStyle = WearClockStyle.DEFAULT.copy(launcherBackdrop = WearBackgroundMode.IMAGE)

            assertEquals(WearBackground.Image(frame), background().first())
        }
    }

    @Test
    fun `a launcher backdrop is ignored outside follow phone`() {
        runTest {
            every { preferences.backgroundMode } returns flowOf(WearBackgroundMode.BRANDED_ANIMATION)
            clockStyle = WearClockStyle.DEFAULT.copy(launcherBackdrop = WearBackgroundMode.NONE)

            assertEquals(WearBackground.BrandedAnimation, background().first())
        }
    }

    private fun writeFrame(bytes: Int): File {
        val frame = File(incoming.root, WearDataLayerPaths.BACKGROUND_IMAGE_FILE_NAME)
        frame.writeBytes(ByteArray(bytes))
        return frame
    }

    private fun background(): Flow<WearBackground> {
        every { context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) } returns incoming.root
        every { clockStyles.style } returns flowOf(clockStyle)
        return ResolveWearBackgroundUseCase(context, preferences, clockStyles).invoke()
    }
}
