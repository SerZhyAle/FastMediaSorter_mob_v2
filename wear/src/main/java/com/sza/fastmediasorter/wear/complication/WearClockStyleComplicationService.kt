package com.sza.fastmediasorter.wear.complication

import androidx.wear.watchface.complications.data.ColorRamp
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearClockStyle
import com.sza.fastmediasorter.wear.domain.model.WearFaceBackdrop
import com.sza.fastmediasorter.wear.domain.repository.WearClockStyleRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.ResolveWearBackgroundUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * S3557: serves the paired phone's clock style to this product's watch face, which binds one fixed,
 * hidden slot to this provider and decodes [WearClockStyleFaceEncoder]'s layout.
 *
 * Declared only in the noLegal manifest (ADR-3): the Play watch build has no Data Layer listener, so a
 * style could never arrive there, and a face with no provider already draws the defaults by itself.
 * No update period - the clock-style receiver asks for an update each time a new style is stored.
 */
@AndroidEntryPoint
class WearClockStyleComplicationService : SuspendingComplicationDataSourceService() {

    @Inject
    lateinit var clockStyleRepository: WearClockStyleRepository

    @Inject
    lateinit var resolveWearBackground: ResolveWearBackgroundUseCase

    @Inject
    lateinit var preferencesRepository: WearPreferencesRepository

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Timber.d("S3557: watch face requested clock style")
        if (request.complicationType != ComplicationType.RANGED_VALUE) return null
        // S3707: the face repeats the app backdrop, so the answer carries the resolved one beside the style.
        val backdrop = WearFaceBackdrop.of(
            background = resolveWearBackground().first(),
            animationsDisabled = preferencesRepository.isAnimationsDisabled.first()
        )
        Timber.d("S3707: watch face served its backdrop")
        return rangedValue(clockStyleRepository.style.first(), backdrop)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.RANGED_VALUE) {
            rangedValue(WearClockStyle.DEFAULT, WearFaceBackdrop.ANIMATION)
        } else {
            null
        }

    private fun rangedValue(style: WearClockStyle, backdrop: WearFaceBackdrop): ComplicationData {
        val label = PlainComplicationText.Builder(getString(R.string.wear_complication_clock_style_label)).build()
        return RangedValueComplicationData.Builder(
            value = WearClockStyleFaceEncoder.code(style, backdrop).toFloat(),
            min = WearClockStyleFaceEncoder.CODE_MIN.toFloat(),
            max = WearClockStyleFaceEncoder.CODE_MAX.toFloat(),
            contentDescription = label
        )
            // The library refuses a RANGED_VALUE with no text, title or image and the refusal kills the
            // process (measured on the Wear emulator); the face never draws this text.
            .setText(label)
            .setColorRamp(ColorRamp(WearClockStyleFaceEncoder.colors(style), false))
            .build()
    }
}
