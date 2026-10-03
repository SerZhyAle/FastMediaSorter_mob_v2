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
import com.sza.fastmediasorter.wear.domain.repository.WearPhoneBatteryRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.ResolveWearBackgroundUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * S3557: serves the paired phone's clock style to this product's watch face, which binds one fixed,
 * hidden slot to this provider and decodes [WearClockStyleFaceEncoder]'s layout.
 *
 * Declared in both editions since S4029 returned the Data Layer listener to the store build; until a
 * style arrives, a face with no provider data draws the defaults by itself.
 * No update period - the clock-style receiver asks for an update each time a new style is stored.
 */
@AndroidEntryPoint
class WearClockStyleComplicationService : SuspendingComplicationDataSourceService() {

    @Inject
    lateinit var clockStyleRepository: WearClockStyleRepository

    // S3764: the phone battery band rides the same multiplexed code as the style digits (ADR-4).
    @Inject
    lateinit var phoneBatteryRepository: WearPhoneBatteryRepository

    @Inject
    lateinit var resolveWearBackground: ResolveWearBackgroundUseCase

    @Inject
    lateinit var preferencesRepository: WearPreferencesRepository

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.RANGED_VALUE) return null
        // S3707: the face repeats the app backdrop, so the answer carries the resolved one beside the style.
        val backdrop = WearFaceBackdrop.of(
            background = resolveWearBackground().first(),
            animationsDisabled = preferencesRepository.isAnimationsDisabled.first()
        )
        // S3764: no report at all, or one older than the repository's staleness constant, composes
        // the stale sentinel - the face answers it with the bare track instead of an old charge.
        val report = phoneBatteryRepository.report.first()
        val nowMs = System.currentTimeMillis()
        val phoneBand = WearClockStyleFaceEncoder.phoneBatteryBand(report, nowMs)
        return rangedValue(clockStyleRepository.style.first(), backdrop, phoneBand)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.RANGED_VALUE) {
            rangedValue(WearClockStyle.DEFAULT, WearFaceBackdrop.ANIMATION, phoneBand = 0)
        } else {
            null
        }

    private fun rangedValue(style: WearClockStyle, backdrop: WearFaceBackdrop, phoneBand: Int): ComplicationData {
        val label = PlainComplicationText.Builder(getString(R.string.wear_complication_clock_style_label)).build()
        val code = WearClockStyleFaceEncoder.code(style, backdrop) +
            WearClockStyleFaceEncoder.PHONE_BAND_WEIGHT * phoneBand
        return RangedValueComplicationData.Builder(
            value = code.toFloat(),
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
