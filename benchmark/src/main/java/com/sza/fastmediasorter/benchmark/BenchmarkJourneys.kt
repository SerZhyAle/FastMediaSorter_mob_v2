package com.sza.fastmediasorter.benchmark

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

object BenchmarkJourneys {
    private const val ACTION_OPEN_JOURNEY = "com.sza.fastmediasorter.perf.action.OPEN_JOURNEY"
    private const val ROUTE_ACTIVITY = "com.sza.fastmediasorter.perf.BenchmarkRouteActivity"
    private const val EXTRA_JOURNEY = "benchmark_journey"
    private const val JOURNEY_BROWSE = "browse"
    private const val JOURNEY_PLAYER = "player"
    private const val BROWSE_LIST_RES = "rvMediaFiles"
    private const val PLAYER_CONTAINER_RES = "mediaContentArea"
    private const val SELECT_ALL_RES = "btnSelectAll"
    private const val DESELECT_ALL_RES = "btnDeselectAll"
    private const val UI_TIMEOUT_MS = 10_000L

    /**
     * Visible rows the interaction journeys need before a measurement means anything: a list shorter
     * than this pays none of the per-row bind cost the journeys exist to measure.
     */
    const val MIN_INTERACTION_ROWS = 4

    // UiAutomator otherwise starts a fling on the very edge of the list, where the system gesture
    // navigation area consumes the gesture and the measured window holds no scroll at all.
    private const val LIST_GESTURE_MARGIN_PX = 80
    private const val DRAG_STEPS = 40

    fun MacrobenchmarkScope.openBrowseJourney() {
        startJourney(JOURNEY_BROWSE)
        waitForBrowseScreen()
    }

    fun MacrobenchmarkScope.openPlayerJourney() {
        startJourney(JOURNEY_PLAYER)
        waitForPlayerScreen()
    }

    fun MacrobenchmarkScope.returnToBrowseFromPlayer() {
        device.pressBack()
        waitForBrowseScreen()
    }

    fun MacrobenchmarkScope.waitForBrowseScreen() {
        waitForView(BROWSE_LIST_RES)
    }

    fun MacrobenchmarkScope.requireBrowseListRows(min: Int) {
        val rows = browseList().childCount
        check(rows >= min) {
            "Browse list shows $rows visible row(s), the interaction journeys need $min. " +
                "Run against a folder holding more files."
        }
    }

    fun MacrobenchmarkScope.flingBrowseList(passes: Int) {
        val list = browseList()
        list.setGestureMargin(LIST_GESTURE_MARGIN_PX)
        repeat(passes) {
            list.fling(Direction.DOWN)
            device.waitForIdle()
            list.fling(Direction.UP)
            device.waitForIdle()
        }
    }

    fun MacrobenchmarkScope.selectAllThenDeselect() {
        tapControl(SELECT_ALL_RES)
        tapControl(DESELECT_ALL_RES)
    }

    fun MacrobenchmarkScope.dragSelectBrowseList() {
        val rows = browseList().children
        check(rows.size >= MIN_INTERACTION_ROWS) {
            "Browse list shows ${rows.size} visible row(s), drag-select needs $MIN_INTERACTION_ROWS."
        }
        // Bounds are read before the long press: entering multi-select rebinds the rows, which
        // invalidates every UiObject2 taken from the pre-press hierarchy.
        val firstBounds = rows.first().visibleBounds
        val lastBounds = rows.last().visibleBounds
        rows.first().longClick()
        device.waitForIdle()
        // Drag-select only engages while a selection is already active, which the long press above
        // seeds - without it the same gesture is an ordinary scroll and measures the wrong path.
        device.drag(
            firstBounds.centerX(),
            firstBounds.centerY(),
            lastBounds.centerX(),
            lastBounds.centerY(),
            DRAG_STEPS
        )
        device.waitForIdle()
    }

    fun MacrobenchmarkScope.waitForPlayerScreen() {
        waitForView(PLAYER_CONTAINER_RES)
    }

    private fun MacrobenchmarkScope.startJourney(journey: String) {
        val intent = Intent(ACTION_OPEN_JOURNEY).apply {
            setClassName(BenchmarkAppSetup.TARGET_PACKAGE, ROUTE_ACTIVITY)
            putExtra(EXTRA_JOURNEY, journey)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivityAndWait(intent)
    }

    private fun MacrobenchmarkScope.browseList(): UiObject2 =
        requireView(BROWSE_LIST_RES)

    private fun MacrobenchmarkScope.tapControl(resId: String) {
        requireView(resId).click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.requireView(resId: String): UiObject2 {
        val view = device.wait(
            Until.findObject(By.res(BenchmarkAppSetup.TARGET_PACKAGE, resId)),
            UI_TIMEOUT_MS
        )
        return checkNotNull(view) { "Timed out waiting for benchmark view '$resId'" }
    }

    private fun MacrobenchmarkScope.waitForView(resId: String) {
        check(device.wait(Until.hasObject(By.res(BenchmarkAppSetup.TARGET_PACKAGE, resId)), UI_TIMEOUT_MS)) {
            "Timed out waiting for benchmark view '$resId'"
        }
    }
}
