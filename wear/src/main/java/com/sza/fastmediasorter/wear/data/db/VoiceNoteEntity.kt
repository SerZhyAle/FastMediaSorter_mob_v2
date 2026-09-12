package com.sza.fastmediasorter.wear.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sza.fastmediasorter.wear.domain.model.VoiceNote
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteDeliveryState

/**
 * S1862 / S2161: the stored shape of a voice note.
 *
 * [deliveryState] is the enum NAME rather than its ordinal and rather than a `@TypeConverter`: a
 * name survives reordering the enum, and one column of one entity does not justify a converter that
 * the whole database would then have to declare.
 *
 * [publishedAddress] is the content address of the entry published into MediaStore.Audio, or null
 * when the note stays private (pre-S2161 recordings, API 28, or failed publication).
 */
@Entity(tableName = "voice_notes")
data class VoiceNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val fileName: String,
    val absolutePath: String,
    val createdAtMillis: Long,
    val durationMillis: Long,
    val sizeBytes: Long,
    val deliveryState: String,
    val publishedAddress: String? = null
)

fun VoiceNoteEntity.toDomain(): VoiceNote = VoiceNote(
    id = id,
    fileName = fileName,
    absolutePath = absolutePath,
    createdAtMillis = createdAtMillis,
    durationMillis = durationMillis,
    sizeBytes = sizeBytes,
    deliveryState = VoiceNoteDeliveryState.fromNameOrDefault(deliveryState),
    publishedAddress = publishedAddress
)
