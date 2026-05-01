package ru.er_log.dictate.core.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for the Dictate app.
 *
 * Contains a single entity [RecognitionEvent] that persists each dictation session.
 * Access it via [recognitionEventDao].
 */
@Database(entities = [RecognitionEvent::class], version = 1, exportSchema = false)
public abstract class DictateDatabase : RoomDatabase() {

    /** Returns the DAO for reading and writing [RecognitionEvent] rows. */
    public abstract fun recognitionEventDao(): RecognitionEventDao
}
