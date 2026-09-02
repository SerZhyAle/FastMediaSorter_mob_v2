package com.sza.fastmediasorter.core.logging

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S2343: installCrashHandler used to infer "already installed" from a nullable field that stays
 * null on a JVM where nothing installed a default handler first. The guard never tripped, so a
 * second call captured the handler the first call had installed and delegated to itself until
 * StackOverflowError - which in the unit suite replaced one leaked exception with a whole-suite
 * cascade, and in production replaces the real crash report.
 *
 * The repro condition is two installs on a handler-less JVM, so every test here drives exactly
 * that; the delegation case is the counterweight that fails if the recursion is "fixed" by
 * dropping delegation altogether.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class LoggingHelperCrashHandlerTest {

    private var originalHandler: Thread.UncaughtExceptionHandler? = null

    /** Records delegations instead of rethrowing, so a dispatch that terminates stays observable. */
    private class RecordingHandler : Thread.UncaughtExceptionHandler {
        var count = 0

        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            count++
        }
    }

    @Before
    fun setUp() {
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        setInstalledFlag(false)
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        setInstalledFlag(false)
    }

    /**
     * LoggingHelper is an object outliving every test in the sandbox, and Thread's default handler
     * is process-wide, so both have to be put back by hand or the next class starts with the guard
     * already tripped and this class's handler still installed.
     */
    private fun setInstalledFlag(value: Boolean) {
        val field = LoggingHelper::class.java.getDeclaredField("crashHandlerInstalled")
        field.isAccessible = true
        field.setBoolean(LoggingHelper, value)
    }

    private fun dispatchToCurrentHandler() {
        Thread.getDefaultUncaughtExceptionHandler()
            ?.uncaughtException(Thread.currentThread(), IllegalStateException("S2343 probe"))
    }

    @Test
    fun `two installs on a handler-less JVM dispatch without recursing`() {
        Thread.setDefaultUncaughtExceptionHandler(null)
        LoggingHelper.installCrashHandler()
        LoggingHelper.installCrashHandler()

        try {
            dispatchToCurrentHandler()
        } catch (error: StackOverflowError) {
            fail("installed handler delegated to itself: $error")
        }
    }

    @Test
    fun `previously installed handler receives the exception exactly once`() {
        val previous = RecordingHandler()
        Thread.setDefaultUncaughtExceptionHandler(previous)
        LoggingHelper.installCrashHandler()
        LoggingHelper.installCrashHandler()

        dispatchToCurrentHandler()

        assertEquals(1, previous.count)
    }

    @Test
    fun `second install leaves the handler from the first one in place`() {
        Thread.setDefaultUncaughtExceptionHandler(null)
        LoggingHelper.installCrashHandler()
        val installed = Thread.getDefaultUncaughtExceptionHandler()

        LoggingHelper.installCrashHandler()

        assertSame(installed, Thread.getDefaultUncaughtExceptionHandler())
    }
}
