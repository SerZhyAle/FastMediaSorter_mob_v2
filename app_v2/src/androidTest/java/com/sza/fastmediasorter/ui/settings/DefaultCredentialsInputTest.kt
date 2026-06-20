package com.sza.fastmediasorter.ui.settings

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.TestFixtures
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionStore
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultCredentialsInputTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // S0535: section state moved to the consolidated store; expand AppData via its unified key.
        context.getSharedPreferences(CollapsibleSectionStore.NAMESPACE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("general__app_data", true)
            .commit()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(CollapsibleSectionStore.NAMESPACE, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun defaultUserAcceptsInlineTextInput() {
        check(TestFixtures.DEFAULT_USER.isNotEmpty())

        val intent = Intent(context, SettingsActivity::class.java).apply {
            putExtra(SettingsActivity.EXTRA_INITIAL_TAB, 0)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ActivityScenario.launch<SettingsActivity>(intent).use {
            onView(withId(R.id.etDefaultUser)).perform(
                scrollTo(),
                click(),
                clearText(),
                typeText(TestFixtures.DEFAULT_USER),
                pressImeActionButton(),
                closeSoftKeyboard(),
            )

            onView(withId(R.id.etDefaultUser)).check(matches(withText(TestFixtures.DEFAULT_USER)))
            onView(withId(R.id.searchOverlay)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

    @Test
    fun defaultCredentialBoxesForwardTapToEditors() {
        val intent = Intent(context, SettingsActivity::class.java).apply {
            putExtra(SettingsActivity.EXTRA_INITIAL_TAB, 0)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ActivityScenario.launch<SettingsActivity>(intent).use {
            onView(withId(R.id.tilDefaultUser)).perform(scrollTo(), click())
            onView(withId(R.id.etDefaultUser)).check(matches(hasFocus()))

            onView(withId(R.id.tilDefaultPassword)).perform(scrollTo(), click())
            onView(withId(R.id.etDefaultPassword)).check(matches(hasFocus()))
        }
    }
}
