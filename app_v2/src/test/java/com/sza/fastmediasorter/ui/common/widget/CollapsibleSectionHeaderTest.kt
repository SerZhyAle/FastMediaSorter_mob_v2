package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.dialog.TooltipDialog
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CollapsibleSectionHeaderTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_FastMediaSorter_App)
        mockkObject(TooltipDialog)
        every { TooltipDialog.show(any(), any<String>(), any<String>()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(TooltipDialog)
    }

    @Test
    fun `chevron rotation differs between expanded and collapsed`() {
        val header = newHeader()
        val chevron = header.findViewById<ImageView>(R.id.csh_chevron)
        val titleView = header.findViewById<TextView>(R.id.csh_title)

        header.setTitle("Authorization")
        header.setExpanded(true, notify = false)
        val expandedRotation = chevron.rotation
        assertEquals("Authorization", titleView.text.toString())

        header.setExpanded(false, notify = false)
        val collapsedRotation = chevron.rotation

        assertNotEquals(expandedRotation, collapsedRotation)
    }

    @Test
    fun `setSummary shows and hides the summary slot`() {
        val header = newHeader()
        val summary = header.findViewById<TextView>(R.id.csh_summary)

        assertEquals(View.GONE, summary.visibility)

        header.setSummary("1.2 GB - 3 files")
        assertEquals(View.VISIBLE, summary.visibility)
        assertEquals("1.2 GB - 3 files", summary.text.toString())

        header.setSummary(null)
        assertEquals(View.GONE, summary.visibility)
    }

    @Test
    fun `state description reflects expanded and collapsed`() {
        val header = newHeader()
        val headerRow = header.findViewById<View>(R.id.csh_headerRow)

        header.setExpanded(true, notify = false)
        assertEquals(
            context.getString(R.string.collapsible_section_state_expanded),
            ViewCompat.getStateDescription(headerRow)?.toString()
        )

        header.setExpanded(false, notify = false)
        assertEquals(
            context.getString(R.string.collapsible_section_state_collapsed),
            ViewCompat.getStateDescription(headerRow)?.toString()
        )
    }

    @Test
    fun `listener fires only on actual state change`() {
        val header = newHeader()
        var callCount = 0

        header.setOnExpandedChangeListener { callCount++ }

        header.setExpanded(true)
        header.setExpanded(true)

        assertEquals(1, callCount)
    }

    @Test
    fun `custom expand collapse content descriptions follow state`() {
        val header = newHeader()
        val headerRow = header.findViewById<View>(R.id.csh_headerRow)

        header.setTitle("Duplicates")
        header.setExpandCollapseContentDescriptions(R.string.cd_expand_group, R.string.cd_collapse_group)
        assertEquals(context.getString(R.string.cd_expand_group), headerRow.contentDescription)

        header.setExpanded(true, notify = false)
        assertEquals(context.getString(R.string.cd_collapse_group), headerRow.contentDescription)
    }

    @Test
    fun `help is hidden by default`() {
        val header = newHeader()

        assertEquals(View.GONE, header.findViewById<ImageButton>(R.id.csh_iconHelp).visibility)
        assertFalse(header.isHelpVisible)
    }

    @Test
    fun `help becomes visible after setHelp and click opens tooltip`() {
        val header = newHeader()
        val helpIcon = header.findViewById<ImageButton>(R.id.csh_iconHelp)

        header.setHelp(R.string.tooltip_saved_authorizations_title, R.string.tooltip_saved_authorizations_message)

        assertEquals(View.VISIBLE, helpIcon.visibility)
        assertEquals(
            context.getString(R.string.tooltip_saved_authorizations_title),
            helpIcon.contentDescription.toString()
        )

        helpIcon.performClick()

        verify(exactly = 1) {
            TooltipDialog.show(
                any(),
                context.getString(R.string.tooltip_saved_authorizations_title),
                context.getString(R.string.tooltip_saved_authorizations_message)
            )
        }
    }

    @Test
    fun `virtual mode drops prefix and click affordance`() {
        val header = newHeader()
        val titleView = header.findViewById<TextView>(R.id.csh_title)
        val headerRow = header.findViewById<View>(R.id.csh_headerRow)

        header.setTitle("About")
        header.setExpanded(true, notify = false)
        header.setVirtual(true)

        assertEquals("About", titleView.text.toString())
        assertFalse(headerRow.isClickable)
        assertFalse(headerRow.isFocusable)
        assertEquals(null, headerRow.background)
    }

    @Test
    fun `trailing slot injects and removes control`() {
        val header = newHeader()
        val trailingSlot = header.findViewById<FrameLayout>(R.id.csh_trailingSlot)
        val trailingView = View(context)

        header.setTrailingControl(trailingView)
        assertEquals(View.VISIBLE, trailingSlot.visibility)
        assertEquals(1, trailingSlot.childCount)
        assertSame(trailingView, trailingSlot.getChildAt(0))

        header.setTrailingControl(null)
        assertEquals(View.GONE, trailingSlot.visibility)
        assertEquals(0, trailingSlot.childCount)
    }

    @Test
    fun `root click toggles expanded state`() {
        val header = newHeader()
        val headerRow = header.findViewById<View>(R.id.csh_headerRow)

        assertFalse(header.isExpanded())
        headerRow.performClick()
        assertTrue(header.isExpanded())
    }

    private fun newHeader(): CollapsibleSectionHeader = CollapsibleSectionHeader(context)
}
