package com.sza.fastmediasorter.wear.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * S1862 / S2161: the watch-side voice-note store, version 2.
 *
 * Migration 1 -> 2 adds [VoiceNoteEntity.publishedAddress] for MediaStore integration (S2161).
 * The schema is exported so future migrations have something to be diffed against.
 */
@Database(entities = [VoiceNoteEntity::class], version = 2, exportSchema = true)
abstract class WearVoiceNoteDatabase : RoomDatabase() {

    abstract fun voiceNoteDao(): VoiceNoteDao

    companion object {
        const val DATABASE_NAME = "wear_voice_notes.db"
    }
}
