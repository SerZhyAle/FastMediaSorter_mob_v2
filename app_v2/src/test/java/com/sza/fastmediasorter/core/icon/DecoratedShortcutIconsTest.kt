package com.sza.fastmediasorter.core.icon

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.core.graphics.drawable.IconCompat
import com.sza.fastmediasorter.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The `legacy` edition reaches API 25, where app shortcuts exist but adaptive icons do not, so its only
 * decorated shortcut is the bitmap branch and the three layer-list fallbacks - no test device runs them.
 */
@RunWith(RobolectricTestRunner::class)
class DecoratedShortcutIconsTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    @Config(sdk = [34])
    fun `API 25 publishes a bitmap icon, not an adaptive one`() {
        val icon = DecoratedShortcutIcons.forGlyph(
            context,
            R.drawable.ic_calculator,
            R.color.color_icon_plate_accent,
            API_25,
        )

        assertEquals(IconCompat.TYPE_BITMAP, icon.type)
    }

    @Test
    @Config(sdk = [34])
    fun `API 26 and later publish an adaptive icon`() {
        val icon = DecoratedShortcutIcons.forGlyph(context, R.drawable.ic_calculator, R.color.color_icon_plate_accent)

        assertEquals(IconCompat.TYPE_ADAPTIVE_BITMAP, icon.type)
    }

    @Test
    @Config(sdk = [34])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `the API 25 shape is a 44 dp circle in a 48 dp asset`() {
        val bitmap = DecoratedShortcutIcons.render(1f, 48f, 44f, 24f, PLATE, ColorDrawable(Color.TRANSPARENT))

        assertEquals(48, bitmap.width)
        assertEquals(PLATE, bitmap.getPixel(24, 24))
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
    }

    /**
     * The API 25 fallbacks cannot be inflated here: Robolectric refuses SDK 25 for an APK built at minSdk 26,
     * and at any later SDK the drawable-anydpi-v26 twin wins. Their shape is read from the source instead;
     * aapt has already linked them in the resource build.
     */
    @Test
    fun `the static shortcut fallbacks are a 44 dp circle and a 24 dp glyph in 48 dp`() {
        listOf("favorites", "slideshow", "broadcast").forEach { name ->
            val xml = File("src/main/res/drawable/ic_shortcut_$name.xml").readText()
            assertTrue(name, xml.contains("<layer-list"))
            assertTrue(name, Regex("""android:width="48dp"\s+android:height="48dp"""").containsMatchIn(xml))
            assertTrue(name, Regex("""android:width="44dp"\s+android:height="44dp"""").containsMatchIn(xml))
            assertTrue(name, xml.contains("android:shape=\"oval\""))
            assertTrue(name, Regex("""android:width="24dp"\s+android:height="24dp"""").containsMatchIn(xml))
        }
    }
    private companion object {
        const val PLATE = 0xFF1976D2.toInt()
        const val API_25 = 25
    }
}
