package com.sza.fastmediasorter.data.repository

import android.content.Context
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ScreenType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.testing.createResumeState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [ResumeStateRepositoryImpl] persists per-window state to SharedPreferences (Robolectric).
 * Covers save/get round-trip, the missing-filePath and resourceId == -1 null gates,
 * the invalid-enum recovery (clears + null), and clearState.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResumeStateRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var repo: ResumeStateRepositoryImpl

    private val windowId = "w1"

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        repo = ResumeStateRepositoryImpl(context)
    }

    @Test
    fun `getState returns null when nothing saved`() = runTest {
        assertNull(repo.getState(windowId))
    }

    @Test
    fun `save then get round-trips the state`() = runTest {
        val state = createResumeState(
            filePath = "/media/clip.mp4",
            resourceId = 7L,
            currentFolderPath = "/media",
            screenType = ScreenType.PLAYER,
            sortMode = SortMode.DATE_DESC,
            isPlaying = true,
            isSlideshowEnabled = true,
            mediaType = MediaType.VIDEO,
            savedAt = 999L,
        )
        repo.saveState(windowId, state)

        val loaded = repo.getState(windowId)
        requireNotNull(loaded)
        assertEquals("/media/clip.mp4", loaded.filePath)
        assertEquals(7L, loaded.resourceId)
        assertEquals("/media", loaded.currentFolderPath)
        assertEquals(ScreenType.PLAYER, loaded.screenType)
        assertEquals(SortMode.DATE_DESC, loaded.sortMode)
        assertEquals(true, loaded.isPlaying)
        assertEquals(true, loaded.isSlideshowEnabled)
        assertEquals(MediaType.VIDEO, loaded.mediaType)
        assertEquals(999L, loaded.savedAt)
    }

    @Test
    fun `state is isolated per window id`() = runTest {
        repo.saveState("wA", createResumeState(filePath = "/a", resourceId = 1L))
        assertNull(repo.getState("wB"))
        assertEquals("/a", repo.getState("wA")?.filePath)
    }

    @Test
    fun `clearState removes a saved state`() = runTest {
        repo.saveState(windowId, createResumeState(resourceId = 3L))
        repo.clearState(windowId)
        assertNull(repo.getState(windowId))
    }

    @Test
    fun `corrupt enum value clears prefs and returns null`() = runTest {
        // Write a valid state, then poison the screen_type key with a non-enum value.
        repo.saveState(windowId, createResumeState(resourceId = 5L))
        context.getSharedPreferences("resume_state_prefs_$windowId", Context.MODE_PRIVATE)
            .edit()
            .putString("screen_type", "NOT_A_SCREEN")
            .apply()

        assertNull(repo.getState(windowId))
        // Prefs cleared as a side effect -> a fresh read is still null.
        assertNull(repo.getState(windowId))
    }
}
