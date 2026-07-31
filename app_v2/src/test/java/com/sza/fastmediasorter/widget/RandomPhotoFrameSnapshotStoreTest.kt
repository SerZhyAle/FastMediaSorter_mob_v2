package com.sza.fastmediasorter.widget

import android.content.Context
import com.sza.fastmediasorter.widget.RandomPhotoFrameSnapshotStore.SnapshotOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S1170: the photo-frame store now serves both a home-screen widget and a launcher desktop cell, so its
 * key namespacing gained an owner token.
 *
 * The one thing that must not change is the WIDGET key format. It is already written on every device
 * that has a configured frame, and a drifted suffix would not fail loudly - the widget would simply
 * render as unconfigured while the user's settings sat unreachable under the old keys. No static gate
 * can see that, which is why it is pinned here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RandomPhotoFrameSnapshotStoreTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun prefs() =
        context.getSharedPreferences("random_photo_frame_widget", Context.MODE_PRIVATE)

    private fun snapshot(resourceId: Long = 42L) = RandomPhotoFrameSnapshotStore.Snapshot(
        resourceId = resourceId,
        resourceName = "Holiday",
        selectedFilePath = "/sdcard/DCIM/a.jpg",
        selectedThumbnailUri = "content://thumb/1",
        hasRenderablePhoto = true,
        fallbackMessage = "",
    )

    @Test
    fun `a widget writes the exact key format it used before the owner token existed`() {
        RandomPhotoFrameSnapshotStore.write(
            context,
            SnapshotOwner.Widget(appWidgetId = 7),
            snapshot(),
            notifyWidgets = false,
        )

        // The literal strings are the point of this test - do not replace them with the constants,
        // or a rename of both at once would pass while every installed widget was orphaned.
        assertTrue("resource_id_7 must be the stored key", prefs().contains("resource_id_7"))
        assertEquals(42L, prefs().getLong("resource_id_7", -1L))
        assertEquals("Holiday", prefs().getString("resource_name_7", ""))
        assertEquals("/sdcard/DCIM/a.jpg", prefs().getString("selected_file_path_7", ""))
        assertTrue(prefs().getBoolean("has_renderable_photo_7", false))
    }

    @Test
    fun `the int overload and the widget owner are the same storage`() {
        RandomPhotoFrameSnapshotStore.write(context, 11, snapshot(resourceId = 5L), notifyWidgets = false)
        val viaOwner = RandomPhotoFrameSnapshotStore.read(context, SnapshotOwner.Widget(11))
        assertEquals(5L, viaOwner.resourceId)
    }

    @Test
    fun `a launcher cell cannot collide with the widget that shares its number`() {
        RandomPhotoFrameSnapshotStore.write(
            context,
            SnapshotOwner.Widget(3),
            snapshot(resourceId = 100L),
            notifyWidgets = false,
        )
        RandomPhotoFrameSnapshotStore.write(
            context,
            SnapshotOwner.LauncherCell(3),
            snapshot(resourceId = 200L),
            notifyWidgets = false,
        )

        assertEquals(100L, RandomPhotoFrameSnapshotStore.read(context, SnapshotOwner.Widget(3)).resourceId)
        assertEquals(
            200L,
            RandomPhotoFrameSnapshotStore.read(context, SnapshotOwner.LauncherCell(3)).resourceId,
        )
    }

    @Test
    fun `clearing a cell leaves the widget with the same number untouched`() {
        RandomPhotoFrameSnapshotStore.write(context, SnapshotOwner.Widget(9), snapshot(), notifyWidgets = false)
        RandomPhotoFrameSnapshotStore.write(
            context,
            SnapshotOwner.LauncherCell(9),
            snapshot(),
            notifyWidgets = false,
        )

        RandomPhotoFrameSnapshotStore.clear(context, SnapshotOwner.LauncherCell(9), notifyWidgets = false)

        assertTrue(RandomPhotoFrameSnapshotStore.read(context, SnapshotOwner.Widget(9)).isConfigured)
        assertFalse(RandomPhotoFrameSnapshotStore.read(context, SnapshotOwner.LauncherCell(9)).isConfigured)
    }

    @Test
    fun `an unconfigured owner reads as not configured`() {
        assertFalse(RandomPhotoFrameSnapshotStore.read(context, SnapshotOwner.Widget(999)).isConfigured)
    }
}
