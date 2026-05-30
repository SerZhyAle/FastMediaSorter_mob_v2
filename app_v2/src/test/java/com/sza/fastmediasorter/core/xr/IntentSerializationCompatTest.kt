package com.sza.fastmediasorter.core.xr

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IntentSerializationCompatTest {

    @Test
    fun `readSerializableExtraCompat returns typed serializable extra`() {
        val input = VrLaunchInput(
            launchMode = VrLaunchMode.FILE_URI,
            fileUriString = "content://media/item/1",
            mediaType = VrMediaType.VIDEO,
        )
        val intent = Intent().putExtra(VrLaunchInput.EXTRA_LAUNCH_INPUT, input)

        val result = intent.readSerializableExtraCompat<VrLaunchInput>(VrLaunchInput.EXTRA_LAUNCH_INPUT)

        assertEquals(input, result)
    }

    @Test
    fun `readSerializableExtraCompat returns null for missing extra`() {
        val result = Intent().readSerializableExtraCompat<VrLaunchInput>(VrLaunchInput.EXTRA_LAUNCH_INPUT)

        assertNull(result)
    }
}