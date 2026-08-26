package com.sza.fastmediasorter.wear.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sza.fastmediasorter.wear.domain.model.VoiceNote
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteDeliveryState

/**
 * S1862: the stored shape of a voice note.
 *
 * [deliveryState] is the enum NAME rather than its ordinal and rather than a `@TypeConverter`: a
 * name survives reordering the enum, and one column of one entity does not justify a converter that
 * the whole database would then have to declare.
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
    val deliveryState: String
)

fun VoiceNoteEntity.toDomain(): VoiceNote = VoiceNote(
    id = id,
    fileName = fileName,
    absolutePath = absolutePath,
    createdAtMillis = createdAtMillis,
    durationMillis = durationMillis,
    sizeBytes = sizeBytes,
    deliveryState = VoiceNoteDeliveryState.fromNameOrDefault(deliveryState)
)
