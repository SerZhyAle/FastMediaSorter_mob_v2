package com.sza.fastmediasorter.ui.icon

import android.graphics.Color
import android.widget.ImageView
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S3080: pins the one claim the helper exists to make - a recolour is removed by removing it, never by
 * writing the view's tint list, because that write erases the `android:tint` the vector declares and no
 * later bind can put it back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class RecyclableIconTintTest {

    private fun view(): ImageView = ImageView(RuntimeEnvironment.getApplication())

    @Test
    fun `applies a colour filter for a colour`() {
        val target = view()

        RecyclableIconTint.apply(target, Color.RED)

        assertNotNull(target.colorFilter)
    }

    @Test
    fun `clears the colour filter for null`() {
        val target = view()
        RecyclableIconTint.apply(target, Color.RED)

        RecyclableIconTint.apply(target, null)

        assertNull(target.colorFilter)
    }

    @Test
    fun `never writes the image tint list`() {
        val target = view()

        RecyclableIconTint.apply(target, Color.RED)
        RecyclableIconTint.apply(target, null)

        assertNull(target.imageTintList)
    }
}
