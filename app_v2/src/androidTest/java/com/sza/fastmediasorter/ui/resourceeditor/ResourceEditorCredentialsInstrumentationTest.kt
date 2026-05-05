package com.sza.fastmediasorter.ui.resourceeditor

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.TestFixtures
import com.sza.fastmediasorter.domain.model.ResourceType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class ResourceEditorCredentialsInstrumentationTest {

    @Test
    fun smbCredentialsFields_acceptAndDisplayTypedText() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = ResourceEditorActivity.createIntent(context, ResourceType.SMB).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val scenario = ActivityScenario.launch<ResourceEditorActivity>(intent)
        try {
            onView(withId(R.id.etName)).perform(
                scrollTo(),
                click(),
                clearText(),
                typeText(TestFixtures.TEST_SMB_RESOURCE_NAME),
                closeSoftKeyboard(),
            )

            onView(withId(R.id.etUsername)).perform(
                scrollTo(),
                click(),
                clearText(),
                typeText(TestFixtures.DEFAULT_USER),
                closeSoftKeyboard(),
            )

            onView(withId(R.id.etName)).check(matches(withText(TestFixtures.TEST_SMB_RESOURCE_NAME)))
            onView(withId(R.id.etUsername)).check(matches(withText(TestFixtures.DEFAULT_USER)))
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        } finally {
            scenario.close()
        }
    }
}
