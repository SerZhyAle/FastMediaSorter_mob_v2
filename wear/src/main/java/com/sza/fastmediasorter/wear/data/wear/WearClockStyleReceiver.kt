package com.sza.fastmediasorter.wear.data.wear

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.sza.fastmediasorter.wear.complication.WearClockStyleComplicationService
import com.sza.fastmediasorter.wear.domain.repository.WearClockStyleRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3557: applies one clock-style packet from the phone.
 *
 * Its own class rather than a branch body in the listener service, because the write and the face
 * refresh must outlive that service's callback and the service file is already near its size limit.
 */
@Singleton
class WearClockStyleReceiver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clockStyleRepository: WearClockStyleRepository,
    private val preferencesRepository: WearPreferencesRepository
) {

    /**
     * An unreadable packet is dropped and the stored style kept: losing one bad message must not
     * reset a look the owner can only restore by gesturing on the phone again.
     */
    suspend fun handle(payload: ByteArray) {
        val style = WearClockStyleCodec.decodeEnvelope(payload)
        if (style == null) {
            Timber.w("Dropped an unreadable clock style packet - keeping the stored style")
            return
        }
        try {
            clockStyleRepository.save(style)
            // ADR-1: the settings bundle's seconds field (S3330) keeps carrying the same value, so the
            // two sources the dim clock can be fed from never disagree.
            preferencesRepository.setDimClockSecondsVisible(style.secondsVisible)
        } catch (e: IOException) {
            Timber.w(e, "Failed to store the clock style - the watch keeps the previous one")
            return
        }
        // Without this the face keeps the old style until its next poll, and the provider declares
        // no polling at all - a gesture on the phone has to show on the wrist at once.
        ComplicationDataSourceUpdateRequester.create(
            context,
            ComponentName(context, WearClockStyleComplicationService::class.java)
        ).requestUpdateAll()
    }
}
