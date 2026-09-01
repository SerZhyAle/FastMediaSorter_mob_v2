package com.sza.fastmediasorter.ui.launcher.signal

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import android.widget.ImageView
import com.sza.fastmediasorter.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherSignalIconBinderTest {

    @Test
    fun `adaptive application icon draws its foreground only`() {
        val foreground = ColorDrawable(Color.GREEN)
        val adaptive = AdaptiveIconDrawable(ColorDrawable(Color.BLUE), foreground)

        assertSame(foreground, LauncherSignalIconBinder.applicationGlyph(adaptive))
    }

    @Test
    fun `legacy application icon passes through unchanged`() {
        val legacy = ColorDrawable(Color.MAGENTA)

        assertSame(legacy, LauncherSignalIconBinder.applicationGlyph(legacy))
    }

    @Test
    fun `application glyph clears tint and fills the cell`() {
        val target = imageView(padding = BASE_PADDING)
        target.imageTintList = ColorStateList.valueOf(Color.WHITE)

        LauncherSignalIconBinder.bindApplicationDrawable(target, ColorDrawable(Color.GREEN))

        assertNull(target.imageTintList)
        assertEquals(0, target.paddingLeft)
    }

    @Test
    fun `resource glyph restores captured padding after recycled application glyph`() {
        val target = imageView(padding = BASE_PADDING)

        LauncherSignalIconBinder.bindApplicationDrawable(target, ColorDrawable(Color.GREEN))
        LauncherSignalIconBinder.bind(target, LauncherSignalIcon.Resource(R.drawable.ic_apps))

        assertEquals(BASE_PADDING, target.paddingLeft)
        assertEquals(BASE_PADDING, target.paddingTop)
    }

    @Test
    fun `missing application package keeps the resource fallback`() {
        val resolved = LauncherSignalIconBinder.resolve(
            RuntimeEnvironment.getApplication(),
            LauncherSignalIcon.Application(MISSING_PACKAGE, R.drawable.ic_apps),
        )

        assertEquals(LauncherSignalIconBinder.Resolved.FromResource(R.drawable.ic_apps), resolved)
    }

    // Theme.FastMediaSorter and not merely "some Material theme": it is the <application> theme, and
    // LauncherHomeActivity declares none of its own, so it is what the binder reads colorOnSurface from in
    // the live launcher. The application context alone carries the system default theme, where that
    // attribute does not exist and MaterialColors.getColor throws.
    private fun imageView(padding: Int): ImageView {
        val themed = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_FastMediaSorter)
        return ImageView(themed).apply {
            setPadding(padding, padding, padding, padding)
        }
    }

    private companion object {
        const val BASE_PADDING = 9
        const val MISSING_PACKAGE = "com.example.s2244.missing"
    }
}
