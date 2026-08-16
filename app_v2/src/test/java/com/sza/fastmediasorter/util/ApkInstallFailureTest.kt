package com.sza.fastmediasorter.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S1686: the installer's refusal used to be reported as one opaque string, and the cause was recoverable
 * only by inspecting the APK and the device by hand. This classification is what makes it readable from a
 * log alone, so it is the half of the fix that has to hold without a device.
 */
class ApkInstallFailureTest {

    @Test
    fun `a downgrade is recognised`() {
        assertEquals(ApkInstallFailure.VERSION_DOWNGRADE, ApkInstallFailure.fromLegacyStatus(-25))
    }

    @Test
    fun `an incompatible update is recognised`() {
        assertEquals(ApkInstallFailure.UPDATE_INCOMPATIBLE, ApkInstallFailure.fromLegacyStatus(-7))
    }

    @Test
    fun `insufficient storage is recognised`() {
        assertEquals(ApkInstallFailure.INSUFFICIENT_STORAGE, ApkInstallFailure.fromLegacyStatus(-4))
    }

    @Test
    fun `every parse failure in the range reads as a corrupt package`() {
        for (status in -108..-100) {
            assertEquals(
                "status $status",
                ApkInstallFailure.CORRUPT_PACKAGE,
                ApkInstallFailure.fromLegacyStatus(status)
            )
        }
    }

    @Test
    fun `a status just outside the parse range is not a corrupt package`() {
        assertEquals(ApkInstallFailure.UNKNOWN, ApkInstallFailure.fromLegacyStatus(-99))
        assertEquals(ApkInstallFailure.UNKNOWN, ApkInstallFailure.fromLegacyStatus(-109))
    }

    @Test
    fun `an unrecognised status is unknown`() {
        assertEquals(ApkInstallFailure.UNKNOWN, ApkInstallFailure.fromLegacyStatus(-999))
    }

    @Test
    fun `an absent extra is unknown`() {
        assertEquals(
            ApkInstallFailure.UNKNOWN,
            ApkInstallFailure.fromLegacyStatus(ApkInstallFailure.NO_STATUS)
        )
    }
}
