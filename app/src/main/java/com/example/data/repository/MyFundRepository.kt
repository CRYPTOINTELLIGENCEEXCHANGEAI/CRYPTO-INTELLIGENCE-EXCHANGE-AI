package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

class MyFundRepository(
    private val context: Context,
    private val api: AppsScriptApiService,
    private val sessionManager: SessionManager
) {
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

    private suspend fun <T> executeApi(call: suspend (String) -> ApiResponse<T>): Result<T> {
        return withContext(Dispatchers.IO) {
            try {
                val url = requireEndpointUrl()
                val response = call(url)
                if (response.success && response.data != null) {
                    Result.success(response.data)
                } else {
                    Result.failure(IllegalStateException(response.message.ifBlank { "Request failed." }))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun executeVoidApi(call: suspend (String) -> ApiResponse<*>): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val url = requireEndpointUrl()
                val response = call(url)
                if (response.success) {
                    Result.success(response.message.ifBlank { "Success" })
                } else {
                    Result.failure(IllegalStateException(response.message.ifBlank { "Request failed." }))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun checkAppVersion(): Result<AppVersionInfo> =
        executeApi { url -> api.checkVersion(url) }

    suspend fun login(email: String, password: String): Result<UserProfile> {
        val result = executeApi { url ->
            api.login(url, LoginRequest(email = email.trim(), password = password))
        }
        result.onSuccess { user ->
            sessionManager.saveUserEmail(user.email)
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
        val result = executeApi { url ->
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
            sessionManager.saveUserEmail(user.email)
        }
        return result
    }

    suspend fun fetchUserProfile(email: String): Result<UserProfile> =
        executeApi { url -> api.getUser(url, email = email) }

    suspend fun fetchUserTransactions(email: String): Result<List<TransactionRecord>> =
        executeApi { url -> api.getTransactions(url, email = email) }

    suspend fun fetchUserPlans(email: String): Result<List<UserPlan>> =
        executeApi { url -> api.getUserPlans(url, email = email) }

    suspend fun submitTransaction(
        email: String,
        type: String,
        amount: Double,
        details: String,
        proofUri: Uri?
    ): Result<TransactionRecord> {
        val proofBase64 = if (proofUri != null) encodeImageToBase64(proofUri) else ""
        return executeApi { url ->
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
        return executeApi { url ->
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
        executeApi { url -> api.getTransactions(url, adminEmail = adminEmail) }

    suspend fun fetchPendingVerifications(adminEmail: String): Result<List<VerificationRecord>> =
        executeApi { url -> api.getPendingVerifications(url, adminEmail = adminEmail) }

    suspend fun fetchAllUsers(adminEmail: String): Result<List<UserProfile>> =
        executeApi { url -> api.getAllUsers(url, adminEmail = adminEmail) }

    suspend fun updateTransactionStatus(
        adminEmail: String,
        rowNumber: Int,
        status: String
    ): Result<String> = executeVoidApi { url ->
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
    ): Result<String> = executeVoidApi { url ->
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
    ): Result<String> = executeVoidApi { url ->
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
