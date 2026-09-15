package com.sza.fastmediasorter.core.network

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LanAddressResolverTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mockk<Context>(relaxed = true)
    }

    @Test
    fun `resolver does not crash when active network is null or missing permissions`() {
        val resolver = LanAddressResolver(context)
        val result = resolver.resolve()
        // Result may be null or a real IP depending on system test environment, but must not crash or throw
        if (result != null) {
            assertTrue(result.isNotEmpty())
        }
    }
}
