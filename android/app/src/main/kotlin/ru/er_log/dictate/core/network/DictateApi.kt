package ru.er_log.dictate.core.network

import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface DictateApi {

    @POST("/transcribe")
    suspend fun transcribe(@Body wav: RequestBody): TranscribeResponse

    @GET("/health")
    suspend fun health(): HealthResponse
}
