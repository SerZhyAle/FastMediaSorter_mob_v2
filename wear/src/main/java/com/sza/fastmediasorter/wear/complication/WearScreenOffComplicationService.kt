package com.sza.fastmediasorter.wear.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.sza.fastmediasorter.wear.MainActivity
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.markWearScreenOffRequest

/**
 * S4018: a glyph-only button whose tap opens the watch app under its dark sheet.
 *
 * The product face binds it to its top-right slot. The data never changes, so it reads nothing and
 * needs no injection; only the tap carries meaning.
 */
class WearScreenOffComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        return dataFor(request.complicationType, tapIntent())
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = dataFor(type, tapIntent = null)

    private fun dataFor(type: ComplicationType, tapIntent: PendingIntent?): ComplicationData? {
        val icon = Icon.createWithResource(this, R.drawable.ic_night_mode)
        val image = MonochromaticImage.Builder(icon).setAmbientImage(icon).build()
        val description = PlainComplicationText.Builder(getString(R.string.wear_screen_off)).build()
        return when (type) {
            ComplicationType.MONOCHROMATIC_IMAGE ->
                MonochromaticImageComplicationData.Builder(image, description)
                    .setTapAction(tapIntent)
                    .build()
            // An empty text is the glyph-only form the product face already draws for a shortcut.
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(PlainComplicationText.Builder("").build(), description)
                    .setMonochromaticImage(image)
                    .setTapAction(tapIntent)
                    .build()
            else -> null
        }
    }

    private fun tapIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_CODE,
        Intent(this, MainActivity::class.java).markWearScreenOffRequest(),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private companion object {
        // Clear of the stand-alone providers' 0..2 and the face slots' 355801..355804.
        const val REQUEST_CODE = 4018_00
    }
}
