package com.sza.fastmediasorter.ui.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.sza.fastmediasorter.data.network.datasource.TsPacketFormat
import com.sza.fastmediasorter.data.network.datasource.TsPacketFormatDetector
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class BdTsPlaybackInstrumentationTest {

    @Test
    fun tsPacketDetector_classifiesMinimalTsAsStandard188() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val probe = context.assets.open("test_media/minimal.ts").use { it.readBytes() }

        val result = TsPacketFormatDetector.detect(probe)

        assertEquals(TsPacketFormat.STANDARD_188, result)
    }
}
