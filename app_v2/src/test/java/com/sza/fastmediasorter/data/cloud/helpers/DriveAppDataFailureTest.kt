package com.sza.fastmediasorter.data.cloud.helpers

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S4231: Drive error bodies to the failure classes items CQ and CS act on. Robolectric for real org.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DriveAppDataFailureTest {

    @Test
    fun `rate limits are transient`() {
        assertTrue(classify(FORBIDDEN, "rateLimitExceeded") is DriveAppDataFailure.RateLimited)
        assertTrue(classify(FORBIDDEN, "userRateLimitExceeded") is DriveAppDataFailure.RateLimited)
        assertTrue(DriveAppDataFailure.classify(TOO_MANY, null) is DriveAppDataFailure.RateLimited)
        assertTrue(DriveAppDataFailure.classify(TOO_MANY, null).isTransient)
    }

    @Test
    fun `the daily limit is its own class and not transient`() {
        val daily = classify(FORBIDDEN, "dailyLimitExceeded")
        assertTrue(daily is DriveAppDataFailure.DailyLimit)
        assertTrue(!daily.isTransient)
        assertTrue(classify(FORBIDDEN, "uploadLimitExceeded") is DriveAppDataFailure.DailyLimit)
    }

    @Test
    fun `a full drive is storage full`() {
        assertTrue(classify(FORBIDDEN, "storageQuotaExceeded") is DriveAppDataFailure.StorageFull)
    }

    @Test
    fun `status codes without a reason`() {
        assertTrue(DriveAppDataFailure.classify(UNAUTHORIZED, null) is DriveAppDataFailure.AuthRequired)
        assertTrue(DriveAppDataFailure.classify(NOT_FOUND, null) is DriveAppDataFailure.NotFound)
        assertTrue(DriveAppDataFailure.classify(SERVER, null) is DriveAppDataFailure.ServerError)
        assertTrue(DriveAppDataFailure.classify(UNAVAILABLE, "not json") is DriveAppDataFailure.ServerError)
        assertTrue(DriveAppDataFailure.classify(null, null) is DriveAppDataFailure.Network)
        assertTrue(DriveAppDataFailure.classify(FORBIDDEN, "not json") is DriveAppDataFailure.Other)
    }

    private fun classify(code: Int, reason: String): DriveAppDataFailure =
        DriveAppDataFailure.classify(code, """{"error":{"errors":[{"reason":"$reason"}],"code":$code}}""")

    private companion object {
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val TOO_MANY = 429
        const val SERVER = 500
        const val UNAVAILABLE = 503
    }
}
