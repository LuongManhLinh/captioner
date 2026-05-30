package io.captioner.service

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path


interface GeneratorApi {
    @Multipart
    @POST("/api/transcribe/caption")
    suspend fun generateCaptions(@Part body: MultipartBody.Part): Response<CaptionResponse>

    @Multipart
    @POST("/api/transcribe/karaoke")
    suspend fun generateKaraoke(@Part body: MultipartBody.Part): Response<KaraokeResponse>

    @Multipart
    @POST("upload-srt")
    suspend fun uploadSrt(
        @Part file: MultipartBody.Part
    ): Response<Any>

    @GET("/api/separate-vocal/{id}")
    suspend fun separateVocal(@Path("id") id: String): Response<ResponseBody>
}
