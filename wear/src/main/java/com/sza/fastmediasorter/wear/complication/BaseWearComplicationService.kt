package com.sza.fastmediasorter.wear.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.sza.fastmediasorter.wear.MainActivity
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearComplicationContent
import com.sza.fastmediasorter.wear.domain.model.WearComplicationKind
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.writeTo
import com.sza.fastmediasorter.wear.domain.usecase.LoadWearComplicationContentUseCase
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * S2047: abstract base service for Wear OS complication data sources.
 */
@AndroidEntryPoint
abstract class BaseWearComplicationService : SuspendingComplicationDataSourceService() {

    @Inject
    lateinit var loadContent: LoadWearComplicationContentUseCase

    protected abstract val kind: WearComplicationKind

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Timber.d("S2047: onComplicationRequest kind=$kind type=${request.complicationType}")
        val content = loadContent(kind)
        return mapContentToData(request.complicationType, content)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val sample = WearComplicationContent.Value(
            shortText = "FMS",
            longText = "FastMediaSorter",
            contentDescription = "FastMediaSorter",
            launchTarget = null
        )
        return mapContentToData(type, sample)
    }

    private fun mapContentToData(type: ComplicationType, content: WearComplicationContent): ComplicationData? {
        val value = content as? WearComplicationContent.Value ?: return null
        val tapIntent = createTapPendingIntent(value.launchTarget)

        val data: ComplicationData? = when (type) {
            ComplicationType.SHORT_TEXT -> buildShortText(value, tapIntent)
            ComplicationType.LONG_TEXT -> buildLongText(value, tapIntent)
            else -> null
        }
        return data
    }

    private fun buildShortText(
        value: WearComplicationContent.Value,
        tapIntent: PendingIntent?
    ): ShortTextComplicationData {
        val builder = ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(value.shortText).build(),
            contentDescription = PlainComplicationText.Builder(value.contentDescription).build()
        ).setMonochromaticImage(createMonochromaticImage())

        if (tapIntent != null) {
            builder.setTapAction(tapIntent)
        }
        return builder.build()
    }

    private fun buildLongText(
        value: WearComplicationContent.Value,
        tapIntent: PendingIntent?
    ): LongTextComplicationData {
        val text = value.longText ?: value.shortText
        val builder = LongTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder(value.contentDescription).build()
        ).setMonochromaticImage(createMonochromaticImage())

        if (tapIntent != null) {
            builder.setTapAction(tapIntent)
        }
        return builder.build()
    }

    private fun createMonochromaticImage(): MonochromaticImage {
        val iconRes = when (kind) {
            WearComplicationKind.LAST_RESOURCE -> R.drawable.ic_complication_last_resource
            WearComplicationKind.FAVOURITES_COUNT -> R.drawable.ic_complication_favourites
            WearComplicationKind.NOW_PLAYING -> R.drawable.ic_complication_now_playing
        }
        val icon = Icon.createWithResource(this, iconRes)
        return MonochromaticImage.Builder(icon)
            .setAmbientImage(icon)
            .build()
    }

    private fun createTapPendingIntent(target: WearLaunchTarget?): PendingIntent? {
        if (target == null) return null
        val intent = Intent(this, MainActivity::class.java).apply {
            target.writeTo(this)
        }
        return PendingIntent.getActivity(
            this,
            kind.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
