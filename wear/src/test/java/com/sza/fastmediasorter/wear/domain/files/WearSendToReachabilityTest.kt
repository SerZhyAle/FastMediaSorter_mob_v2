package com.sza.fastmediasorter.wear.domain.files

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.sza.fastmediasorter.wear.domain.model.WearSendToReceiverEntry
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2142 step 03.6: pins the finding of `research/04` - a stock Wear OS answers ACTION_SEND with a
 * system stub, so "an activity resolved" is not the same as "a receiver exists here".
 *
 * Both directions of the one-way rule are pinned, because the harm differs: a stub counted as a
 * handler offers a receiver that ends in a refusal, and a phone receiver hidden for being far away
 * changes the length of the list depending on which room the phone is in.
 *
 * The `Intent` is mocked rather than constructed, for the reason `WearLaunchTargetTest` records: this
 * module carries no Robolectric, so a real `Intent` here throws "not mocked" out of the android.jar
 * stub instead of testing anything.
 */
class WearSendToReachabilityTest {

    private fun reachability(vararg resolvedPackages: String): WearSendToReachability {
        val packageManager = mockk<PackageManager>()
        val resolved = resolvedPackages.map { packageName ->
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply { this.packageName = packageName }
            }
        }
        every { packageManager.queryIntentActivities(any<Intent>(), any<Int>()) } returns resolved
        val context = mockk<Context>()
        every { context.packageManager } returns packageManager
        return WearSendToReachability(context)
    }

    private fun entry(servedOnWatch: Boolean) = WearSendToReceiverEntry(
        id = "email",
        title = "Email",
        servedOnWatch = servedOnWatch
    )

    @Test
    fun `a watch-served receiver whose only resolver is the stub falls back to the phone`() {
        val reachability = reachability(STUB_PACKAGE)

        assertFalse(reachability.isServedHere(entry(servedOnWatch = true), sendIntent))
    }

    @Test
    fun `a watch-served receiver with a real handler is served here`() {
        val reachability = reachability(STUB_PACKAGE, "com.example.mail")

        assertTrue(reachability.isServedHere(entry(servedOnWatch = true), sendIntent))
    }

    @Test
    fun `a watch-served receiver nothing resolves falls back to the phone`() {
        val reachability = reachability()

        assertFalse(reachability.isServedHere(entry(servedOnWatch = true), sendIntent))
    }

    /**
     * The rule never raises: a receiver the phone did not mark stays a phone receiver even when this
     * watch has an application that would take the intent.
     */
    @Test
    fun `a receiver the phone did not mark is never raised to local service`() {
        val reachability = reachability("com.example.mail")

        assertFalse(reachability.isServedHere(entry(servedOnWatch = false), sendIntent))
    }

    private val sendIntent: Intent get() = mockk(relaxed = true)

    private companion object {
        const val STUB_PACKAGE = "com.google.android.wearable.frameworkpackagestubs"
    }
}
