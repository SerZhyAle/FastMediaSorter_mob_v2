package com.sza.fastmediasorter.staterestore

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferRequest
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferRequestStore
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferSource
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferTerminalPayload
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.scheduledops.ScheduledOperationsActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The critical rows of `docs/STATE_RESTORE_MATRIX.md`, executed (S3371 phase 07).
 *
 * The matrix declares a fate per screen and per long operation for rotation, process death,
 * relaunch and cancellation. A declared fate that nothing runs is an opinion, so every row marked
 * critical there has a method here, and each method reproduces the fate rather than the code path
 * that happens to implement it today.
 *
 * Process death is reproduced where it actually bites: the state that has to survive it is on
 * disk, and what a kill destroys is the instance holding it. So the transfer scenarios build a
 * SECOND store over the same directory instead of killing the process - a fresh instance reading
 * the same files is exactly what the restarted process does, and it can be asserted inside one
 * instrumentation run. The store is pointed at a temporary directory rather than the app's own
 * `filesDir`, because a suite that wrote into the real transfer queue could cancel a transfer the
 * device owner started.
 *
 * Two of the four rows - main browse and player - cannot be launched without a media resource
 * present on the device, and a scenario that quietly passes on an empty device is the unobserved
 * green this whole contour exists to end. What is asserted for them instead is the identity key
 * both screens save and restore; the fuller rotation walk belongs with the seeded content the
 * matrix's long-tail step describes.
 *
 * The suite is placed in its own package so the nightly device loop selects it by placement:
 * `.github/workflows/nightly-device-loop.yml` names the package, never the class list.
 */
@RunWith(AndroidJUnit4::class)
class CriticalStateRestoreTest {

