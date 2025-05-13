package com.nicksunday.boardgameassistant.api

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface BGGApi {
    @Headers("Accept: application/xml")
    @GET("xmlapi2/collection")
    suspend fun getBoardGameLibrary(
        @Query("username") username: String,
        @Query("own") own: Int = 1
    ): Response<ResponseBody>


    @Headers("Accept: application/xml")
    @GET("xmlapi2/thing")
    suspend fun getBoardGameDetails(
        @Query("id") id: String,
        @Query("stats") stats: Int = 1
    ): Response<ResponseBody>

    @Headers("Accept: application/xml")
    @GET("xmlapi2/search")
    suspend fun search(
        @Query("query") query: String
    ): Response<ResponseBody>

    companion object {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("boardgamegeek.com")
            .build()

        fun create(): BGGApi = create(url)

        private fun create(httpUrl: HttpUrl): BGGApi {
            val client = OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().apply {
                    this.level = HttpLoggingInterceptor.Level.BASIC
                })
                .build()

            return Retrofit.Builder()
                .baseUrl(httpUrl)
                .client(client)
                .build()
                .create(BGGApi::class.java)
        }
    }
}
