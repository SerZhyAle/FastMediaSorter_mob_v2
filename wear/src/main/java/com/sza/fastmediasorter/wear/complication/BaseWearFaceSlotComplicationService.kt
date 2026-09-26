package com.sza.fastmediasorter.wear.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.sza.fastmediasorter.wear.MainActivity
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
import com.sza.fastmediasorter.wear.domain.model.WearFaceSlots
import com.sza.fastmediasorter.wear.domain.model.WearFaceSystemItem
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.writeTo
import com.sza.fastmediasorter.wear.domain.repository.WearFaceSlotsRepository
import com.sza.fastmediasorter.wear.domain.usecase.LoadWearComplicationContentUseCase
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S3558: serves one watch-face slot the choice the phone made for it.
 *
 * One subclass per slot (ADR-2): a request does not say which slot asks, and a face without code can
 * only name a provider by its component. Always SHORT_TEXT (ADR-3) - the face fixes the requested type,
 * and a short text carries both the glyph and the value.
 *
 * Nothing to draw answers [NoDataComplicationData] without a placeholder, never null and never
 * EmptyComplicationData: null means "no update" and would leave the previous choice on the face, and
 * the library refuses EMPTY from a data source with an exception that kills the process.
 */
@AndroidEntryPoint
abstract class BaseWearFaceSlotComplicationService : SuspendingComplicationDataSourceService() {

    @Inject
    lateinit var faceSlotsRepository: WearFaceSlotsRepository

    // Lazy: only a slot holding one of the app's data items needs the content use case's repositories.
    @Inject
    lateinit var loadContent: Lazy<LoadWearComplicationContentUseCase>

    @Inject
    lateinit var systemReader: Lazy<WearFaceSlotSystemReader>

    @Inject
    lateinit var capabilities: WearRestrictedCapabilities

    /** 1-based, as the face's slotId is. */
    protected abstract val slot: Int

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        Timber.d("S3558: watch face requested slot content")
        if (request.complicationType != ComplicationType.SHORT_TEXT) return NoDataComplicationData()
        val option = faceSlotsRepository.slots.first().optionFor(slot)
        return when (val plan = WearFaceSlotContentResolver.planFor(option)) {
            WearFaceSlotPlan.Blank -> NoDataComplicationData()
            is WearFaceSlotPlan.Shortcut -> shortcutData(plan)
            is WearFaceSlotPlan.AppData -> appData(plan)
            is WearFaceSlotPlan.System -> systemData(plan)
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        val plan = WearFaceSlotContentResolver.planFor(WearFaceSlots.DEFAULT.optionFor(slot))
        val icon = when (plan) {
            WearFaceSlotPlan.Blank -> R.drawable.ic_open_in_new
            is WearFaceSlotPlan.Shortcut -> plan.icon
            is WearFaceSlotPlan.AppData -> plan.icon
            is WearFaceSlotPlan.System -> plan.icon
        }
        return shortText(PREVIEW_TEXT, getString(R.string.app_name), icon, tapIntent = null)
    }

    private fun shortcutData(plan: WearFaceSlotPlan.Shortcut): ComplicationData {
        val label = WearFaceSlotContentResolver.labelResFor(plan.destination, capabilities) ?: R.string.app_name
        // The glyph alone is the button: an empty text is accepted by the library (it builds its own
        // ComplicationText.EMPTY the same way) and the face draws nothing beside the icon.
        val tap = appTapIntent(WearLaunchTarget.Destination(plan.destination))
        return shortText(text = "", description = getString(label), icon = plan.icon, tapIntent = tap)
    }

    private suspend fun appData(plan: WearFaceSlotPlan.AppData): ComplicationData {
        val text = WearComplicationTextFormatter(resources).format(loadContent.get()(plan.kind))
            ?: return NoDataComplicationData()
        val tap = text.launchTarget?.let(::appTapIntent)
        return shortText(text.shortText, text.contentDescription, plan.icon, tap)
    }

    private suspend fun systemData(plan: WearFaceSlotPlan.System): ComplicationData {
        val reader = systemReader.get()
        // Off the main thread: the handler lookup is a package-manager IPC.
        val (handler, value) = withContext(Dispatchers.IO) {
            plan.tapAction?.let(reader::handlerIntentFor) to reader.valueText(plan.item)
        }
        return if ((plan.needsHandler && handler == null) || value == null) {
            NoDataComplicationData()
        } else {
            val label = getString(systemLabelFor(plan.item))
            val description = if (value.isEmpty()) {
                label
            } else {
                getString(R.string.wear_complication_face_slot_value_a11y, label, value)
            }
            shortText(value, description, plan.icon, handler?.let(::pendingActivity))
        }
    }

    private fun shortText(
        text: String,
        description: String,
        @DrawableRes icon: Int,
        tapIntent: PendingIntent?
    ): ComplicationData {
        val image = Icon.createWithResource(this, icon)
        val builder = ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder(description).build()
        ).setMonochromaticImage(MonochromaticImage.Builder(image).setAmbientImage(image).build())
        if (tapIntent != null) {
            builder.setTapAction(tapIntent)
        }
        return builder.build()
    }

    private fun appTapIntent(target: WearLaunchTarget): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply { target.writeTo(this) }
        return pendingActivity(intent)
    }

    // Extras do not count toward PendingIntent identity, so every slot needs its own request code or the
    // four slots opening MainActivity would overwrite one another's target; offset past the 0..2 the
    // stand-alone data providers use.
    private fun pendingActivity(intent: Intent): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_CODE_BASE + slot,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    @StringRes
    private fun systemLabelFor(item: WearFaceSystemItem): Int = when (item) {
        WearFaceSystemItem.BATTERY -> R.string.wear_complication_face_slot_battery
        WearFaceSystemItem.DATE -> R.string.wear_complication_face_slot_date
        WearFaceSystemItem.NEXT_ALARM -> R.string.wear_complication_face_slot_next_alarm
        WearFaceSystemItem.ALARMS -> R.string.wear_complication_face_slot_alarms
        WearFaceSystemItem.TIMER -> R.string.wear_complication_face_slot_timer
    }

    private companion object {
        const val PREVIEW_TEXT = "FMS"
        const val REQUEST_CODE_BASE = 3558_00
    }
}
