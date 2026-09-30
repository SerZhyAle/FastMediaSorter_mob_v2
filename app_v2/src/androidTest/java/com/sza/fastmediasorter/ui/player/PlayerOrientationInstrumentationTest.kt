package com.sza.fastmediasorter.ui.player

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.sza.fastmediasorter.domain.model.SyntheticResourceIds
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class PlayerOrientationInstrumentationTest {

    @Test
    fun recreate_doesNotCrash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // A launch without media finishes the player by design (unknown resource -> FinishActivity),
        // which races recreate(). The synthetic stream resource needs no database row and keeps an
        // ad-hoc URL as a one-item list, so the activity stays alive; a closed loopback port fails
        // playback, not the activity.
        val intent = PlayerActivity.createIntent(
            context = context,
            resourceId = SyntheticResourceIds.STREAM,
            skipAvailabilityCheck = true,
            initialFilePath = UNREACHABLE_STREAM_URL,
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val scenario = ActivityScenario.launch<PlayerActivity>(intent)
        try {
            scenario.recreate()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        } finally {
            scenario.close()
        }
    }

    private companion object {
        const val UNREACHABLE_STREAM_URL = "http://127.0.0.1:9/recreate-probe.mp4"
    }
}
