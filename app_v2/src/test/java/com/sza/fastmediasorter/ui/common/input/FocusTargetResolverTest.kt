package com.sza.fastmediasorter.ui.common.input

import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FocusTargetResolverTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun nullTarget_returnsNull() {
        assertNull(FocusTargetResolver.resolveToLeafFocusable(null))
    }

    @Test
    fun leafControl_returnedAsIs() {
        val button = Button(context)
        assertSame(button, FocusTargetResolver.resolveToLeafFocusable(button))
    }

    @Test
    fun scrollViewWrappingControls_returnsFirstNonEditTextControl() {
        val edit = EditText(context)
        val button = Button(context)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(edit)
            addView(button)
        }
        val scroll = ScrollView(context).apply { addView(column) }

        // EditText is skipped (would pop the soft keyboard); the Button is the first real control.
        assertSame(button, FocusTargetResolver.resolveToLeafFocusable(scroll))
    }

    @Test
    fun fragmentHostFrameLayout_skipsEditText_returnsButton() {
        val edit = EditText(context)
        val button = Button(context)
        val host = FrameLayout(context).apply {
            addView(edit)
            addView(button)
        }
        assertSame(button, FocusTargetResolver.resolveToLeafFocusable(host))
    }

    @Test
    fun recyclerViewAfterDescendantsWithChild_returnedAsIs() {
        val rv = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            descendantFocusability = RecyclerView.FOCUS_AFTER_DESCENDANTS
            // A focusable child standing in for a bound item view.
            addView(Button(context))
        }
        // It legitimately delegates focus to its items; leave it so list scrolling keeps working.
        assertSame(rv, FocusTargetResolver.resolveToLeafFocusable(rv))
    }

    @Test
    fun containerWhoseOnlyControlIsNestedScrollContainer_prefersTheRealLeaf() {
        // Outer container holds an inner ViewPager2 (a scroll container) AND a Button.
        // The resolver must skip the inner scroll container and pick the Button.
        val inner = ViewPager2(context)
        val button = Button(context)
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(inner)
            addView(button)
        }
        assertSame(button, FocusTargetResolver.resolveToLeafFocusable(outer))
    }

    @Test
    fun isFocusTrapContainer_nullFocus_isTrap() {
        assertTrue(FocusTargetResolver.isFocusTrapContainer(null))
    }

    @Test
    fun isFocusTrapContainer_leafControl_isNotTrap() {
        assertFalse(FocusTargetResolver.isFocusTrapContainer(Button(context)))
    }

    @Test
    fun isFocusTrapContainer_recyclerViewAfterDescendantsWithChild_isNotTrap() {
        val rv = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            descendantFocusability = RecyclerView.FOCUS_AFTER_DESCENDANTS
            addView(Button(context))
        }
        assertFalse(FocusTargetResolver.isFocusTrapContainer(rv))
    }

    @Test
    fun isFocusTrapContainer_scrollViewWrappingControls_isTrap() {
        val scroll = ScrollView(context).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(Button(context))
            })
        }
        assertTrue(FocusTargetResolver.isFocusTrapContainer(scroll))
    }

    @Test
    fun isFocusTrapContainer_realLeafPastNestedScrollContainer_isTrap() {
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(ViewPager2(context))
            addView(Button(context))
        }
        assertTrue(FocusTargetResolver.isFocusTrapContainer(outer))
    }
}
