package com.sza.fastmediasorter.ui.common

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.ui.common.input.FocusDirection
import com.sza.fastmediasorter.ui.common.input.InputAction
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FocusManagerTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `page down jumps by page size after deterministic initial focus`() {
        val harness = FocusHarness(itemCount = 25)

        assertTrue(harness.manager.applyAction(InputAction.PageJump(+1)))
        assertEquals(10, harness.manager.getCurrentPosition())
        assertEquals(listOf(0, 10), harness.focusedPositions)
    }

    @Test
    fun `home and end move to first and last items`() {
        val harness = FocusHarness(itemCount = 12)

        harness.manager.setPosition(5)
        assertTrue(harness.manager.applyAction(InputAction.MoveFocus(FocusDirection.FIRST)))
        assertEquals(0, harness.manager.getCurrentPosition())

        assertTrue(harness.manager.applyAction(InputAction.MoveFocus(FocusDirection.LAST)))
        assertEquals(11, harness.manager.getCurrentPosition())
    }

    @Test
    fun `range extension keeps original anchor`() {
        val harness = FocusHarness(itemCount = 8)

        harness.manager.setPosition(2)
        assertTrue(harness.manager.applyAction(InputAction.RangeExtendDown))
        assertEquals(3, harness.manager.getCurrentPosition())
        assertEquals(2 to 3, harness.lastRange)
    }

    private inner class FocusHarness(itemCount: Int) {
        val recyclerView = mockk<RecyclerView>(relaxed = true)
        val focusedPositions = mutableListOf<Int>()
        var lastRange: Pair<Int, Int>? = null

        private val holders = (0 until itemCount).associateWith {
            object : RecyclerView.ViewHolder(View(context)) {}
        }

        val manager = FocusManager(
            recyclerView = recyclerView,
            callbacks = object : FocusManager.FocusCallbacks {
                override fun onItemFocused(position: Int) {
                    focusedPositions += position
                }

                override fun onItemSelected(position: Int) = Unit

                override fun getItemCount(): Int = itemCount

                override fun onRangeExtended(anchor: Int, position: Int) {
                    lastRange = anchor to position
                }
            },
        )

        init {
            every { recyclerView.layoutManager } returns LinearLayoutManager(context)
            every { recyclerView.post(any()) } answers {
                firstArg<Runnable>().run()
                true
            }
            every { recyclerView.findViewHolderForAdapterPosition(any()) } answers {
                holders[firstArg<Int>()]
            }
        }
    }
}