package com.sza.fastmediasorter.ui.addresource

import android.content.Intent
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.main.ResourceTab
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class AddResourceInputTapTest {

    @Test
    fun smbOutlinedBoxesForwardTapToEditors() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = AddResourceActivity.createIntent(context, preselectedTab = ResourceTab.SMB).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val scenario = ActivityScenario.launch<AddResourceActivity>(intent)
        try {
            onView(withId(R.id.tilSmbServer)).perform(scrollTo(), tapOutlinedBox(R.id.etSmbServer))
            assertFocusOn(scenario, R.id.etSmbServer)

            // The previous tap raised the keyboard; on a short screen it covers the next box.
            onView(withId(R.id.tilSmbUsername))
                .perform(closeSoftKeyboard(), scrollTo(), tapOutlinedBox(R.id.etSmbUsername))
            assertFocusOn(scenario, R.id.etSmbUsername)

            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun sftpOutlinedBoxesForwardTapToEditors() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = AddResourceActivity.createIntent(context, preselectedTab = ResourceTab.FTP_SFTP).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val scenario = ActivityScenario.launch<AddResourceActivity>(intent)
        try {
            onView(withId(R.id.tilSftpHost)).perform(scrollTo(), tapOutlinedBox(R.id.etSftpHost))
            assertFocusOn(scenario, R.id.etSftpHost)

            onView(withId(R.id.tilSftpUsername))
                .perform(closeSoftKeyboard(), scrollTo(), tapOutlinedBox(R.id.etSftpUsername))
            assertFocusOn(scenario, R.id.etSftpUsername)

            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        } finally {
            scenario.close()
        }
    }

    /**
     * Taps the outlined box itself, at its vertical centre. A plain click() lands on the centre of the
     * whole TextInputLayout, and the SMB server's helper text wraps to several lines below the box, so
     * that tap hit the helper text - which is not the box and not a place the user expects focus from.
     */
    private fun tapOutlinedBox(editorId: Int): ViewAction = GeneralClickAction(
        Tap.SINGLE,
        CoordinatesProvider { layout ->
            val editor = layout.findViewById<View>(editorId)
            val location = IntArray(2).also(editor::getLocationOnScreen)
            floatArrayOf(location[0] + editor.width / 2f, location[1] + editor.height / 2f)
        },
        Press.FINGER,
        0,
        0,
    )

    // Names the view that holds focus instead, so a failure says where the tap went.
    private fun assertFocusOn(scenario: ActivityScenario<AddResourceActivity>, expectedId: Int) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        var holder = "nothing"
        scenario.onActivity { activity ->
            val focused = activity.currentFocus
            holder = when {
                focused == null -> "nothing"
                focused.id == View.NO_ID -> focused.javaClass.simpleName
                else -> activity.resources.getResourceEntryName(focused.id)
            }
        }
        val expected = ApplicationProvider.getApplicationContext<android.content.Context>()
            .resources.getResourceEntryName(expectedId)
        assertEquals("focus after the tap", expected, holder)
    }
}