    private lateinit var storeDirectory: File
    private lateinit var storeContext: Context

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        storeDirectory = File(instrumentation.targetContext.cacheDir, "state-restore-${System.nanoTime()}")
        check(storeDirectory.mkdirs()) { "could not create the isolated files directory for the transfer store" }
        storeContext = IsolatedFilesContext(instrumentation.targetContext, storeDirectory)
    }

    @After
    fun tearDown() {
        storeDirectory.deleteRecursively()
    }

    /**
     * Matrix row "Scheduled operations / Rotation: preserved". The screen owns no state of its
     * own - it renders flows the repository republishes - so the proof is that a recreation
     * produces a NEW Activity that reaches RESUMED with the same content available to it. A
     * recreation that throws, or one that lands anywhere but RESUMED, is the regression.
     */
    @Test
    fun scheduledOperationsScreenRebuildsItselfAfterRecreation() {
        ActivityScenario.launch(ScheduledOperationsActivity::class.java).use { scenario ->
            val beforeRecreation = currentActivityOf(scenario)
            scenario.recreate()
            val afterRecreation = currentActivityOf(scenario)

            assertNotSame(
                "recreation returned the same Activity instance, so nothing was actually rebuilt",
                beforeRecreation,
                afterRecreation,
            )
            assertEquals(
                "the scheduled-operations screen did not come back to RESUMED after recreation",
                Lifecycle.State.RESUMED,
                scenario.state,
            )
        }
    }

    /**
     * Matrix row "Transfer strip / Process death: preserved". The request is written as JSON under
     * the app's files directory precisely so it outlives the process that enqueued it; a second
     * store over the same directory is what the restarted process sees.
     */
    @Test
    fun transferRequestSurvivesTheProcessThatEnqueuedIt() {
        val request = sampleRequest(destinationName = "Sorted")
        BrowseFileTransferRequestStore(storeContext, Gson()).enqueueRequest(request)

        val afterRestart = BrowseFileTransferRequestStore(storeContext, Gson())
        assertTrue(
            "the queue was empty after the restart, so an interrupted transfer would be lost silently",
            afterRestart.hasPendingRequests(),
        )
        val restored = afterRestart.pollNextRequest()
        assertNotNull("the restarted process could not read back the enqueued request", restored)
        assertEquals(request.destinationPath, restored?.destinationPath)
        assertEquals(request.sources.single().path, restored?.sources?.single()?.path)
    }

    /**
     * Matrix row "Transfer strip / Relaunch: preserved, including a transfer that finished while
     * the app was gone". The terminal event is stored rather than emitted and forgotten, and it is
     * delivered ONCE - a second read must find nothing, or a relaunch would re-announce a
     * completion the user already saw.
     */
    @Test
    fun transferOutcomeIsDeliveredOnceAfterRelaunch() {
        BrowseFileTransferRequestStore(storeContext, Gson()).writeTerminalEvent(
            BrowseFileTransferTerminalPayload(
                kind = "success",
                workId = "state-restore-work",
                operationType = FileOperationType.COPY,
                processedCount = 3,
            ),
        )

        val afterRelaunch = BrowseFileTransferRequestStore(storeContext, Gson())
        val delivered = afterRelaunch.consumeTerminalEvent()
        assertNotNull("the stored outcome did not survive the relaunch", delivered)
        assertEquals("state-restore-work", delivered?.workId)
        assertNull(
            "the outcome was delivered twice, so a relaunch would re-announce a finished transfer",
            BrowseFileTransferRequestStore(storeContext, Gson()).consumeTerminalEvent(),
        )
    }

    /**
     * Matrix row "Transfer strip / Cancellation". Cancelling ends the work and leaves nothing
     * queued behind it: a request the user cancelled must not be picked up again by the next
     * launch, which is the difference between a cancellation and a pause.
     */
    @Test
    fun cancellingATransferLeavesNothingQueuedBehind() {
        val store = BrowseFileTransferRequestStore(storeContext, Gson())
        store.enqueueRequest(sampleRequest(destinationName = "First"))
        store.enqueueRequest(sampleRequest(destinationName = "Second"))
        store.writeTerminalEvent(
            BrowseFileTransferTerminalPayload(
                kind = "cancelled",
                workId = "state-restore-cancelled",
                operationType = FileOperationType.MOVE,
            ),
        )

        store.clearAll()

        val afterCancel = BrowseFileTransferRequestStore(storeContext, Gson())
        assertFalse(
            "a cancelled transfer left work queued, so the next launch would resume it",
            afterCancel.hasPendingRequests(),
        )
        assertNull("a cancelled transfer left a terminal event to replay", afterCancel.consumeTerminalEvent())
    }

    /**
     * Matrix rows "Main browse / Process death" and "Player / Process death". Both screens are
     * windows of the same multi-window system: each writes its window id into the saved-instance
     * Bundle and reads it back in `onCreate`, falling back to the launch intent. The two sides
     * agree only because they spell the same key, and a rename on one side alone is silent - the
     * window simply loses its identity after a kill and reopens as a new one.
     */
    @Test
    fun browseAndPlayerRestoreTheSameWindowIdentityKey() {
        assertEquals(
            "the browse and player screens no longer save their window identity under the same key",
            BrowseActivity.EXTRA_WINDOW_ID,
            PlayerActivity.EXTRA_WINDOW_ID,
        )
    }

    private fun <A : Activity> currentActivityOf(scenario: ActivityScenario<A>): A {
        lateinit var captured: A
        scenario.onActivity { captured = it }
        return captured
    }

    private fun sampleRequest(destinationName: String) = BrowseFileTransferRequest(
        operationType = FileOperationType.COPY,
        sourceResourceId = 1L,
        sourceResourceName = "State restore fixture",
        sourceCredentialsId = null,
        currentBrowsePath = "/fixture",
        destinationPath = "/fixture/$destinationName",
        destinationName = destinationName,
        overwriteFiles = false,
        sources = listOf(
            BrowseFileTransferSource(
                path = "/fixture/source.jpg",
                displayName = "source.jpg",
                size = 1L,
                isDirectory = false,
            ),
        ),
    )

    /**
     * Keeps the transfer store off the app's real queue. Only `filesDir` is consulted by the
     * store, so overriding it is enough to give each run its own disk.
     */
    private class IsolatedFilesContext(base: Context, private val isolatedDir: File) : ContextWrapper(base) {
        override fun getFilesDir(): File = isolatedDir
    }
}
