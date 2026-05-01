package ru.er_log.dictate.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for [RecognitionEvent] persistence and aggregation queries.
 */
@Dao
public interface RecognitionEventDao {

    /**
     * Inserts a [RecognitionEvent] and returns the new row ID.
     */
    @Insert
    public suspend fun insert(event: RecognitionEvent): Long

    /**
     * Returns a [Flow] emitting the sum of [RecognitionEvent.words] for events whose
     * [RecognitionEvent.timestampUtcMs] falls within [[fromUtcMs], [toUtcMs]).
     *
     * Emits `null` when there are no matching events; callers should coalesce to `0L`.
     */
    @Query("SELECT SUM(words) FROM RecognitionEvent WHERE timestampUtcMs >= :fromUtcMs AND timestampUtcMs < :toUtcMs")
    public fun wordsBetween(fromUtcMs: Long, toUtcMs: Long): Flow<Long?>
}
