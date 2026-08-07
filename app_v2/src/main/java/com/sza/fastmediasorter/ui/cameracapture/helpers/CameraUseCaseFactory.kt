package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.util.Rational
import android.util.Size
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExtendableBuilder
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import com.sza.fastmediasorter.ui.cameracapture.model.CameraLensEntry

/** Builds the CameraX use-case group while keeping output geometry in one place. */
internal class CameraUseCaseFactory(
    private val videoMode: Boolean,
    private val selectedAspectRatio: Int?,
    private val selectedResolution: Size?,
    private val targetRotation: Int,
    /**
     * S1189: names one physical lens inside the selected logical camera. Applied to preview and
     * image capture together or not at all - a preview on the ultra-wide and a capture on the main
     * lens would break WYSIWYG. Null in video mode, because the video pipeline is built through
     * `VideoCapture.withOutput` and offers no builder to carry the id.
     */
    private val physicalCameraId: String? = null,
    /**
     * S1189: the chosen photo size only exists in the sensor's high-resolution set, so image capture
     * must be allowed to trade capture rate for resolution. Never applied to the preview - a preview
     * stream at sensor resolution is neither needed nor cheap.
     */
    private val preferHighResolution: Boolean = false,
) {

    fun create(previewView: PreviewView): CameraUseCases {
        val preview = Preview.Builder()
            .setResolutionSelector(buildResolutionSelector(allowHighResolution = false))
            .applyPhysicalCameraId()
            .build()
            .also {
                it.surfaceProvider = previewView.surfaceProvider
                it.targetRotation = targetRotation
            }
        val imageCapture = if (videoMode) null else createPhotoCapture()
        val videoCapture = if (videoMode) {
            VideoCapture.withOutput(Recorder.Builder().build())
                .also { it.targetRotation = targetRotation }
        } else {
            null
        }
        val captureUseCase: UseCase = imageCapture ?: requireNotNull(videoCapture)
        val viewPort = ViewPort.Builder(effectiveAspectRational(), targetRotation)
            .setScaleType(ViewPort.FILL_CENTER)
            .build()
        val group = UseCaseGroup.Builder()
            .setViewPort(viewPort)
            .addUseCase(preview)
            .addUseCase(captureUseCase)
            .build()
        return CameraUseCases(preview, imageCapture, videoCapture, group)
    }

    /**
     * S1478: the image capture on its own, for a caller that has no preview surface to give - the
     * headless shot. It is the same use case [create] binds, built from the same resolution selector
     * and the same physical-lens id, so the two capture routes cannot drift apart the way they did
     * while the headless path owned a second builder. CameraX defaults to
     * [ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY]; stating it here makes the shared choice visible
     * instead of leaving each route to inherit it silently.
     */
    fun createPhotoCapture(): ImageCapture =
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(buildResolutionSelector(preferHighResolution))
            .applyPhysicalCameraId()
            .build()
            .also { it.targetRotation = targetRotation }

    /**
     * S1189: no-op unless a physical lens was chosen and the pipeline can carry it, so a device
     * without sub-lens support builds exactly the use cases it built before.
     */
    private fun <T, B : ExtendableBuilder<T>> B.applyPhysicalCameraId(): B {
        val id = physicalCameraId
        if (id == null || videoMode) return this
        Camera2Interop.Extender(this).setPhysicalCameraId(id)
        return this
    }

    private fun buildResolutionSelector(allowHighResolution: Boolean): ResolutionSelector {
        val aspect = effectiveAspectRatioInt()
        val builder = ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy(aspect, AspectRatioStrategy.FALLBACK_RULE_AUTO),
            )
        if (allowHighResolution) {
            builder.setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
        }
        selectedResolution?.takeIf { resolutionMatchesAspect(it, aspect) }?.let {
            builder.setResolutionStrategy(
                ResolutionStrategy(it, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
            )
        }
        return builder.build()
    }

    private fun effectiveAspectRatioInt(): Int =
        if (videoMode) selectedAspectRatio ?: AspectRatio.RATIO_4_3 else PHOTO_ASPECT_RATIO

    private fun effectiveAspectRational(): Rational =
        if (effectiveAspectRatioInt() == AspectRatio.RATIO_16_9) RATIONAL_16_9 else RATIONAL_4_3

    companion object {

        /**
         * S1066: photo capture always requests the full 4:3 sensor stream - a 16:9 selection is
         * realised by the result-frame overlay plus a post-capture crop, not by a narrower stream.
         *
         * S1457: public so the settings dialog can offer only resolutions this pipeline will honour.
         * It used to hardcode its own list, and every mismatched pick was dropped by
         * [resolutionMatchesAspect] below with nothing shown to the user.
         */
        val PHOTO_ASPECT_RATIO = AspectRatio.RATIO_4_3

        /** True when [size] is close enough to [aspect] that the resolution strategy will apply it. */
        fun resolutionMatchesAspect(size: Size, aspect: Int): Boolean {
            if (size.height == 0) return false
            val ratio = size.width.toFloat() / size.height.toFloat()
            val target = if (aspect == AspectRatio.RATIO_16_9) SIXTEEN_NINE else FOUR_THREE
            return kotlin.math.abs(ratio - target) < ASPECT_MATCH_EPSILON
        }

        fun selectorFor(info: CameraInfo): CameraSelector =
            CameraSelector.Builder()
                .addCameraFilter { infos -> infos.filter { it == info } }
                .build()

        /**
         * S1189: a lens entry always binds its LOGICAL camera - a physical sub-lens is not
         * selectable on its own, it is named on the use-case builders instead.
         */
        fun selectorFor(entry: CameraLensEntry): CameraSelector = selectorFor(entry.cameraInfo)

        private val RATIONAL_4_3 = Rational(4, 3)
        private val RATIONAL_16_9 = Rational(16, 9)
        private const val FOUR_THREE = 4f / 3f
        private const val SIXTEEN_NINE = 16f / 9f
        private const val ASPECT_MATCH_EPSILON = 0.02f
    }
}

internal data class CameraUseCases(
    val preview: Preview,
    val imageCapture: ImageCapture?,
    val videoCapture: VideoCapture<Recorder>?,
    val group: UseCaseGroup,
)
