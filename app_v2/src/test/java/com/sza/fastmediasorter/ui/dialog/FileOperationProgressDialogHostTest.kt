package com.sza.fastmediasorter.ui.dialog

import android.app.Activity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.FragmentActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** S3902: the delayed progress show must see a dead host through the dialog's wrapped context. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileOperationProgressDialogHostTest {

    @Test
    fun `a live activity behind a theme wrapper is not gone`() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()

        assertFalse(ContextThemeWrapper(activity, 0).isDialogHostGone())
    }

    @Test
    fun `a finishing activity is gone`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.finish()

        assertTrue(ContextThemeWrapper(activity, 0).isDialogHostGone())
    }

    @Test
    fun `a destroyed lifecycle owner is gone`() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        controller.pause().stop().destroy()

        assertTrue(ContextThemeWrapper(controller.get(), 0).isDialogHostGone())
    }

    @Test
    fun `an application context has no host to lose`() {
        assertFalse(RuntimeEnvironment.getApplication().isDialogHostGone())
    }
}
