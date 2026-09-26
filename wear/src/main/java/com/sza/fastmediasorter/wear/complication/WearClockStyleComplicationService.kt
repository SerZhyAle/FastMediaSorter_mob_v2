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
import com.sza.fastmediasorter.wear.domain.repository.WearClockStyleRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
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

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.RANGED_VALUE) return null
        return rangedValue(clockStyleRepository.style.first())
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.RANGED_VALUE) rangedValue(WearClockStyle.DEFAULT) else null

    private fun rangedValue(style: WearClockStyle): ComplicationData =
        RangedValueComplicationData.Builder(
            value = WearClockStyleFaceEncoder.code(style).toFloat(),
            min = WearClockStyleFaceEncoder.CODE_MIN.toFloat(),
            max = WearClockStyleFaceEncoder.CODE_MAX.toFloat(),
            contentDescription = PlainComplicationText.Builder(
                getString(R.string.wear_complication_clock_style_label)
            ).build()
        )
            .setColorRamp(ColorRamp(WearClockStyleFaceEncoder.colors(style), false))
            .build()
}
