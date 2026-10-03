package com.sza.fastmediasorter.wear.complication

import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearBackground
import com.sza.fastmediasorter.wear.domain.usecase.ResolveWearBackgroundUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * S3708: serves the watch app's delivered backdrop frame to this product's watch face, which binds
 * one hidden full-screen PHOTO_IMAGE slot to this provider and hides it in ambient mode.
 *
 * Any backdrop other than a delivered frame answers [NoDataComplicationData], so the slot draws
 * nothing and the face shows what the style slot's backdrop digit says. Declared only in the
 * noLegal manifest for the same reason as [WearClockStyleComplicationService]: the Play build has no
 * Data Layer listener that could ever deliver a frame. No update period - every backdrop change
 * asks for an update through the same path that refreshes the style slot.
 */
@AndroidEntryPoint
class WearFacePhotoComplicationService : SuspendingComplicationDataSourceService() {

    @Inject
    lateinit var resolveWearBackground: ResolveWearBackgroundUseCase

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val bytes = if (request.complicationType == ComplicationType.PHOTO_IMAGE) deliveredPhotoBytes() else null
        return bytes?.let { photo(Icon.createWithData(it, 0, it.size)) } ?: NoDataComplicationData()
    }

    private suspend fun deliveredPhotoBytes(): ByteArray? {
        val frame = resolveWearBackground().first() as? WearBackground.Image ?: return null
        return withContext(Dispatchers.IO) { WearFacePhotoEncoder.encode(frame.file) }
    }

    /** The editor never offers this hidden slot, so a preview only has to be valid, not pretty. */
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.PHOTO_IMAGE) {
            photo(Icon.createWithResource(this, R.mipmap.ic_launcher))
        } else {
            null
        }

    private fun photo(icon: Icon): ComplicationData = PhotoImageComplicationData.Builder(
        photoImage = icon,
        contentDescription = PlainComplicationText.Builder(
            getString(R.string.wear_complication_face_photo_label)
        ).build()
    ).build()
}
