package com.sza.fastmediasorter.wear.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * S1862: the watch-side voice-note store, version 1.
 *
 * No migration exists and none is written: no installed watch database predates this build, so
 * there is nothing to migrate from (ADR-5). The schema is exported so a version 2 has something to
 * be diffed against.
 */
@Database(entities = [VoiceNoteEntity::class], version = 1, exportSchema = true)
abstract class WearVoiceNoteDatabase : RoomDatabase() {

    abstract fun voiceNoteDao(): VoiceNoteDao

    companion object {
        const val DATABASE_NAME = "wear_voice_notes.db"
    }
}
