package ru.er_log.dictate.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TranscribeResponse(
    val text: String,
    val words: Int,
    @SerialName("duration_sec") val durationSec: Double,
)

@Serializable
data class HealthResponse(
    val ready: Boolean,
    val backend: String,
    val model: String,
)
