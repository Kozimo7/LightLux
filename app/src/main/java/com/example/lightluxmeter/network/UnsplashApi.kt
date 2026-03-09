package com.example.lightluxmeter.network

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

// Data Models
data class UnsplashResponse(val results: List<UnsplashPhoto>)

data class UnsplashPhoto(
        val id: String,
        val description: String?,
        val alt_description: String?,
        val urls: UnsplashUrls
)

data class UnsplashUrls(
        val raw: String,
        val full: String,
        val regular: String,
        val small: String,
        val thumb: String
)

interface UnsplashApi {
    @Headers("Accept-Version: v1")
    @GET("search/photos")
    suspend fun searchPhotos(
            @Query("query") query: String,
            @Query("client_id") clientId: String = "NScnJ4yctfdBDkmMNIAjrjNml_KL6Hnum08DIeQk8ik",
            @Query("per_page") perPage: Int = 30
    ): UnsplashResponse

    companion object {
        private const val BASE_URL = "https://api.unsplash.com/"

        fun create(): UnsplashApi {
            val certificatePinner =
                    CertificatePinner.Builder()
                            .add(
                                    "api.unsplash.com",
                                    "sha256/AYPYJLVU3pG/1G/91agkdpRH0s69R0pgl0eude3Na18="
                            ) // Actual hash from log
                            .build()

            val client = OkHttpClient.Builder().certificatePinner(certificatePinner).build()

            val retrofit =
                    Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build()

            return retrofit.create(UnsplashApi::class.java)
        }
    }
}
