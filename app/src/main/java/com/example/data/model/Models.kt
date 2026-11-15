package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

const val ADMIN_EMAIL = "myfundglobal@gmail.com"

@JsonClass(generateAdapter = true)
data class UserProfile(
    @Json(name = "Email") val email: String = "",
    @Json(name = "Name") val name: String = "",
    @Json(name = "Invested") val invested: Double = 0.0,
    @Json(name = "Total Withdrawal") val totalWithdrawal: Double = 0.0,
    @Json(name = "Interest (%)") val interestRate: Double = 0.0,
    @Json(name = "Withdrawable Limit") val withdrawableLimit: Double = 0.0,
    @Json(name = "Phone") val phone: String = "",
    @Json(name = "User ID") val userId: String = "",
    @Json(name = "PhotoURL") val photoUrl: String = "",
    @Json(name = "Total Amount") val totalAmount: Double = 0.0,
    @Json(name = "Status") val status: String = "Unverified",
    @Json(name = "Notification") val notification: String = "",
    @Json(name = "ReferredBy") val referredBy: String = "",
    @Json(name = "Terms_Consent_Date") val termsConsentDate: String = "",
    @Json(name = "isAdmin") val isAdmin: Boolean = false,
    @Json(name = "_rowNumber") val rowNumber: Int = 0
) {
    val computedIsAdmin: Boolean
        get() = isAdmin || email.trim().equals(ADMIN_EMAIL, ignoreCase = true)
}

@JsonClass(generateAdapter = true)
data class TransactionRecord(
    @Json(name = "Date") val date: String = "",
    @Json(name = "Email") val email: String = "",
    @Json(name = "Type") val type: String = "",
    @Json(name = "Amount") val amount: Double = 0.0,
    @Json(name = "Details") val details: String = "",
    @Json(name = "Status") val status: String = "Pending",
    @Json(name = "Proof") val proof: String = "",
    @Json(name = "_rowNumber") val rowNumber: Int = 0
)

@JsonClass(generateAdapter = true)
data class VerificationRecord(
    @Json(name = "Date") val date: String = "",
    @Json(name = "Email") val email: String = "",
    @Json(name = "PAN") val pan: String = "",
    @Json(name = "Front") val front: String = "",
    @Json(name = "Back") val back: String = "",
    @Json(name = "Selfie") val selfie: String = "",
    @Json(name = "userStatus") val userStatus: String = "Pending",
    @Json(name = "userName") val userName: String = "",
    @Json(name = "_rowNumber") val rowNumber: Int = 0
)

@JsonClass(generateAdapter = true)
data class UserPlan(
    @Json(name = "PlanID") val planId: String = "",
    @Json(name = "UID") val uid: String = "",
    @Json(name = "Email") val email: String = "",
    @Json(name = "PlanName") val planName: String = "",
    @Json(name = "Amount") val amount: Double = 0.0,
    @Json(name = "Status") val status: String = "",
    @Json(name = "PaidReturns") val paidReturns: Double = 0.0,
    @Json(name = "MaxReturns") val maxReturns: Double = 0.0,
    @Json(name = "Date") val date: String = ""
)

@JsonClass(generateAdapter = true)
data class AppVersionInfo(
    @Json(name = "latestVersionCode") val latestVersionCode: Int = 1,
    @Json(name = "latestVersionName") val latestVersionName: String = "1.0",
    @Json(name = "minVersionCode") val minVersionCode: Int = 1,
    @Json(name = "forceUpdate") val forceUpdate: Boolean = false,
    @Json(name = "updateUrl") val updateUrl: String = "",
    @Json(name = "updateMessage") val updateMessage: String = ""
)

@JsonClass(generateAdapter = true)
data class ApiResponse<T>(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "message") val message: String = "",
    @Json(name = "error") val error: String = "",
    @Json(name = "data") val data: T? = null
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val action: String = "login",
    val email: String,
    val identifier: String = email,
    val userId: String = email,
    val password: String
)

@JsonClass(generateAdapter = true)
data class SignupRequest(
    val action: String = "signup",
    val name: String,
    val email: String,
    val phone: String,
    val password: String,
    val referredBy: String = ""
)

@JsonClass(generateAdapter = true)
data class SubmitTransactionRequest(
    val action: String = "submitTransaction",
    val email: String,
    val type: String,
    val amount: Double,
    val details: String,
    val proofUrl: String = "",
    val proofBase64: String = ""
)

@JsonClass(generateAdapter = true)
data class SubmitKycRequest(
    val action: String = "submitKyc",
    val email: String,
    val pan: String,
    val frontBase64: String = "",
    val backBase64: String = "",
    val selfieBase64: String = "",
    val frontUrl: String = "",
    val backUrl: String = "",
    val selfieUrl: String = ""
)

@JsonClass(generateAdapter = true)
data class UpdateTransactionStatusRequest(
    val action: String = "updateTransactionStatus",
    val adminEmail: String,
    val rowNumber: Int,
    val status: String
)

@JsonClass(generateAdapter = true)
data class UpdateKycStatusRequest(
    val action: String = "updateKycStatus",
    val adminEmail: String,
    val email: String,
    val status: String,
    val notification: String? = null
)

@JsonClass(generateAdapter = true)
data class UpdateUserBalanceRequest(
    val action: String = "updateUserBalance",
    val adminEmail: String,
    val email: String,
    val invested: Double,
    val totalAmount: Double,
    val withdrawableLimit: Double,
    val interestRate: Double,
    val notification: String
)
