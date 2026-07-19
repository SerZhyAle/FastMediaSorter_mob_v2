package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.util.Rational
import android.util.Size
import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
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

/** Builds the CameraX use-case group while keeping output geometry in one place. */
internal class CameraUseCaseFactory(
    private val videoMode: Boolean,
    private val selectedAspectRatio: Int?,
    private val selectedResolution: Size?,
    private val targetRotation: Int,
) {

    fun create(previewView: PreviewView): CameraUseCases {
        val preview = Preview.Builder()
            .setResolutionSelector(buildResolutionSelector())
            .build()
            .also {
                it.surfaceProvider = previewView.surfaceProvider
                it.targetRotation = targetRotation
            }
        val imageCapture = if (videoMode) {
            null
        } else {
            ImageCapture.Builder()
                .setResolutionSelector(buildResolutionSelector())
                .build()
                .also { it.targetRotation = targetRotation }
        }
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

    private fun buildResolutionSelector(): ResolutionSelector {
        val aspect = effectiveAspectRatioInt()
        val builder = ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy(aspect, AspectRatioStrategy.FALLBACK_RULE_AUTO),
            )
        selectedResolution?.takeIf { resolutionMatchesAspect(it, aspect) }?.let {
            builder.setResolutionStrategy(
                ResolutionStrategy(it, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
            )
        }
        return builder.build()
    }

    private fun effectiveAspectRatioInt(): Int =
        if (videoMode) selectedAspectRatio ?: AspectRatio.RATIO_4_3 else AspectRatio.RATIO_4_3

    private fun effectiveAspectRational(): Rational =
        if (effectiveAspectRatioInt() == AspectRatio.RATIO_16_9) RATIONAL_16_9 else RATIONAL_4_3

    private fun resolutionMatchesAspect(size: Size, aspect: Int): Boolean {
        if (size.height == 0) return false
        val ratio = size.width.toFloat() / size.height.toFloat()
        val target = if (aspect == AspectRatio.RATIO_16_9) SIXTEEN_NINE else FOUR_THREE
        return kotlin.math.abs(ratio - target) < ASPECT_MATCH_EPSILON
    }

    companion object {
        fun selectorFor(info: CameraInfo): CameraSelector =
            CameraSelector.Builder()
                .addCameraFilter { infos -> infos.filter { it == info } }
                .build()

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
