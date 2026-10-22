package com.sza.fastmediasorter.ui.broadcast.helpers

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.broadcast.BroadcastState
import com.sza.fastmediasorter.databinding.ActivityBroadcastControlBinding
import timber.log.Timber

/**
 * S4418: the broadcast control screen's share of the player's rotation rule (S0667) - landscape means
 * fullscreen for a visual medium. While a live camera picture is on air and the phone is on its side,
 * the picture container takes the whole screen (system bars hidden, display cutout still respected by the
 * activity's inset padding) and the control panel steps aside; a tap on the picture brings the panel back
 * beside it, the layout the screen had before, and a second tap hides it again.
 *
 * The encoded stream keeps the orientation it started with (S4403), so the picture stays as the listeners
 * get it - only the rectangle it is shown in grows.
 *
 * The only state is the "panel asked for" flag. It lives here because [BroadcastControlManager], the
 * singleton that owns this object, outlives the activity a rotation recreates; every view reference is
 * taken per call and never stored.
 */
class BroadcastLandscapeVideoManager {

    private var controlsShown = false

    /** Wires the tap once per activity; [currentState] is read at tap time so the render sees the live state. */
    fun bind(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding,
        currentState: () -> BroadcastState,
    ) {
        binding.previewContainer.setOnClickListener {
            controlsShown = !controlsShown
            render(activity, binding, currentState())
        }
        // setOnClickListener makes the view clickable; render() decides when a tap is meaningful.
        applyTapAvailability(activity, binding, available = false)
    }

    fun render(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding,
        state: BroadcastState,
    ) {
        if (state !is BroadcastState.Live) controlsShown = false
        val landscape = binding.root.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val pictureOnScreen = state is BroadcastState.Live && binding.previewContainer.isVisible
        applyTapAvailability(activity, binding, available = landscape && pictureOnScreen)
        // Portrait has one scrolling column and no second pane, so there is nothing to give up or take back.
        if (!landscape) return
        val fullscreen = BroadcastEntryUi.showsFullscreenPreview(landscape, pictureOnScreen, controlsShown)
        Timber.d("S4418: landscape video fullscreen=$fullscreen picture=$pictureOnScreen controls=$controlsShown")
        // Declared by the landscape layout alone: data binding wants the root element's id to agree across
        // variants, and the portrait root is the scrolling column itself, with no panel to hide.
        binding.controlsScroll?.isVisible = !fullscreen
        applyPictureMargin(binding, fullscreen)
        applySystemBars(activity, binding.root, hidden = fullscreen)
    }

    /**
     * A container with nothing to toggle must not be a button: the idle lens preview shares it, and a
     * TalkBack user would be told about an action that does nothing. `noHideDescendants` in the layout
     * also hides the container itself from accessibility, so it is lifted only while the tap works.
     */
    private fun applyTapAvailability(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding,
        available: Boolean,
    ) {
        val container = binding.previewContainer
        container.isClickable = available
        container.isFocusable = available
        container.contentDescription =
            if (available) activity.getString(R.string.broadcast_control_preview_toggle_cd) else null
        container.importantForAccessibility = if (available) {
            View.IMPORTANT_FOR_ACCESSIBILITY_YES
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }

    /** The gap between the two panes has no neighbour to separate from once the panel is gone. */
    private fun applyPictureMargin(binding: ActivityBroadcastControlBinding, fullscreen: Boolean) {
        val container = binding.previewContainer
        val params = container.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val target = if (fullscreen) 0 else container.resources.getDimensionPixelSize(R.dimen.margin_normal)
        if (params.marginEnd == target) return
        params.marginEnd = target
        container.layoutParams = params
    }

    private fun applySystemBars(activity: AppCompatActivity, root: View, hidden: Boolean) {
        val controller = WindowCompat.getInsetsController(activity.window, root)
        if (hidden) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
