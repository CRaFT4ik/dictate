package ru.er_log.dictate.core.transcribe

public interface DictateRepository {
    public suspend fun transcribe(wav: ByteArray): TranscribeResult
}
