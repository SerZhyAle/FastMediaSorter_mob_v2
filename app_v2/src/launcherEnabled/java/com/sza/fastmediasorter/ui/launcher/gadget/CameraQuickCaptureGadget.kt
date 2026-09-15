package com.sza.fastmediasorter.ui.launcher.gadget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.GadgetHomeWidgetBinding
import com.sza.fastmediasorter.widget.CameraQuickCaptureActivity
import com.sza.fastmediasorter.widget.CameraQuickCaptureWidgetProvider
import timber.log.Timber

/**
 * S1930: the camera quick-capture widget as a desktop cell - one tap shoots into the target this cell
 * was configured with.
 *
 * Its own class rather than a [HomeWidgetGadget] registration for the reason that class's KDoc gives:
 * a per-instance widget is not the "one key, one fixed destination" shape. The destination here is
 * whatever this cell's own instance stored, and the tap starts the capture trampoline rather than
 * running a [LauncherCellCommand][com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand] -
 * the command codec is frozen at what a cell's `target` can hold, and "capture into instance -1000042"
 * is not one of those things.
 *
 * The face is deliberately the shared [GadgetHomeWidgetBinding] one: an icon and a label is exactly
 * what the home-screen twin shows, so the difference between the two widgets stays in what they do,
 * not in how they look.
 */
class CameraQuickCaptureGadget : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_CAMERA_QUICK_CAPTURE
    override val defaultSpanW: Int = SPAN
    override val defaultSpanH: Int = SPAN
    override val labelRes: Int = R.string.widget_camera_quick_capture_label
    override val iconRes: Int = R.drawable.ic_widget_camera_quick_capture

    /** The param is an instance token minted at add time, not a resource id - see strategic §5.1. */
    override val requiresResourceParam: Boolean = false

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        CameraQuickCaptureGadgetView(container.context, ConfigurableWidgetCatalog.tokenOf(param))

    private companion object {
        /** `targetCellWidth` / `targetCellHeight` of widget_camera_quick_capture_info.xml. */
        const val SPAN = 1
    }
}

private class CameraQuickCaptureGadgetView(
    context: Context,
    private val token: Int?,
) : LauncherGadgetView(context) {

    init {
        val binding = GadgetHomeWidgetBinding.inflate(LayoutInflater.from(context), this)
        binding.gadgetHomeWidgetIcon.setImageResource(R.drawable.ic_widget_camera_quick_capture)
        val label = context.getString(R.string.widget_camera_quick_capture_label)
        val instance = token
        if (instance == null) {
            showUnavailable(binding, label)
        } else {
            binding.gadgetHomeWidgetLabel.text = label
            // The label is the only thing naming this cell, so the whole cell announces it rather than
            // leaving a talkback user with an unlabelled tap target (Rule 16).
            contentDescription = label
            isFocusable = true
            isClickable = true
            setOnClickListener { onTap(instance) }
        }
    }

    /**
     * A cell whose param carries no usable token has no instance to capture into and none to
     * configure, so it says so on its face - the same answer [RandomPhotoFrameGadget]'s
     * `showUnavailable()` gives - rather than swallowing a tap that looked like it worked.
     */
    private fun showUnavailable(binding: GadgetHomeWidgetBinding, label: String) {
        val unavailable = context.getString(R.string.launcher_home_cell_unavailable)
        binding.gadgetHomeWidgetIcon.alpha = UNAVAILABLE_ALPHA
        binding.gadgetHomeWidgetLabel.text = "$label\n$unavailable"
        contentDescription = "$label, $unavailable"
        isClickable = false
    }

    /**
     * Configured state is read at the moment of the tap, never cached at construction: the cell's own
     * configuration screen can run between two taps, and a cached "not configured yet" would keep
     * sending the user back to it after they had answered.
     */
    private fun onTap(instance: Int) {
        val intent = if (CameraQuickCaptureWidgetProvider.isConfigured(context, instance)) {
            captureIntent(instance)
        } else {
            ConfigurableWidgetCatalog.configIntent(
                context = context,
                gadgetKey = LauncherGadgetRegistry.KEY_CAMERA_QUICK_CAPTURE,
                token = instance,
            ) ?: return
        }
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Timber.w(it, "Launcher quick capture gadget: camera refused to open") }
    }

    private fun captureIntent(instance: Int): Intent =
        Intent(context, CameraQuickCaptureActivity::class.java).apply {
            action = CameraQuickCaptureActivity.ACTION_CAPTURE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, instance)
        }

    private companion object {
        /** Dimmed enough to read as inert against a wallpaper, still legible as the camera icon. */
        const val UNAVAILABLE_ALPHA = 0.4f
    }
}
