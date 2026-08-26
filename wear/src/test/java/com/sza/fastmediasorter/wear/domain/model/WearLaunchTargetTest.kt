package com.sza.fastmediasorter.wear.domain.model

import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S1955: the launch wire format, tested from both ends at once.
 *
 * A writer and a reader that disagree produce no error of any kind - the app simply opens its home screen,
 * which is also what it does when nothing was requested. That silence is the reason these two live in one
 * file and are pinned by a round trip rather than by asserting on the extras they happen to use.
 *
 * `Intent` is mocked rather than constructed, which keeps this off Robolectric the way the phone module's
 * intent tests already do: the extras are a map, and a map is all the contract needs.
 */
class WearLaunchTargetTest {

    /** An `Intent` that remembers its extras, backed by [values] so a test can take them apart. */
    private fun intentBackedBy(values: MutableMap<String, Any?>): Intent {
        val intent = mockk<Intent>(relaxed = true)
        every { intent.putExtra(any<String>(), any<String>()) } answers {
            values[firstArg()] = arg<String?>(1)
            intent
        }
        every { intent.putExtra(any<String>(), any<Int>()) } answers {
            values[firstArg()] = arg<Int>(1)
            intent
        }
        every { intent.getStringExtra(any()) } answers { values[firstArg()] as? String }
        every { intent.getIntExtra(any(), any()) } answers { values[firstArg()] as? Int ?: arg(1) }
        return intent
    }

    private fun roundTrip(target: WearLaunchTarget): WearLaunchTarget? {
        val intent = intentBackedBy(mutableMapOf())
        target.writeTo(intent)
        return readWearLaunchTarget(intent)
    }

    private val resourceRef = WearTileTargetRef.Resource(
        id = "phone-42",
        type = NetworkSourceType.SMB,
        server = "192.168.1.100",
        port = 445,
        shareName = "Common",
        basePath = "/photos"
    )

    @Test
    fun `every launch target shape survives the round trip`() {
        val shapes = listOf(
            WearLaunchTarget.Pick(WearTileKind.RESOURCE),
            WearLaunchTarget.Pick(WearTileKind.STREAM),
            WearLaunchTarget.Pick(WearTileKind.FAVOURITES),
            WearLaunchTarget.Open(WearTileTargetRef.Favourites),
            WearLaunchTarget.Open(streamTargetRef("http://host.example/live")),
            WearLaunchTarget.Open(resourceRef)
        )

        shapes.forEach { shape ->
            assertEquals("$shape must read back as itself", shape, roundTrip(shape))
        }
    }

    @Test
    fun `a resource ref with no share name still survives the round trip`() {
        // shareName is null on every non-SMB resource, so its absence is data rather than a missing field.
        val shape = WearLaunchTarget.Open(resourceRef.copy(shareName = null))

        assertEquals(shape, roundTrip(shape))
    }

    @Test
    fun `an intent carrying no extras reads back nothing`() {
        assertNull(
            "an ordinary launch must not be mistaken for a request to open something",
            readWearLaunchTarget(intentBackedBy(mutableMapOf()))
        )
    }

    @Test
    fun `an intent carrying only some of the resource extras reads back nothing`() {
        val written = mutableMapOf<String, Any?>()
        WearLaunchTarget.Open(resourceRef).writeTo(intentBackedBy(written))
        // Drop one required field by its value, so the test does not restate the private extra keys.
        val truncated = written.filterValues { it != resourceRef.basePath }.toMutableMap()

        assertNull(
            "a half-filled target must resolve to nothing rather than to some other address",
            readWearLaunchTarget(intentBackedBy(truncated))
        )
    }
}
