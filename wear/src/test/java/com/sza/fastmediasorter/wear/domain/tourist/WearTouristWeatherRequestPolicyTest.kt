package com.sza.fastmediasorter.wear.domain.tourist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearTouristWeatherRequestPolicyTest {

    private val policy = WearTouristWeatherRequestPolicy
    private val home = WearWeatherPosition(50.45, 30.52)

    @Test
    fun `position is rounded to two decimals`() {
        val rounded = policy.round(50.45678, -30.52349)

        assertEquals(50.46, rounded.latitude, 1e-9)
        assertEquals(-30.52, rounded.longitude, 1e-9)
    }

    @Test
    fun `first position asks at once`() {
        assertTrue(policy.shouldRequest(null, null, home, nowMs = 0L))
    }

    @Test
    fun `same place inside the interval does not ask again`() {
        val now = policy.REQUEST_INTERVAL_MS - 1
        assertFalse(policy.shouldRequest(home, 0L, home, now))
    }

    @Test
    fun `same place after the interval asks again`() {
        assertTrue(policy.shouldRequest(home, 0L, home, policy.REQUEST_INTERVAL_MS))
    }

    @Test
    fun `a move past the threshold asks inside the interval`() {
        // 0.03 degrees of latitude is about 3.3 km.
        val moved = WearWeatherPosition(home.latitude + 0.03, home.longitude)
        assertTrue(policy.shouldRequest(home, 0L, moved, nowMs = 1L))
    }

    @Test
    fun `a move below the threshold does not ask inside the interval`() {
        // 0.01 degrees of latitude is about 1.1 km.
        val moved = WearWeatherPosition(home.latitude + 0.01, home.longitude)
        assertFalse(policy.shouldRequest(home, 0L, moved, nowMs = 1L))
    }

    @Test
    fun `an answer up to an hour old is fresh and older is not`() {
        assertTrue(policy.isFresh(observedAtMs = 0L, nowMs = policy.MAX_AGE_MS))
        assertFalse(policy.isFresh(observedAtMs = 0L, nowMs = policy.MAX_AGE_MS + 1))
    }
}
