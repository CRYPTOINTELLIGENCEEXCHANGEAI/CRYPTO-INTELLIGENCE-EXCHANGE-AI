package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.data.local.SessionManager
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
import com.example.data.remote.AppsScriptApiService
import com.example.data.remote.NetworkClient
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.lang.reflect.Type
import kotlin.math.max

class MyFundRepository(
    private val context: Context,
    private val api: AppsScriptApiService,
    private val sessionManager: SessionManager
) {
    companion object {
        private const val TAG = "MyFundAPI"

        fun parseUserProfileJson(
            rawBody: String,
            statusCode: Int = 200,
            fallbackEmail: String = ""
        ): Result<UserProfile> {
            val trimmed = rawBody.trim()
            if (trimmed.isEmpty()) {
                return Result.failure(IllegalStateException("HTTP $statusCode returned an empty response."))
            }
            if (trimmed.startsWith("<")) {
                val hint = if (trimmed.contains("accounts.google.com", ignoreCase = true) ||
                    trimmed.contains("Sign in", ignoreCase = true)
                ) {
                    "Apps Script returned a Google Sign-In page (HTTP $statusCode). Ensure deployment access is set to 'Anyone'."
                } else {
                    "Apps Script returned HTML instead of JSON (HTTP $statusCode): ${trimmed.take(200)}"
                }
                return Result.failure(IllegalStateException(hint))
            }

            val mapType = Types.newParameterizedType(
                Map::class.java,
                String::class.java,
                Any::class.java
            )
            val root: Map<String, Any?> = try {
                NetworkClient.moshi.adapter<Map<String, Any?>>(mapType).fromJson(trimmed)
                    ?: return Result.failure(
                        IllegalStateException("Invalid JSON (HTTP $statusCode): ${trimmed.take(240)}")
                    )
            } catch (e: Exception) {
                return Result.failure(
                    IllegalStateException("Invalid JSON (HTTP $statusCode): ${e.message} | Raw: ${trimmed.take(240)}")
                )
            }

            val success = optSafeBoolean(root, "success")
            if (!success) {
                val serverErr = optSafeString(root, "error", "message")
                return Result.failure(
                    IllegalStateException(
                        serverErr.ifBlank { "Request failed (HTTP $statusCode): ${trimmed.take(240)}" }
                    )
                )
            }

            // Support flat top-level JSON as well as optional "data" or "user" wrappers
            val userObj = optNestedMap(root, "data")
                ?: optNestedMap(root, "user")
                ?: root

            val resolvedEmail = optSafeString(userObj, "email", "Email")
                .ifBlank { optSafeString(root, "email", "Email") }
                .ifBlank { fallbackEmail.trim() }

            val profile = UserProfile(
                email = resolvedEmail,
                name = optSafeString(userObj, "name", "Name"),
                invested = optSafeDouble(userObj, "inv", "Invested", "invested"),
                totalWithdrawal = optSafeDouble(userObj, "wit", "Total Withdrawal", "totalWithdrawal"),
                interestRate = optSafeDouble(userObj, "roi", "Interest (%)", "interestRate"),
                withdrawableLimit = optSafeDouble(userObj, "lim", "Withdrawable Limit", "withdrawableLimit"),
                phone = optSafeString(userObj, "phone", "Phone"),
                userId = optSafeString(userObj, "uid", "User ID", "userId"),
                photoUrl = optSafeString(userObj, "photo", "PhotoURL", "photoUrl"),
                totalAmount = optSafeDouble(userObj, "balance", "Total Amount", "totalAmount"),
                status = optSafeString(userObj, "kycStatus", "Status", "status").ifBlank { "Not Verified" },
                notification = optSafeString(userObj, "notification", "Notification"),
                referredBy = optSafeString(userObj, "referredBy", "ReferredBy"),
                termsConsentDate = optSafeString(userObj, "termsConsentDate", "Terms_Consent_Date"),
                isAdmin = optSafeBoolean(userObj, "isAdmin") || optSafeBoolean(root, "isAdmin"),
                rowNumber = optSafeDouble(userObj, "_rowNumber").toInt()
            )
            return Result.success(profile)
        }

        private fun optNestedMap(obj: Map<String, Any?>, key: String): Map<String, Any?>? {
            val raw = obj[key] as? Map<*, *> ?: return null
            return raw.entries.associate { (k, v) -> k.toString() to v }
        }

        private fun optSafeBoolean(obj: Map<String, Any?>, key: String): Boolean {
            return when (val raw = obj[key]) {
                is Boolean -> raw
                is String -> raw.trim().equals("true", ignoreCase = true)
                is Number -> raw.toInt() != 0
                else -> false
            }
        }

        private fun optSafeString(obj: Map<String, Any?>, vararg keys: String): String {
            for (key in keys) {
                val raw = obj[key] ?: continue
                val value = raw.toString().trim()
                if (value.isNotEmpty() && !value.equals("null", ignoreCase = true)) {
                    return value
                }
            }
            return ""
        }

        private fun optSafeDouble(obj: Map<String, Any?>, vararg keys: String): Double {
            for (key in keys) {
                val raw = obj[key] ?: continue
                when (raw) {
                    is Number -> return raw.toDouble()
                    is String -> {
                        val parsed = raw.trim().toDoubleOrNull()
                        if (parsed != null) return parsed
                    }
                }
            }
            return 0.0
        }
    }

    val loggedInEmailFlow: Flow<String?> = sessionManager.loggedInEmailFlow
    val appsScriptUrlFlow: Flow<String> = sessionManager.appsScriptUrlFlow

    suspend fun saveAppsScriptUrl(url: String) {
        sessionManager.saveAppsScriptUrl(url)
    }

    suspend fun logout() {
        sessionManager.clearSession()
    }

    private suspend fun requireEndpointUrl(): String {
        val url = sessionManager.getAppsScriptUrl()
        if (url.isBlank() || !url.startsWith("http")) {
            throw IllegalStateException("Google Apps Script Web App URL is not configured.")
        }
        return url
    }

    private suspend fun executeUserProfileApi(
        actionName: String,
        fallbackEmail: String = "",
        call: suspend (String) -> Response<ResponseBody>
    ): Result<UserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                val url = requireEndpointUrl()
                Log.d(TAG, "[$actionName] Sending request to: $url")
                val rawResponse = call(url)
                val statusCode = rawResponse.code()
                val finalUrl = rawResponse.raw().request.url.toString()
                val bodyString = if (rawResponse.isSuccessful) {
                    rawResponse.body()?.string().orEmpty()
                } else {
                    rawResponse.errorBody()?.string().orEmpty()
                }
                Log.d(TAG, "[$actionName] HTTP $statusCode | Final URL: $finalUrl | Body: $bodyString")

                if (!rawResponse.isSuccessful) {
                    val snippet = bodyString.take(240).ifBlank { rawResponse.message() }
                    return@withContext Result.failure(
                        IllegalStateException("HTTP $statusCode from Apps Script: $snippet")
                    )
                }

                val parsedResult = parseUserProfileJson(bodyString, statusCode, fallbackEmail)
                parsedResult.onFailure { err ->
                    Log.e(TAG, "[$actionName] Failed: ${err.message}")
                }
                parsedResult
            } catch (e: Exception) {
                Log.e(TAG, "[$actionName] Exception: ${e::class.java.simpleName}: ${e.message}", e)
                Result.failure(
                    IllegalStateException("${e::class.java.simpleName}: ${e.message ?: "Network error"}")
                )
            }
        }
    }

    private suspend inline fun <reified T> executeApi(
        actionName: String,
        crossinline call: suspend (String) -> Response<ResponseBody>
    ): Result<T> {
        val type = Types.newParameterizedType(ApiResponse::class.java, T::class.java)
        return executeTypedApi(actionName, type) { url -> call(url) }
    }

    private suspend inline fun <reified E> executeListApi(
        actionName: String,
        crossinline call: suspend (String) -> Response<ResponseBody>
    ): Result<List<E>> {
        val listType = Types.newParameterizedType(List::class.java, E::class.java)
        val responseType = Types.newParameterizedType(ApiResponse::class.java, listType)
        return executeTypedApi(actionName, responseType) { url -> call(url) }
    }

    private suspend fun <T> executeTypedApi(
        actionName: String,
        responseType: Type,
        call: suspend (String) -> Response<ResponseBody>
    ): Result<T> {
        return withContext(Dispatchers.IO) {
            try {
                val url = requireEndpointUrl()
                Log.d(TAG, "[$actionName] Sending request to: $url")
                val rawResponse = call(url)
                val statusCode = rawResponse.code()
                val finalUrl = rawResponse.raw().request.url.toString()
                val bodyString = if (rawResponse.isSuccessful) {
                    rawResponse.body()?.string().orEmpty()
                } else {
                    rawResponse.errorBody()?.string().orEmpty()
                }

                Log.d(TAG, "[$actionName] HTTP $statusCode | Final URL: $finalUrl | Body: $bodyString")

                if (!rawResponse.isSuccessful) {
                    val snippet = bodyString.take(240).ifBlank { rawResponse.message() }
                    return@withContext Result.failure(
                        IllegalStateException("HTTP $statusCode from Apps Script: $snippet")
                    )
                }

                val trimmed = bodyString.trim()
                if (trimmed.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalStateException("HTTP $statusCode returned an empty response body.")
                    )
                }

                if (trimmed.startsWith("<")) {
                    val hint = if (trimmed.contains("accounts.google.com", ignoreCase = true) ||
                        trimmed.contains("Sign in", ignoreCase = true)
                    ) {
                        "Apps Script returned a Google Sign-In HTML page (HTTP $statusCode). Ensure deployment access is set to 'Anyone'."
                    } else {
                        "Apps Script returned HTML instead of JSON (HTTP $statusCode): ${trimmed.take(200)}"
                    }
                    Log.e(TAG, "[$actionName] $hint")
                    return@withContext Result.failure(IllegalStateException(hint))
                }

                val adapter = NetworkClient.moshi.adapter<ApiResponse<T>>(responseType)
                val parsed = try {
                    adapter.fromJson(trimmed)
                } catch (parseErr: Exception) {
                    Log.e(TAG, "[$actionName] JSON parse error: ${parseErr.message}", parseErr)
                    return@withContext Result.failure(
                        IllegalStateException("Invalid JSON (HTTP $statusCode): ${parseErr.message} | Raw: ${trimmed.take(200)}")
                    )
                }

                if (parsed == null) {
                    return@withContext Result.failure(
                        IllegalStateException("Unable to parse response (HTTP $statusCode): ${trimmed.take(200)}")
                    )
                }

                if (parsed.success && parsed.data != null) {
                    Result.success(parsed.data)
                } else {
                    val serverMsg = parsed.message.ifBlank { parsed.error }
                    val detailedMsg = serverMsg.ifBlank {
                        "HTTP $statusCode unexpected response (success=${parsed.success}): ${trimmed.take(220)}"
                    }
                    Log.e(TAG, "[$actionName] API rejected: $detailedMsg")
                    Result.failure(IllegalStateException(detailedMsg))
                }
            } catch (e: Exception) {
                Log.e(TAG, "[$actionName] Exception: ${e::class.java.simpleName}: ${e.message}", e)
                Result.failure(
                    IllegalStateException("${e::class.java.simpleName}: ${e.message ?: "Network error"}")
                )
            }
        }
    }

    private suspend fun executeVoidApi(
        actionName: String,
        call: suspend (String) -> Response<ResponseBody>
    ): Result<String> {
        val type = Types.newParameterizedType(ApiResponse::class.java, Any::class.java)
        return withContext(Dispatchers.IO) {
            try {
                val url = requireEndpointUrl()
                Log.d(TAG, "[$actionName] Sending request to: $url")
                val rawResponse = call(url)
                val statusCode = rawResponse.code()
                val bodyString = if (rawResponse.isSuccessful) {
                    rawResponse.body()?.string().orEmpty()
                } else {
                    rawResponse.errorBody()?.string().orEmpty()
                }
                Log.d(TAG, "[$actionName] HTTP $statusCode | Body: $bodyString")

                if (!rawResponse.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("HTTP $statusCode from Apps Script: ${bodyString.take(220)}")
                    )
                }
                val trimmed = bodyString.trim()
                if (trimmed.startsWith("<")) {
                    return@withContext Result.failure(
                        IllegalStateException("Apps Script returned HTML instead of JSON (HTTP $statusCode): ${trimmed.take(200)}")
                    )
                }
                val adapter = NetworkClient.moshi.adapter<ApiResponse<Any>>(type)
                val parsed = adapter.fromJson(trimmed)
                if (parsed != null && parsed.success) {
                    Result.success(parsed.message.ifBlank { "Success" })
                } else {
                    val serverMsg = parsed?.message?.ifBlank { parsed.error }.orEmpty()
                    val msg = serverMsg.ifBlank {
                        "HTTP $statusCode unexpected response: ${trimmed.take(220)}"
                    }
                    Result.failure(IllegalStateException(msg))
                }
            } catch (e: Exception) {
                Log.e(TAG, "[$actionName] Exception: ${e::class.java.simpleName}: ${e.message}", e)
                Result.failure(
                    IllegalStateException("${e::class.java.simpleName}: ${e.message ?: "Network error"}")
                )
            }
        }
    }

    suspend fun checkAppVersion(): Result<AppVersionInfo> =
        executeApi("checkVersion") { url -> api.checkVersion(url) }

    suspend fun login(email: String, password: String): Result<UserProfile> {
        Log.d(TAG, "Attempting login for identifier=${email.trim()}")
        val result = executeUserProfileApi("login", fallbackEmail = email.trim()) { url ->
            api.login(url, LoginRequest(email = email.trim(), password = password))
        }
        result.onSuccess { user ->
            Log.d(TAG, "Login succeeded for ${user.email}, uid=${user.userId}, isAdmin=${user.computedIsAdmin}")
            if (user.email.isNotBlank()) {
                sessionManager.saveUserEmail(user.email)
            }
        }.onFailure { err ->
            Log.e(TAG, "Login failed for ${email.trim()}: ${err.message}", err)
        }
        return result
    }

    suspend fun signup(
        name: String,
        email: String,
        phone: String,
        password: String,
        referredBy: String
    ): Result<UserProfile> {
        val result = executeUserProfileApi("signup", fallbackEmail = email.trim()) { url ->
            api.signup(
                url,
                SignupRequest(
                    name = name.trim(),
                    email = email.trim(),
                    phone = phone.trim(),
                    password = password,
                    referredBy = referredBy.trim()
                )
            )
        }
        result.onSuccess { user ->
            if (user.email.isNotBlank()) {
                sessionManager.saveUserEmail(user.email)
            }
        }
        return result
    }

    suspend fun fetchUserProfile(email: String): Result<UserProfile> =
        executeUserProfileApi("getUser", fallbackEmail = email) { url ->
            api.getUser(url, email = email)
        }

    suspend fun fetchUserTransactions(email: String): Result<List<TransactionRecord>> =
        executeListApi("getTransactions") { url -> api.getTransactions(url, email = email) }

    suspend fun fetchUserPlans(email: String): Result<List<UserPlan>> =
        executeListApi("getUserPlans") { url -> api.getUserPlans(url, email = email) }

    suspend fun submitTransaction(
        email: String,
        type: String,
        amount: Double,
        details: String,
        proofUri: Uri?
    ): Result<TransactionRecord> {
        val proofBase64 = if (proofUri != null) encodeImageToBase64(proofUri) else ""
        return executeApi("submitTransaction") { url ->
            api.submitTransaction(
                url,
                SubmitTransactionRequest(
                    email = email,
                    type = type,
                    amount = amount,
                    details = details,
                    proofBase64 = proofBase64
                )
            )
        }
    }

    suspend fun submitKyc(
        email: String,
        pan: String,
        frontUri: Uri?,
        backUri: Uri?,
        selfieUri: Uri?
    ): Result<VerificationRecord> {
        val frontB64 = if (frontUri != null) encodeImageToBase64(frontUri) else ""
        val backB64 = if (backUri != null) encodeImageToBase64(backUri) else ""
        val selfieB64 = if (selfieUri != null) encodeImageToBase64(selfieUri) else ""
        return executeApi("submitKyc") { url ->
            api.submitKyc(
                url,
                SubmitKycRequest(
                    email = email,
                    pan = pan.trim().uppercase(),
                    frontBase64 = frontB64,
                    backBase64 = backB64,
                    selfieBase64 = selfieB64
                )
            )
        }
    }

    // ==================== ADMIN OPERATIONS ====================

    suspend fun fetchAllTransactionsForAdmin(adminEmail: String): Result<List<TransactionRecord>> =
        executeListApi("adminGetTransactions") { url -> api.getTransactions(url, adminEmail = adminEmail) }

    suspend fun fetchPendingVerifications(adminEmail: String): Result<List<VerificationRecord>> =
        executeListApi("getPendingVerifications") { url -> api.getPendingVerifications(url, adminEmail = adminEmail) }

    suspend fun fetchAllUsers(adminEmail: String): Result<List<UserProfile>> =
        executeListApi("getAllUsers") { url -> api.getAllUsers(url, adminEmail = adminEmail) }

    suspend fun updateTransactionStatus(
        adminEmail: String,
        rowNumber: Int,
        status: String
    ): Result<String> = executeVoidApi("updateTransactionStatus") { url ->
        api.updateTransactionStatus(
            url,
            UpdateTransactionStatusRequest(
                adminEmail = adminEmail,
                rowNumber = rowNumber,
                status = status
            )
        )
    }

    suspend fun updateKycStatus(
        adminEmail: String,
        targetEmail: String,
        status: String,
        notification: String? = null
    ): Result<String> = executeVoidApi("updateKycStatus") { url ->
        api.updateKycStatus(
            url,
            UpdateKycStatusRequest(
                adminEmail = adminEmail,
                email = targetEmail,
                status = status,
                notification = notification
            )
        )
    }

    suspend fun updateUserBalance(
        adminEmail: String,
        targetEmail: String,
        invested: Double,
        totalAmount: Double,
        withdrawableLimit: Double,
        interestRate: Double,
        notification: String
    ): Result<String> = executeVoidApi("updateUserBalance") { url ->
        api.updateUserBalance(
            url,
            UpdateUserBalanceRequest(
                adminEmail = adminEmail,
                email = targetEmail,
                invested = invested,
                totalAmount = totalAmount,
                withdrawableLimit = withdrawableLimit,
                interestRate = interestRate,
                notification = notification
            )
        )
    }

    private suspend fun encodeImageToBase64(uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Unable to read selected image.")
        val original = stream.use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalStateException("Unsupported image format.")

        val maxDim = 1024
        val scale = max(original.width, original.height).toFloat() / maxDim
        val resized = if (scale > 1f) {
            Bitmap.createScaledBitmap(
                original,
                (original.width / scale).toInt(),
                (original.height / scale).toInt(),
                true
            )
        } else {
            original
        }

        val baos = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 75, baos)
        if (resized !== original) resized.recycle()
        original.recycle()
        Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }
}
