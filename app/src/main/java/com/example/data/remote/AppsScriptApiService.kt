package com.example.data.remote

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

class SafeDoubleAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): Double {
        return when (reader.peek()) {
            JsonReader.Token.NUMBER -> reader.nextDouble()
            JsonReader.Token.STRING -> reader.nextString().trim().toDoubleOrNull() ?: 0.0
            JsonReader.Token.NULL -> {
                reader.nextNull<Unit>()
                0.0
            }
            else -> {
                reader.skipValue()
                0.0
            }
        }
    }

    @ToJson
    fun toJson(value: Double): Double = value
}

class SafeIntAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): Int {
        return when (reader.peek()) {
            JsonReader.Token.NUMBER -> reader.nextDouble().toInt()
            JsonReader.Token.STRING -> reader.nextString().trim().toDoubleOrNull()?.toInt() ?: 0
            JsonReader.Token.NULL -> {
                reader.nextNull<Unit>()
                0
            }
            else -> {
                reader.skipValue()
                0
            }
        }
    }

    @ToJson
    fun toJson(value: Int): Int = value
}

interface AppsScriptApiService {
    @POST
    suspend fun postAction(
        @Url url: String,
        @Body body: RequestBody
    ): Response<ResponseBody>
}

object NetworkClient {

    val moshi: Moshi = Moshi.Builder()
        .add(SafeDoubleAdapter())
        .add(SafeIntAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
        )
        .build()

    val apiService: AppsScriptApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://script.google.com/")
            .client(okHttpClient)
            .build()
            .create(AppsScriptApiService::class.java)
    }
}
