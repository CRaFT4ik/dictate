package ru.er_log.dictate.core.transcribe

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import ru.er_log.dictate.core.network.DictateApi
import java.io.IOException

public class DictateRepositoryImpl(private val api: DictateApi) : DictateRepository {

    override suspend fun transcribe(wav: ByteArray): TranscribeResult = runCatching {
        val body = wav.toRequestBody("application/octet-stream".toMediaType())
        val response = api.transcribe(body)
        TranscribeResult.Success(
            text = response.text,
            words = response.words,
            durationSec = response.durationSec,
        )
    }.getOrElse { throwable ->
        when (throwable) {
            is IOException -> TranscribeResult.NetworkError
            is HttpException -> TranscribeResult.ServerError(throwable.code())
            else -> throw throwable
        }
    }
}
