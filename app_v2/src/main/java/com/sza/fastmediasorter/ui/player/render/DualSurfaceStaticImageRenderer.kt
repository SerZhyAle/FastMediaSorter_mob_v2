package com.sza.fastmediasorter.ui.player.render

import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import com.github.chrisbanes.photoview.PhotoView
import com.sza.fastmediasorter.core.util.MemoryTier
import com.sza.fastmediasorter.data.cloud.glide.CloudThumbnailData
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.network.glide.NetworkFileData
import com.sza.fastmediasorter.domain.model.StereoMode
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.coroutines.resume

/**
 * Dual-surface image renderer for instant image swapping.
 *
 * Uses two PhotoViews (Surface A and Surface B) to enable seamless transitions:
 * - One surface displays the current image (active)
 * - The other surface preloads the next image (inactive)
 * - On navigation, surfaces swap visibility instantly
 *
 * This eliminates the flash/flicker during image transitions.
 */
class DualSurfaceStaticImageRenderer(
    private val surfaceA: PhotoView,
    private val surfaceB: PhotoView?,
    private val memoryTier: MemoryTier = MemoryTier.detect(surfaceA.context)
) : StaticImageRenderer {

    private enum class ActiveSurface { A, B }

    private val _state = MutableStateFlow<RenderState>(RenderState.Idle)
    override val state: StateFlow<RenderState> = _state

    private var activeSurface: ActiveSurface = ActiveSurface.A
    private var currentMode: RendererMode = RendererMode.MANUAL
    private var currentTarget: RenderTarget? = null
    // Stereo crop mode for 3D images; MONO = no crop (default).
    private var currentStereoMode: StereoMode = StereoMode.MONO

    private val appContext = surfaceA.context.applicationContext
    private val maxLoadDimension = if (memoryTier == MemoryTier.LOW) 2048 else 4096
    private val preloadMaxDimension = if (memoryTier == MemoryTier.LOW) 1280 else 1920

    init {
        Timber.d("DualSurfaceStaticImageRenderer: Initialized with memoryTier=$memoryTier, hasSurfaceB=${surfaceB != null}")
    }

    override suspend fun render(target: RenderTarget) {
        Timber.d("DualSurfaceStaticImageRenderer: render() called for ${target.mediaFile.name}, requestId=${target.requestId}")

        val previousTarget = currentTarget
        currentTarget = target

        // Transition to Loading state
        updateState(RenderState.Loading(target))

        // If no surface B available, render directly to surface A
        if (surfaceB == null) {
            renderToSingleSurface(target)
            return
        }

        // Get inactive surface for loading
        val inactiveSurface = getInactiveSurface()
        if (inactiveSurface == null) {
            Timber.w("DualSurfaceStaticImageRenderer: No inactive surface available, falling back to single surface")
            renderToSingleSurface(target)
            return
        }

        // Load into inactive surface
        val loadResult = loadImageIntoSurface(target, inactiveSurface)

        if (loadResult) {
            // Success: swap visibility
            if (previousTarget != null) {
                updateState(
                    RenderState.Transitioning(
                        from = previousTarget,
                        to = target,
                        policy = getTransitionPolicy()
                    )
                )
            }

            swapSurfaces()
            updateState(RenderState.Ready(target))
            Timber.d("DualSurfaceStaticImageRenderer: Render complete, surfaces swapped, active=$activeSurface")
        } else {
            // Error already set by loadImageIntoSurface
            Timber.w("DualSurfaceStaticImageRenderer: Render failed for ${target.mediaFile.name}")
        }
    }

    override suspend fun prefetch(targets: List<RenderTarget>) {
        if (targets.isEmpty()) {
            Timber.d("DualSurfaceStaticImageRenderer: prefetch() called with empty list")
            return
        }

        Timber.d("DualSurfaceStaticImageRenderer: prefetch() called for ${targets.size} targets")

        targets.forEach { target ->
            try {
                prefetchTarget(target)
                Timber.d("DualSurfaceStaticImageRenderer: Prefetch completed for ${target.mediaFile.name}")
            } catch (e: Exception) {
                Timber.w(e, "DualSurfaceStaticImageRenderer: Prefetch failed for ${target.mediaFile.name}")
            }
        }
    }

    override fun setMode(mode: RendererMode) {
        Timber.d("DualSurfaceStaticImageRenderer: setMode() $currentMode -> $mode")
        currentMode = mode
    }

    override fun setStereoMode(mode: StereoMode) {
        Timber.d("DualSurfaceStaticImageRenderer: setStereoMode() $currentStereoMode -> $mode")
        currentStereoMode = mode
    }

    override fun onPause() {
        Timber.d("DualSurfaceStaticImageRenderer: onPause() - canceling pending requests")
        // Cancel any pending Glide requests
        try {
            Glide.with(appContext).clear(surfaceA)
            surfaceB?.let { Glide.with(appContext).clear(it) }
        } catch (e: Exception) {
            Timber.w(e, "DualSurfaceStaticImageRenderer: Error clearing Glide requests on pause")
        }
    }

    override fun onResume() {
        Timber.d("DualSurfaceStaticImageRenderer: onResume() - no-op")
        // No action needed on resume
    }

    override fun release() {
        Timber.d("DualSurfaceStaticImageRenderer: release() - clearing all resources")

        try {
            // Cancel all Glide requests
            Glide.with(appContext).clear(surfaceA)
            surfaceB?.let { Glide.with(appContext).clear(it) }

            // Clear displayed images
            surfaceA.setImageDrawable(null)
            surfaceB?.setImageDrawable(null)

            // Hide both surfaces
            surfaceA.visibility = View.GONE
            surfaceB?.visibility = View.GONE

            // Reset state
            currentTarget = null
            activeSurface = ActiveSurface.A
            updateState(RenderState.Idle)
        } catch (e: Exception) {
            Timber.w(e, "DualSurfaceStaticImageRenderer: Error during release")
        }

        Timber.d("DualSurfaceStaticImageRenderer: release() complete, state=Idle")
    }

    // region Private Methods

    private fun getActiveSurfaceView(): PhotoView {
        return when (activeSurface) {
            ActiveSurface.A -> surfaceA
            ActiveSurface.B -> surfaceB ?: surfaceA
        }
    }

    private fun getInactiveSurface(): PhotoView? {
        return when (activeSurface) {
            ActiveSurface.A -> surfaceB
            ActiveSurface.B -> surfaceA
        }
    }

    private fun swapSurfaces() {
        val previousActive = getActiveSurfaceView()
        val previousInactive = getInactiveSurface()

        // Toggle active surface
        activeSurface = when (activeSurface) {
            ActiveSurface.A -> ActiveSurface.B
            ActiveSurface.B -> ActiveSurface.A
        }

        // Swap visibility
        previousActive.visibility = View.GONE
        previousInactive?.visibility = View.VISIBLE

        Timber.d("DualSurfaceStaticImageRenderer: Surfaces swapped, now active=$activeSurface")
    }

    private suspend fun renderToSingleSurface(target: RenderTarget) {
        val loadResult = loadImageIntoSurface(target, surfaceA)

        if (loadResult) {
            surfaceA.visibility = View.VISIBLE
            updateState(RenderState.Ready(target))
            Timber.d("DualSurfaceStaticImageRenderer: Single surface render complete")
        }
    }

    private suspend fun loadImageIntoSurface(target: RenderTarget, surface: PhotoView): Boolean {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                loadWithGlide(target, surface, continuation)
            }
        }
    }

    private fun loadWithGlide(
        target: RenderTarget,
        surface: PhotoView,
        continuation: CancellableContinuation<Boolean>
    ) {
        val mediaFile = target.mediaFile
        val cacheKey = "${mediaFile.path}_${mediaFile.size}"

        // Include stereo mode in cache key so switching SBS↔MONO triggers a fresh decode.
        val stereoCacheKey = "${cacheKey}_stereo_${currentStereoMode.name}"

        val glideRequest = Glide.with(appContext)
            .load(resolveGlideModel(target))
            .signature(ObjectKey(stereoCacheKey))
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .priority(resolvePriority(target.priority))
            .override(maxLoadDimension, maxLoadDimension)
            .fitCenter()

        // Apply stereo crop for 3D images (SBS → left half, OU → top half).
        val isStereo = currentStereoMode == StereoMode.SBS_FULL ||
            currentStereoMode == StereoMode.SBS_HALF ||
            currentStereoMode == StereoMode.OU
        if (isStereo) {
            glideRequest.transform(StereoImageCropTransformation(currentStereoMode))
        }

        if (memoryTier == MemoryTier.LOW) {
            glideRequest.format(DecodeFormat.PREFER_RGB_565)
        }

        glideRequest.listener(object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Drawable>,
                isFirstResource: Boolean
            ): Boolean {
                Timber.e(e, "DualSurfaceStaticImageRenderer: Glide load failed for ${mediaFile.name}")
                
                val error = e ?: Exception("Unknown Glide error")
                updateState(RenderState.Error(this@DualSurfaceStaticImageRenderer.currentTarget!!, error))
                
                if (continuation.isActive) {
                    continuation.resume(false)
                }
                return true
            }

            override fun onResourceReady(
                resource: Drawable,
                model: Any,
                target: Target<Drawable>?,
                dataSource: DataSource,
                isFirstResource: Boolean
            ): Boolean {
                Timber.d("DualSurfaceStaticImageRenderer: Glide load success for ${mediaFile.name}, source=$dataSource")
                
                if (continuation.isActive) {
                    continuation.resume(true)
                }
                return false // Let Glide set the drawable
            }
        }).into(surface)

        continuation.invokeOnCancellation {
            Timber.d("DualSurfaceStaticImageRenderer: Load cancelled for ${mediaFile.name}")
            Glide.with(appContext).clear(surface)
        }
    }

    private fun resolveGlideModel(target: RenderTarget): Any {
        val mediaFile = target.mediaFile
        val path = target.path

        return when {
            // Cloud files
            path.startsWith("cloud://", ignoreCase = true) -> {
                val provider = resolveCloudProvider(path)
                val fileId = when (provider) {
                    CloudProvider.DROPBOX -> {
                        val afterScheme = path.substringAfter("cloud://dropbox")
                        val cleanPath = afterScheme.trimStart('/')
                        "/$cleanPath"
                    }
                    else -> path.substringAfterLast('/')
                }
                CloudThumbnailData(
                    thumbnailUrl = mediaFile.thumbnailUrl ?: "",
                    fileId = fileId,
                    loadFullImage = true,
                    cloudProvider = provider
                )
            }

            // Network files (SMB/FTP/SFTP) - detect by path prefix
            path.startsWith("smb://", ignoreCase = true) ||
            path.startsWith("ftp://", ignoreCase = true) ||
            path.startsWith("sftp://", ignoreCase = true) -> {
                NetworkFileData(
                    path = path,
                    loadFullImage = true,
                    highPriority = true,
                    size = mediaFile.size,
                    createdDate = mediaFile.createdDate
                )
            }

            // Local files
            else -> File(path)
        }
    }

    private fun resolveCloudProvider(path: String): CloudProvider {
        val authority = path.removePrefix("cloud://").substringBefore("/").lowercase()
        return when (authority) {
            "googledrive", "google_drive" -> CloudProvider.GOOGLE_DRIVE
            "onedrive" -> CloudProvider.ONEDRIVE
            "dropbox" -> CloudProvider.DROPBOX
            else -> CloudProvider.GOOGLE_DRIVE
        }
    }

    private fun resolvePriority(priority: RenderPriority): Priority {
        return when (priority) {
            RenderPriority.NEXT -> Priority.IMMEDIATE
            RenderPriority.PREVIOUS -> Priority.HIGH
            RenderPriority.LOOKAHEAD -> Priority.LOW
        }
    }

    private suspend fun prefetchTarget(target: RenderTarget) {
        val mediaFile = target.mediaFile
        val cacheKey = "${mediaFile.path}_${mediaFile.size}"

        withContext(Dispatchers.IO) {
            val request = Glide.with(appContext)
                .downloadOnly()
                .load(resolveGlideModel(target))
                .signature(ObjectKey(cacheKey))
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .override(preloadMaxDimension, preloadMaxDimension)

            if (memoryTier == MemoryTier.LOW) {
                request.set(com.bumptech.glide.load.Option.memory("decodeFormat"), DecodeFormat.PREFER_RGB_565)
            }

            request.submit().get()
        }
    }

    private fun getTransitionPolicy(): TransitionPolicy {
        return when (currentMode) {
            RendererMode.SLIDESHOW -> TransitionPolicy(
                type = TransitionType.INSTANT_SWAP,
                durationMs = 0L
            )
            RendererMode.MANUAL -> TransitionPolicy.Default
        }
    }

    private fun updateState(newState: RenderState) {
        val oldState = _state.value
        _state.value = newState
        Timber.d("DualSurfaceStaticImageRenderer: State transition: ${oldState::class.simpleName} -> ${newState::class.simpleName}")
    }

    // endregion

    companion object {
        /**
         * Factory method to create DualSurfaceStaticImageRenderer from PhotoView references.
         *
         * @param surfaceA Primary PhotoView (required)
         * @param surfaceB Secondary PhotoView (optional, may be null in landscape layouts)
         * @param memoryTier Memory tier for optimization
         */
        fun create(
            surfaceA: PhotoView,
            surfaceB: PhotoView? = null,
            memoryTier: MemoryTier = MemoryTier.detect(surfaceA.context)
        ): DualSurfaceStaticImageRenderer {
            return DualSurfaceStaticImageRenderer(surfaceA, surfaceB, memoryTier)
        }
    }
}
