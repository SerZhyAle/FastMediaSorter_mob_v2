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
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.settings.helpers.GeneralSettingsSectionsHelper
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultCredentialsInputTest {

    private companion object {
        const val DEFAULT_USER_ACCEPTS_INLINE_TEXT_INPUT = "default user accepts inline text input"
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(GeneralSettingsSectionsHelper.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(GeneralSettingsSectionsHelper.KEY_APP_DATA_EXPANDED, true)
            .commit()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(GeneralSettingsSectionsHelper.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun defaultUserAcceptsInlineTextInput() {
        check(DEFAULT_USER_ACCEPTS_INLINE_TEXT_INPUT.isNotEmpty())

        val intent = Intent(context, SettingsActivity::class.java).apply {
            putExtra(SettingsActivity.EXTRA_INITIAL_TAB, 0)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ActivityScenario.launch<SettingsActivity>(intent).use {
            onView(withId(R.id.etDefaultUser)).perform(
                scrollTo(),
                click(),
                clearText(),
                typeText("s0090-user"),
                pressImeActionButton(),
                closeSoftKeyboard(),
            )

            onView(withId(R.id.etDefaultUser)).check(matches(withText("s0090-user")))
            onView(withId(R.id.searchOverlay)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }
}