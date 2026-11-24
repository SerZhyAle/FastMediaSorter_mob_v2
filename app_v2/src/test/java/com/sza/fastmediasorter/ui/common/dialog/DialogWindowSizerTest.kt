package com.sza.fastmediasorter.ui.common.dialog

import android.content.res.Resources
import android.util.DisplayMetrics
import com.sza.fastmediasorter.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class DialogWindowSizerTest {

    private val minWidthPx = 150
    private val maxWidthPx = 300
    private val wideMaxWidthPx = 560

    private fun resourcesWithScreenWidth(screenWidthPx: Int, sharedMaxPx: Int = maxWidthPx): Resources {
        val resources = mockk<Resources>()
        every { resources.getDimensionPixelSize(R.dimen.dialog_min_width) } returns minWidthPx
        every { resources.getDimensionPixelSize(R.dimen.dialog_max_width) } returns sharedMaxPx
        every { resources.getDimensionPixelSize(R.dimen.dialog_wide_max_width) } returns wideMaxWidthPx
        every { resources.displayMetrics } returns DisplayMetrics().apply { widthPixels = screenWidthPx }
        return resources
    }

    @Test
    fun `narrow phone screen clamps to the minimum width`() {
        val resources = resourcesWithScreenWidth(screenWidthPx = 100)

        assertEquals(minWidthPx, DialogWindowSizer.resolveWidthPx(resources))
    }

    @Test
    fun `standard portrait screen inside bounds is used as-is`() {
        val resources = resourcesWithScreenWidth(screenWidthPx = 250)

        assertEquals(250, DialogWindowSizer.resolveWidthPx(resources))
    }

    @Test
    fun `landscape phone screen clamps to the maximum width`() {
        val resources = resourcesWithScreenWidth(screenWidthPx = 640)

        assertEquals(maxWidthPx, DialogWindowSizer.resolveWidthPx(resources))
    }

    @Test
    fun `tablet w600dp screen clamps to the maximum width`() {
        val resources = resourcesWithScreenWidth(screenWidthPx = 1200)

        assertEquals(maxWidthPx, DialogWindowSizer.resolveWidthPx(resources))
    }

    @Test
    fun `screen width exactly at the minimum bound is unclamped`() {
        val resources = resourcesWithScreenWidth(screenWidthPx = minWidthPx)

        assertEquals(minWidthPx, DialogWindowSizer.resolveWidthPx(resources))
    }

    @Test
    fun `screen width exactly at the maximum bound is unclamped`() {
        val resources = resourcesWithScreenWidth(screenWidthPx = maxWidthPx)

        assertEquals(maxWidthPx, DialogWindowSizer.resolveWidthPx(resources))
    }

    @Test
    fun `wide dialog on a phone portrait screen uses the full screen width`() {
        val resources = resourcesWithScreenWidth(screenWidthPx = 411)

        assertEquals(411, DialogWindowSizer.resolveWidthPx(resources, wide = true))
    }

    @Test
    fun `wide dialog on a landscape screen clamps to the wide maximum`() {
        val resources = resourcesWithScreenWidth(screenWidthPx = 900)

        assertEquals(wideMaxWidthPx, DialogWindowSizer.resolveWidthPx(resources, wide = true))
    }

    @Test
    fun `wide dialog never lowers a larger shared maximum`() {
        val resources = resourcesWithScreenWidth(screenWidthPx = 1200, sharedMaxPx = 720)

        assertEquals(720, DialogWindowSizer.resolveWidthPx(resources, wide = true))
    }
}
