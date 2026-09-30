package com.sza.fastmediasorter.core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GmsAvailabilityCheckerStoreTest {

    private val launched = mutableListOf<String>()

    private fun contextFailingOn(vararg schemes: String): Context {
        val context = mockk<Context>()
        every { context.startActivity(any()) } answers {
            val uri = firstArg<Intent>().data.toString()
            launched += uri
            if (schemes.any { uri.startsWith(it) }) throw ActivityNotFoundException(uri)
        }
        return context
    }

    @Test
    fun `store app present opens market page only`() {
        val context = contextFailingOn()
        assertTrue(GmsAvailabilityChecker.openPlayServicesInStore(context))
        assertEquals(listOf("market://details?id=com.google.android.gms"), launched)
    }

    @Test
    fun `no store app falls back to web page`() {
        val context = contextFailingOn("market:")
        assertTrue(GmsAvailabilityChecker.openPlayServicesInStore(context))
        assertEquals(2, launched.size)
        assertTrue(launched[1].startsWith("https://play.google.com/"))
    }

    @Test
    fun `no store and no browser returns false without throwing`() {
        val context = contextFailingOn("market:", "https:")
        assertFalse(GmsAvailabilityChecker.openPlayServicesInStore(context))
        verify(exactly = 2) { context.startActivity(any()) }
    }
}
