package ru.er_log.dictate.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted record of a single dictation session.
 *
 * @property id Auto-generated primary key.
 * @property timestampUtcMs UTC epoch milliseconds when the event was recorded.
 * @property words Number of recognised words in this session.
 * @property durationSec Duration of the recorded audio in seconds.
 */
@Entity
public data class RecognitionEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampUtcMs: Long,
    val words: Int,
    val durationSec: Double,
)
