package com.sza.fastmediasorter.ui.addresource

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
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
            onView(withId(R.id.tilSmbServer)).perform(scrollTo(), click())
            onView(withId(R.id.etSmbServer)).check(matches(hasFocus()))

            onView(withId(R.id.tilSmbUsername)).perform(scrollTo(), click())
            onView(withId(R.id.etSmbUsername)).check(matches(hasFocus()))

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
            onView(withId(R.id.tilSftpHost)).perform(scrollTo(), click())
            onView(withId(R.id.etSftpHost)).check(matches(hasFocus()))

            onView(withId(R.id.tilSftpUsername)).perform(scrollTo(), click())
            onView(withId(R.id.etSftpUsername)).check(matches(hasFocus()))

            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        } finally {
            scenario.close()
        }
    }
}