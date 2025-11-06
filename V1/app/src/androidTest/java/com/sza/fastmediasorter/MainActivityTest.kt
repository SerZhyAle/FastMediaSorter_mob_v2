package com.sza.fastmediasorter

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val activityRule = ActivityTestRule(MainActivity::class.java)

    @Test
    fun mainActivity_hasTabLayout() {
        // Check if tab layout is present
        onView(withId(R.id.tabLayout))
            .check(matches(isDisplayed()))
    }

    @Test
    fun mainActivity_hasViewPager() {
        // Check if view pager is present
        onView(withId(R.id.viewPager))
            .check(matches(isDisplayed()))
    }

    @Test
    fun mainActivity_hasTopButtons() {
        // Check if top buttons are present
        onView(withId(R.id.sortButton))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.sort)))

        onView(withId(R.id.settingsButton))
            .check(matches(isDisplayed()))

        onView(withId(R.id.slideshowButton))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.slideshow)))
    }

    @Test
    fun mainActivity_hasIntervalInput() {
        // Check if interval input is present
        onView(withId(R.id.intervalInput))
            .check(matches(isDisplayed()))
    }

    @Test
    fun mainActivity_tabsAreClickable() {
        // Test tab switching by text
        onView(withText(R.string.local_folders))
            .check(matches(isDisplayed()))
            .perform(click())

        onView(withText(R.string.network))
            .check(matches(isDisplayed()))
            .perform(click())
    }

    @Test
    fun mainActivity_sortButtonWorks() {
        // Test sort button click
        onView(withId(R.id.sortButton))
            .check(matches(isDisplayed()))
            .perform(click())
    }

    @Test
    fun mainActivity_slideshowButtonWorks() {
        // Test slideshow button click
        onView(withId(R.id.slideshowButton))
            .check(matches(isDisplayed()))
            .perform(click())
    }
}
