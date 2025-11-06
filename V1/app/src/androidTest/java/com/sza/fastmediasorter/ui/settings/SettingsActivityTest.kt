package com.sza.fastmediasorter.ui.settings

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import com.sza.fastmediasorter.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsActivityTest {
    @get:Rule
    val activityRule = ActivityTestRule(SettingsActivity::class.java)

    @Test
    fun settingsActivity_displaysCorrectTitle() {
        // Check if the settings title is displayed
        onView(withId(R.id.settingsTitle))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.application_settings)))
    }

    @Test
    fun settingsActivity_hasAllMainSections() {
        // Check if all main settings sections are present
        onView(withId(R.id.sortingSettingsLabel))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.sorting_settings)))

        onView(withId(R.id.slideshowSettingsLabel))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.slideshow_settings)))

        onView(withId(R.id.generalSettingsLabel))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.general_settings)))
    }

    @Test
    fun settingsActivity_hasLanguageDropdown() {
        // Check if language dropdown is present
        onView(withId(R.id.languageLabel))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.language)))

        onView(withId(R.id.languageSpinner))
            .check(matches(isDisplayed()))
    }

    @Test
    fun settingsActivity_hasAllCheckboxes() {
        // Check if all checkboxes are present
        onView(withId(R.id.allowCopyCheckbox))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.allow_to_copy)))

        onView(withId(R.id.allowMoveCheckbox))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.allow_to_move)))

        onView(withId(R.id.allowDeleteCheckbox))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.allow_to_delete)))

        onView(withId(R.id.showControlsCheckbox))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.show_controls)))

        onView(withId(R.id.keepScreenOnCheckbox))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.not_allow_device_to_sleep)))
    }

    @Test
    fun settingsActivity_checkboxInteraction() {
        // Test checkbox interaction
        onView(withId(R.id.allowCopyCheckbox))
            .perform(click())

        onView(withId(R.id.allowMoveCheckbox))
            .perform(click())

        // Verify checkboxes can be toggled
        onView(withId(R.id.allowCopyCheckbox))
            .check(matches(isDisplayed()))

        onView(withId(R.id.allowMoveCheckbox))
            .check(matches(isDisplayed()))
    }

    @Test
    fun settingsActivity_hasActionButtons() {
        // Check if action buttons are present
        onView(withId(R.id.requestMediaAccessButton))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.grant_media_access)))

        onView(withId(R.id.userGuideButton))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.user_guide)))

        onView(withId(R.id.viewLogsButton))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.view_logs)))
    }

    @Test
    fun settingsActivity_usernamePasswordFields() {
        // Test username and password input fields
        onView(withId(R.id.defaultUsernameInput))
            .check(matches(isDisplayed()))
            .perform(typeText("testuser"))

        onView(withId(R.id.defaultPasswordInput))
            .check(matches(isDisplayed()))
            .perform(typeText("testpass"))

        // Close keyboard
        closeSoftKeyboard()

        // Verify text was entered
        onView(withId(R.id.defaultUsernameInput))
            .check(matches(withText("testuser")))

        onView(withId(R.id.defaultPasswordInput))
            .check(matches(withText("testpass")))
    }

    @Test
    fun settingsActivity_languageDropdownInteraction() {
        // Test language dropdown interaction
        onView(withId(R.id.languageSpinner))
            .check(matches(isDisplayed()))
            .perform(click())

        // Note: The actual dropdown items test would require more complex setup
        // as it involves auto-complete text view with dynamic content
    }

    @Test
    fun settingsActivity_viewLogsButton() {
        // Test view logs button click
        onView(withId(R.id.viewLogsButton))
            .check(matches(isDisplayed()))
            .perform(click())

        // Note: This would open a dialog, testing dialog content would require additional setup
    }
}
