package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.SavedCredentials
import com.example.data.local.SessionManager
import com.example.data.model.AdminDashboardData
import com.example.data.model.AdminPendingKyc
import com.example.data.model.AdminPendingTx
import com.example.data.model.AdminStatsData
import com.example.data.model.AdminUserWithPlans
import com.example.data.model.AppSettingsData
import com.example.data.model.DepositMethod
import com.example.data.model.HistoryItem
import com.example.data.model.NotificationItem
import com.example.data.model.PlanOption
import com.example.data.model.PlansInfo
import com.example.data.model.UserPlanItem
import com.example.data.model.UserProfile
import com.example.data.model.WithdrawMethod
import com.example.data.remote.AppsScriptApiService
import com.example.data.remote.NetworkClient
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class MyFundRepository(
    private val context: Context,
    private val api: AppsScriptApiService,
    private val sessionManager: SessionManager
) {
    companion object {
        private const val TAG = "MyFundAPI"
        private val TEXT_PLAIN_MEDIA_TYPE = "text/plain; charset=utf-8".toMediaType()

        private val mapType = Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            Any::class.java
        )

        fun parseUserProfileFromMap(
            root: Map<String, Any?>,
            fallbackIdentifier: String = ""
        ): UserProfile {
            val userObj = optMap(root, "data") ?: optMap(root, "user") ?: root
            val resolvedEmail = optString(userObj, "email", "Email")
                .ifBlank { optString(root, "email", "Email") }
                .ifBlank { if (fallbackIdentifier.contains("@")) fallbackIdentifier.trim() else "" }

            return UserProfile(
                email = resolvedEmail,
                name = optString(userObj, "name", "Name"),
                invested = optDouble(userObj, "inv", "Invested", "invested"),
                totalWithdrawal = optDouble(userObj, "wit", "Total Withdrawal", "totalWithdrawal"),
                interestRate = optDouble(userObj, "roi", "Interest (%)", "interestRate"),
                withdrawableLimit = optDouble(userObj, "lim", "Withdrawable Limit", "withdrawableLimit"),
                phone = optString(userObj, "phone", "Phone"),
                userId = optString(userObj, "uid", "User ID", "userId"),
                photoUrl = optString(userObj, "photo", "PhotoURL", "photoUrl"),
                totalAmount = optDouble(userObj, "balance", "Total Amount", "totalAmount"),
                status = optString(userObj, "kycStatus", "Status", "status").ifBlank { "Not Verified" }
            )
        }

        fun parseUserProfileJson(
            rawBody: String,
            fallbackIdentifier: String = ""
        ): Result<UserProfile> {
            val rootResult = parseJsonObject(rawBody)
            val root = rootResult.getOrElse { return Result.failure(it) }
            if (!optBoolean(root, "success")) {
                return Result.failure(IllegalStateException(extractFriendlyError(root)))
            }
            return Result.success(parseUserProfileFromMap(root, fallbackIdentifier))
        }

        private fun parseJsonObject(rawBody: String): Result<Map<String, Any?>> {
            val trimmed = rawBody.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("<")) {
                return Result.failure(
                    IllegalStateException("Unable to reach the server right now. Please try again shortly.")
                )
            }
            return try {
                val parsed = NetworkClient.moshi.adapter<Map<String, Any?>>(mapType).fromJson(trimmed)
                if (parsed == null) {
                    Result.failure(IllegalStateException("We received an unexpected response. Please try again."))
                } else {
                    Result.success(parsed)
                }
            } catch (e: Exception) {
                Log.e(TAG, "JSON parse error: ${e.message}", e)
                Result.failure(IllegalStateException("We couldn't read the server response. Please try again."))
            }
        }

        private fun extractFriendlyError(root: Map<String, Any?>): String {
            return optString(root, "error", "msg", "message")
                .ifBlank { "Something went wrong. Please check your details and try again." }
        }

        private fun extractFriendlySuccessMsg(root: Map<String, Any?>, defaultMsg: String): String {
            return optString(root, "msg", "message").ifBlank { defaultMsg }
        }

        private fun optMap(obj: Map<String, Any?>, key: String): Map<String, Any?>? {
            val raw = obj[key] as? Map<*, *> ?: return null
            return raw.entries.associate { (k, v) -> k.toString() to v }
        }

        private fun optListOfMaps(obj: Map<String, Any?>, vararg keys: String): List<Map<String, Any?>> {
            for (key in keys) {
                val raw = obj[key] as? List<*> ?: continue
                return raw.mapNotNull { item ->
                    (item as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v }
                }
            }
            return emptyList()
        }

        private fun optBoolean(obj: Map<String, Any?>, key: String): Boolean {
            return when (val raw = obj[key]) {
                is Boolean -> raw
                is String -> raw.trim().equals("true", ignoreCase = true)
                is Number -> raw.toInt() != 0
                else -> false
            }
        }

        private fun optString(obj: Map<String, Any?>, vararg keys: String): String {
            for (key in keys) {
                val raw = obj[key] ?: continue
                val value = raw.toString().trim()
                if (value.isNotEmpty() && !value.equals("null", ignoreCase = true)) {
                    return value
                }
            }
            return ""
        }

        private fun optDouble(obj: Map<String, Any?>, vararg keys: String): Double {
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

        private fun optInt(obj: Map<String, Any?>, vararg keys: String): Int {
            return optDouble(obj, *keys).toInt()
        }
    }

    fun getSavedCredentials(): SavedCredentials? = sessionManager.getSavedCredentials()

    fun logout() {
        sessionManager.clearSession()
    }

    private suspend fun postAction(
        action: String,
        data: Map<String, Any?> = emptyMap()
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        try {
            val payloadMap = mapOf(
                "action" to action,
                "data" to data
            )
            val jsonPayload = NetworkClient.moshi.adapter<Map<String, Any?>>(mapType).toJson(payloadMap)
            Log.d(TAG, "POST action=$action")
            val body = jsonPayload.toRequestBody(TEXT_PLAIN_MEDIA_TYPE)
            val response = api.postAction(BuildConfig.APPS_SCRIPT_URL, body)
            val rawString = if (response.isSuccessful) {
                response.body()?.string().orEmpty()
            } else {
                response.errorBody()?.string().orEmpty()
            }
            Log.d(TAG, "POST action=$action HTTP=${response.code()} body=$rawString")

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("Unable to connect to the server. Please try again.")
                )
            }

            val root = parseJsonObject(rawString).getOrElse {
                return@withContext Result.failure(it)
            }

            if (!optBoolean(root, "success")) {
                return@withContext Result.failure(IllegalStateException(extractFriendlyError(root)))
            }
            Result.success(root)
        } catch (e: Exception) {
            Log.e(TAG, "POST action=$action failed: ${e.message}", e)
            Result.failure(
                IllegalStateException("Connection failed. Please check your internet connection and try again.")
            )
        }
    }

    // ==================== AUTH ====================

    suspend fun login(username: String, password: String, saveSession: Boolean = true): Result<UserProfile> {
        val result = postAction(
            action = "login",
            data = mapOf(
                "username" to username.trim(),
                "password" to password
            )
        ).map { root ->
            applyLocalProfileOverride(parseUserProfileFromMap(root, username))
        }
        if (saveSession) {
            result.onSuccess {
                sessionManager.saveCredentials(username.trim(), password)
            }
        }
        return result
    }

    private fun profileOverrideKey(user: UserProfile): String {
        return user.email.ifBlank { user.userId }.trim().lowercase()
    }

    private fun applyLocalProfileOverride(user: UserProfile): UserProfile {
        val key = profileOverrideKey(user)
        val override = sessionManager.getProfileOverride(key) ?: return user
        val resolvedPhoto = if (override.photoPath.isNotBlank()) {
            val filePath = override.photoPath.removePrefix("file://")
            if (override.photoPath.startsWith("http") || File(filePath).exists()) {
                override.photoPath
            } else {
                user.photoUrl
            }
        } else {
            user.photoUrl
        }
        return user.copy(
            name = override.name.ifBlank { user.name },
            phone = override.phone.ifBlank { user.phone },
            photoUrl = resolvedPhoto
        )
    }

    suspend fun updateUserProfile(
        user: UserProfile,
        newName: String,
        newPhone: String,
        newPhotoUri: Uri?
    ): Result<Pair<UserProfile, String>> = withContext(Dispatchers.IO) {
        try {
            val trimmedName = newName.trim().ifBlank { user.name }
            val trimmedPhone = newPhone.trim().ifBlank { user.phone }
            val key = profileOverrideKey(user)

            var savedPhotoUriString = user.photoUrl
            var photoDataUrl: String? = null
            if (newPhotoUri != null) {
                val (localUri, dataUrl) = saveResizedAvatar(newPhotoUri, key)
                savedPhotoUriString = localUri
                photoDataUrl = dataUrl
            }

            sessionManager.saveProfileOverride(
                userKey = key,
                name = trimmedName,
                phone = trimmedPhone,
                photoPath = savedPhotoUriString
            )

            val updatedProfile = user.copy(
                name = trimmedName,
                phone = trimmedPhone,
                photoUrl = savedPhotoUriString
            )

            val payload = mutableMapOf<String, Any?>(
                "uid" to user.userId,
                "email" to user.email,
                "name" to trimmedName,
                "phone" to trimmedPhone
            )
            if (!photoDataUrl.isNullOrBlank()) {
                payload["photo"] = photoDataUrl
            }

            val remoteResult = postAction("updateProfile", payload)
            val message = remoteResult.fold(
                onSuccess = { extractFriendlySuccessMsg(it, "Profile updated successfully.") },
                onFailure = { "Profile updated." }
            )
            Result.success(updatedProfile to message)
        } catch (e: Exception) {
            Log.e(TAG, "updateUserProfile failed: ${e.message}", e)
            Result.failure(IllegalStateException(e.message ?: "Unable to update profile photo."))
        }
    }

    suspend fun sendSignupOtp(email: String, phone: String): Result<String> {
        return postAction(
            action = "sendSignupOtp",
            data = mapOf(
                "email" to email.trim(),
                "phone" to phone.trim()
            )
        ).map { extractFriendlySuccessMsg(it, "Verification code sent to your email.") }
    }

    suspend fun verifyOtpAndRegister(
        name: String,
        surname: String,
        email: String,
        phone: String,
        pass: String,
        ref: String,
        otp: String
    ): Result<String> {
        return postAction(
            action = "verifyOtpAndRegister",
            data = mapOf(
                "name" to name.trim(),
                "surname" to surname.trim(),
                "email" to email.trim(),
                "phone" to phone.trim(),
                "pass" to pass,
                "ref" to ref.trim(),
                "otp" to otp.trim()
            )
        ).map { extractFriendlySuccessMsg(it, "Account created successfully! Please sign in.") }
    }

    suspend fun changePassword(
        uid: String,
        oldPassword: String,
        newPassword: String
    ): Result<String> {
        return postAction(
            action = "changePassword",
            data = mapOf(
                "uid" to uid,
                "oldPassword" to oldPassword,
                "newPassword" to newPassword
            )
        ).map { extractFriendlySuccessMsg(it, "Password updated successfully.") }
    }

    // ==================== PLANS, HISTORY, SETTINGS, NOTIFICATIONS ====================

    suspend fun getPlansInfo(): Result<PlansInfo> {
        return postAction("getPlansInfo").map { root ->
            val container = optMap(root, "data") ?: root
            val planMaps = optListOfMaps(container, "plans")
                .ifEmpty { optListOfMaps(root, "plans") }
            PlansInfo(
                ratePercent = optDouble(container, "ratePercent", "rate"),
                period = optString(container, "period").ifBlank { "monthly" },
                months = optInt(container, "months", "duration").let { if (it > 0) it else 12 },
                plans = planMaps.map { p ->
                    PlanOption(
                        name = optString(p, "name", "planName"),
                        amount = optDouble(p, "amount", "amt"),
                        monthlyReturn = optDouble(p, "monthlyReturn"),
                        totalReturn = optDouble(p, "totalReturn")
                    )
                }
            )
        }
    }

    suspend fun getUserPlans(uid: String): Result<List<UserPlanItem>> {
        return postAction("getUserPlans", mapOf("uid" to uid)).map { root ->
            optListOfMaps(root, "data", "plans").map { p ->
                UserPlanItem(
                    id = optString(p, "id", "planId"),
                    name = optString(p, "name", "planName"),
                    amt = optDouble(p, "amt", "amount"),
                    status = optString(p, "status").ifBlank { "Active" },
                    paid = optInt(p, "paid", "paidReturns"),
                    max = optInt(p, "max", "maxReturns")
                )
            }
        }
    }

    suspend fun getHistory(emailOrUid: String): Result<List<HistoryItem>> {
        return postAction("getHistory", mapOf("emailOrUid" to emailOrUid)).map { root ->
            optListOfMaps(root, "data", "history").map { h ->
                HistoryItem(
                    date = optString(h, "date", "Date"),
                    type = optString(h, "type", "Type"),
                    amount = optDouble(h, "amount", "Amount"),
                    details = optString(h, "details", "Details"),
                    status = optString(h, "status", "Status"),
                    proof = optString(h, "proof", "Proof")
                )
            }
        }
    }

    suspend fun getAppSettings(): Result<AppSettingsData> {
        return postAction("getAppSettings").map { root ->
            val dataMap = optMap(root, "data") ?: root
            val stringMap = dataMap.entries.associate { (k, v) -> k to (v?.toString()?.trim().orEmpty()) }
            val deposits = (1..5).mapNotNull { idx ->
                val title = stringMap["DEP_TITLE_$idx"].orEmpty()
                val addr = stringMap["DEP_ADDR_$idx"].orEmpty()
                val qr = stringMap["DEP_QR_$idx"].orEmpty()
                if (title.isNotBlank() || addr.isNotBlank()) {
                    DepositMethod(index = idx, title = title.ifBlank { "Method $idx" }, address = addr, qrUrl = qr)
                } else null
            }
            val withdraws = (1..5).mapNotNull { idx ->
                val title = stringMap["WIT_TITLE_$idx"].orEmpty()
                val helper = stringMap["WIT_H_$idx"].orEmpty()
                if (title.isNotBlank()) {
                    WithdrawMethod(index = idx, title = title, helper = helper)
                } else null
            }
            AppSettingsData(depositMethods = deposits, withdrawMethods = withdraws, raw = stringMap)
        }
    }

    suspend fun getNotifications(uid: String): Result<List<NotificationItem>> {
        return postAction("getNotifications", mapOf("uid" to uid)).map { root ->
            optListOfMaps(root, "data", "notifications").map { n ->
                NotificationItem(
                    title = optString(n, "title", "subject").ifBlank { "Notification" },
                    message = optString(n, "message", "msg", "body"),
                    date = optString(n, "date", "time", "createdAt")
                )
            }
        }
    }

    // ==================== USER TRANSACTIONS & KYC ====================

    suspend fun submitDeposit(
        email: String,
        amount: Double,
        txnId: String,
        method: String,
        planName: String,
        screenshotUri: Uri
    ): Result<String> {
        val dataUrl = encodeImageDataUrl(screenshotUri)
        return postAction(
            action = "submitDeposit",
            data = mapOf(
                "email" to email,
                "amount" to amount,
                "txnId" to txnId.trim(),
                "method" to method,
                "planName" to planName,
                "fileData" to dataUrl
            )
        ).map { extractFriendlySuccessMsg(it, "Deposit submitted! It is currently pending verification.") }
    }

    suspend fun activatePlan(
        email: String,
        uid: String,
        planName: String,
        amount: Double
    ): Result<String> {
        return postAction(
            action = "activatePlan",
            data = mapOf(
                "email" to email,
                "uid" to uid,
                "planName" to planName,
                "amount" to amount
            )
        ).map { extractFriendlySuccessMsg(it, "$planName activated from your balance!") }
    }

    suspend fun submitWithdrawal(
        email: String,
        amount: Double,
        details: String,
        method: String
    ): Result<String> {
        return postAction(
            action = "submitWithdrawal",
            data = mapOf(
                "email" to email,
                "amount" to amount,
                "details" to details.trim(),
                "method" to method
            )
        ).map { extractFriendlySuccessMsg(it, "Withdrawal request submitted and pending review.") }
    }

    suspend fun submitKYC(
        email: String,
        panNum: String,
        frontUri: Uri,
        backUri: Uri,
        selfieUri: Uri
    ): Result<String> {
        val adFront = encodeImageDataUrl(frontUri)
        val adBack = encodeImageDataUrl(backUri)
        val panImg = encodeImageDataUrl(selfieUri)
        return postAction(
            action = "submitKYC",
            data = mapOf(
                "email" to email,
                "panNum" to panNum.trim().uppercase(),
                "adFront" to adFront,
                "adBack" to adBack,
                "panImg" to panImg
            )
        ).map { extractFriendlySuccessMsg(it, "KYC documents submitted for review.") }
    }

    // ==================== ADMIN ACTIONS (IN-MEMORY CREDENTIALS ONLY) ====================

    suspend fun adminLogin(adminId: String, adminPass: String): Result<String> {
        return postAction(
            action = "adminLogin",
            data = mapOf(
                "adminId" to adminId.trim(),
                "adminPass" to adminPass
            )
        ).map { extractFriendlySuccessMsg(it, "Admin session unlocked.") }
    }

    suspend fun adminDashboard(adminId: String, adminPass: String): Result<AdminDashboardData> {
        return postAction(
            action = "adminDashboard",
            data = mapOf("adminId" to adminId.trim(), "adminPass" to adminPass)
        ).map { root ->
            val container = optMap(root, "data") ?: root
            fun parseTxList(key: String) = optListOfMaps(container, key).map { item ->
                AdminPendingTx(
                    id = optString(item, "id", "rowId"),
                    email = optString(item, "email"),
                    type = optString(item, "type"),
                    amount = optDouble(item, "amount"),
                    details = optString(item, "details"),
                    proof = optString(item, "proof"),
                    status = optString(item, "status")
                )
            }
            val kycs = optListOfMaps(container, "kycs").map { item ->
                val userMap = optMap(item, "user") ?: emptyMap()
                AdminPendingKyc(
                    id = optString(item, "id"),
                    email = optString(item, "email"),
                    panNo = optString(item, "panNo", "panNum"),
                    adFront = optString(item, "adFront"),
                    adBack = optString(item, "adBack"),
                    panImg = optString(item, "panImg"),
                    userName = optString(userMap, "name"),
                    userUid = optString(userMap, "uid"),
                    userPhone = optString(userMap, "phone")
                )
            }
            AdminDashboardData(
                deps = parseTxList("deps"),
                withs = parseTxList("withs"),
                kycs = kycs,
                refs = parseTxList("refs"),
                totalUsers = optInt(container, "totalUsers")
            )
        }
    }

    suspend fun adminUsers(adminId: String, adminPass: String): Result<List<UserProfile>> {
        return postAction(
            action = "adminUsers",
            data = mapOf("adminId" to adminId.trim(), "adminPass" to adminPass)
        ).map { root ->
            optListOfMaps(root, "data", "users").map { u -> parseUserProfileFromMap(u) }
        }
    }

    suspend fun adminUsersWithPlans(adminId: String, adminPass: String): Result<List<AdminUserWithPlans>> {
        return postAction(
            action = "adminUsersWithPlans",
            data = mapOf("adminId" to adminId.trim(), "adminPass" to adminPass)
        ).map { root ->
            optListOfMaps(root, "data", "users").map { u ->
                val plans = optListOfMaps(u, "plans").map { p ->
                    UserPlanItem(
                        id = optString(p, "id", "planId"),
                        name = optString(p, "name", "planName"),
                        amt = optDouble(p, "amt", "amount"),
                        status = optString(p, "status"),
                        paid = optInt(p, "paid"),
                        max = optInt(p, "max")
                    )
                }
                AdminUserWithPlans(
                    uid = optString(u, "uid", "userId"),
                    name = optString(u, "name"),
                    email = optString(u, "email"),
                    plans = plans
                )
            }
        }
    }

    suspend fun adminStats(
        adminId: String,
        adminPass: String,
        start: String,
        end: String
    ): Result<AdminStatsData> {
        return postAction(
            action = "adminStats",
            data = mapOf(
                "adminId" to adminId.trim(),
                "adminPass" to adminPass,
                "start" to start.trim(),
                "end" to end.trim()
            )
        ).map { root ->
            val container = optMap(root, "data") ?: root
            val pairs = container.entries
                .filter { (k, v) -> k != "success" && k != "msg" && k != "error" && v != null }
                .map { (k, v) -> k to v.toString() }
            AdminStatsData(metrics = pairs)
        }
    }

    suspend fun adminAction(
        adminId: String,
        adminPass: String,
        type: String,
        id: String,
        act: String,
        email: String,
        amount: Double
    ): Result<String> {
        return postAction(
            action = "adminAction",
            data = mapOf(
                "adminId" to adminId.trim(),
                "adminPass" to adminPass,
                "type" to type,
                "id" to id,
                "act" to act,
                "email" to email,
                "amount" to amount
            )
        ).map { extractFriendlySuccessMsg(it, "$type marked as $act.") }
    }

    suspend fun adminPayReturn(
        adminId: String,
        adminPass: String,
        uid: String,
        planId: String
    ): Result<String> {
        return postAction(
            action = "adminPayReturn",
            data = mapOf(
                "adminId" to adminId.trim(),
                "adminPass" to adminPass,
                "uid" to uid,
                "planId" to planId
            )
        ).map { extractFriendlySuccessMsg(it, "Monthly return credited.") }
    }

    suspend fun adminCredit(
        adminId: String,
        adminPass: String,
        uid: String,
        amount: Double,
        type: String
    ): Result<String> {
        return postAction(
            action = "adminCredit",
            data = mapOf(
                "adminId" to adminId.trim(),
                "adminPass" to adminPass,
                "uid" to uid.trim(),
                "amount" to amount,
                "type" to type
            )
        ).map { extractFriendlySuccessMsg(it, "Credit applied successfully.") }
    }

    suspend fun adminNotify(
        adminId: String,
        adminPass: String,
        uid: String,
        title: String,
        message: String
    ): Result<String> {
        return postAction(
            action = "adminNotify",
            data = mapOf(
                "adminId" to adminId.trim(),
                "adminPass" to adminPass,
                "uid" to uid.trim(),
                "title" to title.trim(),
                "message" to message.trim()
            )
        ).map { extractFriendlySuccessMsg(it, "Notification sent.") }
    }

    // ==================== IMAGE RESIZE & DATA URL ENCODING ====================

    private suspend fun encodeImageDataUrl(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = compressUriToJpegBytes(uri)
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        "data:image/jpeg;base64,$b64"
    }

    private fun saveResizedAvatar(uri: Uri, userKey: String): Pair<String, String> {
        val bytes = compressUriToJpegBytes(uri)
        val avatarsDir = File(context.filesDir, "avatars").apply { mkdirs() }
        val safeName = userKey.replace(Regex("[^a-z0-9_]"), "_").ifBlank { "user" }
        val outFile = File(avatarsDir, "avatar_${safeName}_${System.currentTimeMillis()}.jpg")
        avatarsDir.listFiles()?.filter { it.name.startsWith("avatar_${safeName}_") }?.forEach { it.delete() }
        FileOutputStream(outFile).use { it.write(bytes) }
        val dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "file://${outFile.absolutePath}" to dataUrl
    }

    private fun compressUriToJpegBytes(uri: Uri): ByteArray {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Unable to read the selected image.")
        val original = stream.use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalStateException("Unsupported image format. Please choose a valid photo.")

        val maxDim = 600
        val largest = max(original.width, original.height)
        val resized = if (largest > maxDim) {
            val scale = maxDim.toFloat() / largest.toFloat()
            val targetW = (original.width * scale).toInt().coerceAtLeast(1)
            val targetH = (original.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(original, targetW, targetH, true)
        } else {
            original
        }

        val baos = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 50, baos)
        if (resized !== original) resized.recycle()
        original.recycle()
        return baos.toByteArray()
    }
}
