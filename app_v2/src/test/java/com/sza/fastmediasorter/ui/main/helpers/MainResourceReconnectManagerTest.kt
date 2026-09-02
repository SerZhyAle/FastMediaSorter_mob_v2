package com.sza.fastmediasorter.ui.main.helpers

import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.testing.createMediaResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * S2376: tests for [MainResourceReconnectManager] state preservation across host recreation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainResourceReconnectManagerTest {

    private lateinit var activity: FragmentActivity
    private var launchedPickerUri: Uri? = null
    private var reconnectedId: Long? = null
    private var reconnectedUri: Uri? = null

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        activity.setTheme(R.style.Theme_FastMediaSorter_App)
        launchedPickerUri = null
        reconnectedId = null
        reconnectedUri = null
    }

    private fun createManager(act: FragmentActivity = activity): MainResourceReconnectManager =
        MainResourceReconnectManager(
            activity = act,
            launchPicker = { launchedPickerUri = it },
            onReconnect = { id, uri ->
                reconnectedId = id
                reconnectedUri = uri
            },
        )

    @Test
    fun `request saves pending state and restoreState picks it up before picker completes`() {
        val manager = createManager()
        val resource = createMediaResource(
            id = 42L,
            type = ResourceType.LOCAL,
            path = "/storage/emulated/0/Pictures",
        )

        manager.request(resource)
        assertNotNull(launchedPickerUri)

        val bundle = Bundle()
        manager.saveState(bundle)

        assertEquals(42L, bundle.getLong("s2374_reconnect_pending_id"))
        assertEquals("/storage/emulated/0/Pictures", bundle.getString("s2374_reconnect_pending_path"))
        assertNull(bundle.getString("s2376_reconnect_picked_uri"))

        val newActivity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        newActivity.setTheme(R.style.Theme_FastMediaSorter_App)
        val newManager = createManager(newActivity)
        newManager.restoreState(bundle)

        // Completing folder pick on new manager proceeds with reconnect
        val uri = Uri.parse("file:///storage/emulated/0/Pictures")
        newManager.onFolderPicked(uri)

        assertEquals(42L, reconnectedId)
        assertEquals(uri, reconnectedUri)
    }

    @Test
    fun `mismatch dialog saves picked uri and restoreState re-shows confirmation dialog`() {
        val manager = createManager()
        val resource = createMediaResource(
            id = 99L,
            type = ResourceType.LOCAL,
            path = "/storage/emulated/0/OriginalFolder",
        )

        manager.request(resource)

        // User picks a different folder
        val pickedUri = Uri.parse("file:///storage/emulated/0/DifferentFolder")
        manager.onFolderPicked(pickedUri)

        // Dialog should be displayed
        val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
        assertNotNull(dialog)

        // Save state while dialog is showing
        val bundle = Bundle()
        manager.saveState(bundle)

        assertEquals(99L, bundle.getLong("s2374_reconnect_pending_id"))
        assertEquals("/storage/emulated/0/OriginalFolder", bundle.getString("s2374_reconnect_pending_path"))
        assertEquals(pickedUri.toString(), bundle.getString("s2376_reconnect_picked_uri"))

        // Recreate host
        val newActivity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        newActivity.setTheme(R.style.Theme_FastMediaSorter_App)
        val newManager = createManager(newActivity)

        newManager.restoreState(bundle)

        // Restored dialog is showing on new activity
        val restoredDialog = ShadowDialog.getLatestDialog() as? AlertDialog
        assertNotNull(restoredDialog)

        // Confirming on restored dialog executes reconnect
        restoredDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(99L, reconnectedId)
        assertEquals(pickedUri, reconnectedUri)
    }

    @Test
    fun `cancelling mismatch dialog clears pending state so next saveState is empty`() {
        val manager = createManager()
        val resource = createMediaResource(
            id = 100L,
            type = ResourceType.LOCAL,
            path = "/storage/emulated/0/OriginalFolder",
        )

        manager.request(resource)
        val pickedUri = Uri.parse("file:///storage/emulated/0/DifferentFolder")
        manager.onFolderPicked(pickedUri)

        val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
        assertNotNull(dialog)
        dialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(reconnectedId)

        val bundle = Bundle()
        manager.saveState(bundle)
        assertEquals(-1L, bundle.getLong("s2374_reconnect_pending_id", -1L))
    }
}
