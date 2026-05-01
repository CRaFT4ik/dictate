package ru.er_log.dictate.core.transcribe

public sealed class TranscribeResult {
    public data class Success(val text: String, val words: Int, val durationSec: Double) : TranscribeResult()
    public data object NetworkError : TranscribeResult()
    public data class ServerError(val code: Int) : TranscribeResult()
}
