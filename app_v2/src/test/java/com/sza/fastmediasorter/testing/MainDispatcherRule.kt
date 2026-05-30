package com.sza.fastmediasorter.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Installs a [TestDispatcher] as [Dispatchers.Main] for the duration of a test and resets it
 * afterwards. Single canonical way to drive coroutine-based domain/data logic deterministically.
 *
 * Usage:
 * ```
 * @get:Rule val dispatcherRule = MainDispatcherRule()
 * ```
 * Pass a shared dispatcher into `runTest(dispatcherRule.testDispatcher)` when a test needs to
 * advance virtual time explicitly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
