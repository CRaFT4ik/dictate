package ru.er_log.dictate.feature.stats.data

import ru.er_log.dictate.core.db.RecognitionEvent
import ru.er_log.dictate.core.db.RecognitionEventDao

public class RecognitionLogger(private val dao: RecognitionEventDao) {

    public suspend fun log(words: Int, durationSec: Double) {
        dao.insert(
            RecognitionEvent(
                timestampUtcMs = System.currentTimeMillis(),
                words = words,
                durationSec = durationSec,
            )
        )
    }
}
