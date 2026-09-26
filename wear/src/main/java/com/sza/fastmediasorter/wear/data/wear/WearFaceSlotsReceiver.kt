package com.sza.fastmediasorter.wear.data.wear

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.sza.fastmediasorter.wear.complication.WearFaceSlot1ComplicationService
import com.sza.fastmediasorter.wear.complication.WearFaceSlot2ComplicationService
import com.sza.fastmediasorter.wear.complication.WearFaceSlot3ComplicationService
import com.sza.fastmediasorter.wear.complication.WearFaceSlot4ComplicationService
import com.sza.fastmediasorter.wear.domain.repository.WearFaceSlotsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3558: applies one face-slots packet from the phone - its own class for [WearClockStyleReceiver]'s
 * reason, the write and the face refresh outlive the listener service's callback.
 */
@Singleton
class WearFaceSlotsReceiver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val faceSlotsRepository: WearFaceSlotsRepository
) {

    /** An unreadable packet is dropped and the stored choice kept, as a clock-style packet is. */
    suspend fun handle(payload: ByteArray) {
        val slots = WearFaceSlotsCodec.decodeEnvelope(payload)
        if (slots == null) {
            Timber.w("Dropped an unreadable face slots packet - keeping the stored choice")
            return
        }
        Timber.d("S3558: face slots received from phone")
        try {
            faceSlotsRepository.save(slots)
        } catch (e: IOException) {
            Timber.w(e, "Failed to store the face slots - the watch keeps the previous choice")
            return
        }
        // The face otherwise keeps the old slot content until the providers' next poll, and a choice
        // made on the phone has to show on the wrist at once.
        SLOT_PROVIDERS.forEach { provider ->
            ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, provider))
                .requestUpdateAll()
        }
    }

    private companion object {
        val SLOT_PROVIDERS = listOf(
            WearFaceSlot1ComplicationService::class.java,
            WearFaceSlot2ComplicationService::class.java,
            WearFaceSlot3ComplicationService::class.java,
            WearFaceSlot4ComplicationService::class.java
        )
    }
}
