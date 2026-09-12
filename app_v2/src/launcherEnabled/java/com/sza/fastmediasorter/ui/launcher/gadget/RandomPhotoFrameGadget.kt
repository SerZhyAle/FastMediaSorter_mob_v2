package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.GadgetLauncherPhotoFrameBinding
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.widget.RandomPhotoFrameSnapshotStore
import com.sza.fastmediasorter.widget.RandomPhotoFrameWidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * S1930: the random photo frame as a desktop cell - one photo out of a resource, re-drawn on every
 * visit, opening at that photo on tap.
 *
 * Its own class rather than another [HomeWidgetGadget] registration, because it is not that shape:
 * that class's KDoc reserves separate classes for exactly the widgets that keep per-instance state,
 * and this one has a snapshot to read, an image to load and three destinations depending on what the
 * snapshot says. Hiding that behind a strategy inside the shared class would make the shared class the
 * place where every widget's differences accumulate.
 *
 * The cell's instance lives in its `param` as a launcher token, so the snapshot it reads is the one its
 * own configuration screen wrote - see [ConfigurableWidgetCatalog].
 */
class RandomPhotoFrameGadget : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_RANDOM_PHOTO_FRAME
    override val defaultSpanW: Int = SPAN
    override val defaultSpanH: Int = SPAN
    override val labelRes: Int = R.string.widget_random_photo_frame_label
    override val iconRes: Int = R.drawable.ic_widget_camera_photos

    /** The param is an instance token minted at add time, not a resource id - see strategic §5.1. */
    override val requiresResourceParam: Boolean = false

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        RandomPhotoFrameGadgetView(container.context, ConfigurableWidgetCatalog.tokenOf(param))

    private companion object {
        /** `targetCellWidth` / `targetCellHeight` of widget_random_photo_frame_info.xml. */
        const val SPAN = 2
    }
}

private class RandomPhotoFrameGadgetView(
    context: Context,
    private val token: Int?,
) : LauncherGadgetView(context) {

    private val binding = GadgetLauncherPhotoFrameBinding.inflate(LayoutInflater.from(context), this)

    init {
        // The cell is one tap target announcing one name, like every other gadget face (Rule 16).
        contentDescription = context.getString(R.string.widget_random_photo_frame_label)
        isFocusable = true
        isClickable = true
    }

    override suspend fun CoroutineScope.onActive() {
        val instance = token
        if (instance == null) {
            showUnavailable()
            return
        }
        // refresh() picks the next photo behind a runBlocking over Room and the thumbnail cache. This
        // scope is the view's main-thread lifecycle scope, so the read is moved off it explicitly.
        val snapshot = withContext(Dispatchers.IO) {
            val stored = RandomPhotoFrameSnapshotStore.read(context, instance)
            if (stored.isConfigured) RandomPhotoFrameWidgetRefresher.refresh(context, instance) else stored
        }
        render(instance, snapshot)
    }

    override fun onDetachedFromWindow() {
        // Glide ownership (audit protocol): the desktop rebuilds every cell view on each emission, so a
        // target left bound keeps a decoded bitmap alive for a view nobody will show again.
        runCatching { Glide.with(binding.gadgetPhotoFrameImage).clear(binding.gadgetPhotoFrameImage) }
        super.onDetachedFromWindow()
    }

    private fun render(instance: Int, snapshot: RandomPhotoFrameSnapshotStore.Snapshot) {
        when {
            !snapshot.isConfigured -> showPlaceholder(
                title = context.getString(R.string.widget_random_photo_frame_empty_title),
                subtitle = context.getString(R.string.widget_random_photo_frame_empty_subtitle),
                onTap = { openConfiguration(instance) },
            )

            snapshot.hasRenderablePhoto && snapshot.selectedThumbnailUri.isNotBlank() ->
                showPhoto(snapshot)

            else -> showPlaceholder(
                title = snapshot.resourceName.ifBlank {
                    context.getString(R.string.widget_random_photo_frame_empty_title)
                },
                subtitle = snapshot.fallbackMessage.ifBlank {
                    context.getString(R.string.widget_random_photo_frame_cache_empty)
                },
                onTap = {
                    open(
                        BrowseActivity.createIntent(
                            context = context,
                            resourceId = snapshot.resourceId,
                            skipAvailabilityCheck = true,
                        )
                    )
                },
            )
        }
    }

    private fun showPhoto(snapshot: RandomPhotoFrameSnapshotStore.Snapshot) {
        binding.gadgetPhotoFrameOverlay.isVisible = false
        contentDescription = snapshot.resourceName.ifBlank {
            context.getString(R.string.widget_random_photo_frame_label)
        }
        Glide.with(binding.gadgetPhotoFrameImage)
            .load(Uri.parse(snapshot.selectedThumbnailUri))
            .centerCrop()
            // S1317: a still frame is what a photo frame shows, and it keeps no animated decoder alive
            // behind a desktop the user has left.
            .dontAnimate()
            .placeholder(R.drawable.ic_image)
            .error(R.drawable.ic_image)
            .into(binding.gadgetPhotoFrameImage)
        setOnClickListener {
            open(
                PlayerActivity.createPanelIntent(
                    context = context,
                    resourceId = snapshot.resourceId,
                    skipAvailabilityCheck = true,
                    initialFilePath = snapshot.selectedFilePath,
                )
            )
        }
    }

    private fun showPlaceholder(title: String, subtitle: String, onTap: () -> Unit) {
        runCatching { Glide.with(binding.gadgetPhotoFrameImage).clear(binding.gadgetPhotoFrameImage) }
        binding.gadgetPhotoFrameImage.setImageResource(R.drawable.ic_image)
        binding.gadgetPhotoFrameOverlay.isVisible = true
        binding.gadgetPhotoFrameTitle.text = title
        binding.gadgetPhotoFrameSubtitle.text = subtitle
        setOnClickListener { onTap() }
    }

    /**
     * A cell whose param carries no usable token cannot be repaired from here - it has no instance to
     * configure - so it says so instead of opening a screen that would write a snapshot nothing reads.
     */
    private fun showUnavailable() {
        binding.gadgetPhotoFrameImage.setImageResource(R.drawable.ic_image)
        binding.gadgetPhotoFrameOverlay.isVisible = true
        binding.gadgetPhotoFrameTitle.setText(R.string.widget_random_photo_frame_label)
        binding.gadgetPhotoFrameSubtitle.setText(R.string.launcher_home_cell_unavailable)
        setOnClickListener(null)
        isClickable = false
    }

    private fun openConfiguration(instance: Int) {
        val intent = ConfigurableWidgetCatalog.configIntent(
            context = context,
            gadgetKey = LauncherGadgetRegistry.KEY_RANDOM_PHOTO_FRAME,
            token = instance,
        ) ?: return
        open(intent)
    }

    private fun open(intent: Intent) {
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Timber.w(it, "Launcher photo frame gadget: target refused to open") }
    }
}
