package com.example.data.remote

import com.example.data.model.ApiResponse
import com.example.data.model.AppVersionInfo
import com.example.data.model.LoginRequest
import com.example.data.model.SignupRequest
import com.example.data.model.SubmitKycRequest
import com.example.data.model.SubmitTransactionRequest
import com.example.data.model.TransactionRecord
import com.example.data.model.UpdateKycStatusRequest
import com.example.data.model.UpdateTransactionStatusRequest
import com.example.data.model.UpdateUserBalanceRequest
import com.example.data.model.UserPlan
import com.example.data.model.UserProfile
import com.example.data.model.VerificationRecord
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

/**
 * Handles empty strings or numeric strings returned by Google Sheets cells gracefully.
 */
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

    @GET
    suspend fun checkVersion(
        @Url url: String,
        @Query("action") action: String = "checkVersion"
    ): ApiResponse<AppVersionInfo>

    @GET
    suspend fun getUser(
        @Url url: String,
        @Query("action") action: String = "getUser",
        @Query("email") email: String
    ): ApiResponse<UserProfile>

    @GET
    suspend fun getTransactions(
        @Url url: String,
        @Query("action") action: String = "getTransactions",
        @Query("email") email: String? = null,
        @Query("adminEmail") adminEmail: String? = null,
        @Query("status") status: String? = null
    ): ApiResponse<List<TransactionRecord>>

    @GET
    suspend fun getUserPlans(
        @Url url: String,
        @Query("action") action: String = "getUserPlans",
        @Query("email") email: String
    ): ApiResponse<List<UserPlan>>

    @GET
    suspend fun getPendingVerifications(
        @Url url: String,
        @Query("action") action: String = "getPendingVerifications",
        @Query("adminEmail") adminEmail: String
    ): ApiResponse<List<VerificationRecord>>

    @GET
    suspend fun getAllUsers(
        @Url url: String,
        @Query("action") action: String = "getAllUsers",
        @Query("adminEmail") adminEmail: String
    ): ApiResponse<List<UserProfile>>

    @POST
    suspend fun login(
        @Url url: String,
        @Body request: LoginRequest
    ): ApiResponse<UserProfile>

    @POST
    suspend fun signup(
        @Url url: String,
        @Body request: SignupRequest
    ): ApiResponse<UserProfile>

    @POST
    suspend fun submitTransaction(
        @Url url: String,
        @Body request: SubmitTransactionRequest
    ): ApiResponse<TransactionRecord>

    @POST
    suspend fun submitKyc(
        @Url url: String,
        @Body request: SubmitKycRequest
    ): ApiResponse<VerificationRecord>

    @POST
    suspend fun updateTransactionStatus(
        @Url url: String,
        @Body request: UpdateTransactionStatusRequest
    ): ApiResponse<Map<String, Any>>

    @POST
    suspend fun updateKycStatus(
        @Url url: String,
        @Body request: UpdateKycStatusRequest
    ): ApiResponse<Any>

    @POST
    suspend fun updateUserBalance(
        @Url url: String,
        @Body request: UpdateUserBalanceRequest
    ): ApiResponse<Any>
}

object NetworkClient {

    private val moshi: Moshi = Moshi.Builder()
        .add(SafeDoubleAdapter())
        .add(SafeIntAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()

    val apiService: AppsScriptApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://script.google.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AppsScriptApiService::class.java)
    }
}
