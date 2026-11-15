package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.SessionManager
import com.example.data.model.AdminDashboardData
import com.example.data.model.AdminStatsData
import com.example.data.model.AdminUserWithPlans
import com.example.data.model.AppSettingsData
import com.example.data.model.HistoryItem
import com.example.data.model.NotificationItem
import com.example.data.model.PlansInfo
import com.example.data.model.UserPlanItem
import com.example.data.model.UserProfile
import com.example.data.remote.NetworkClient
import com.example.data.repository.MyFundRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Splash : AuthUiState
    data object Unauthenticated : AuthUiState
    data class Authenticated(val user: UserProfile) : AuthUiState
}

data class PendingSignup(
    val name: String,
    val surname: String,
    val email: String,
    val phone: String,
    val pass: String,
    val ref: String
)

data class MainUiState(
    val authState: AuthUiState = AuthUiState.Splash,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isSubmitting: Boolean = false,
    val pendingSignup: PendingSignup? = null,
    val plansInfo: PlansInfo = PlansInfo(),
    val userPlans: List<UserPlanItem> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    val appSettings: AppSettingsData = AppSettingsData(),
    val notifications: List<NotificationItem> = emptyList(),
    // Admin in-memory session (never saved to disk)
    val adminId: String = "",
    val adminPass: String = "",
    val isAdminUnlocked: Boolean = false,
    val adminDashboard: AdminDashboardData = AdminDashboardData(),
    val adminUsers: List<UserProfile> = emptyList(),
    val adminUsersWithPlans: List<AdminUserWithPlans> = emptyList(),
    val adminStats: AdminStatsData = AdminStatsData(),
    val message: String? = null,
    val errorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = SessionManager(application.applicationContext)
    private val repository = MyFundRepository(
        context = application.applicationContext,
        api = NetworkClient.apiService,
        sessionManager = sessionManager
    )

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        runSplashAndAutoLogin()
    }

    private fun runSplashAndAutoLogin() {
        viewModelScope.launch {
            val saved = repository.getSavedCredentials()
            if (saved == null) {
                delay(2000)
                _uiState.update { it.copy(authState = AuthUiState.Unauthenticated) }
            } else {
                val startTime = System.currentTimeMillis()
                val loginResult = repository.login(saved.username, saved.password, saveSession = false)
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < 2000) delay(2000 - elapsed)

                loginResult.onSuccess { user ->
                    _uiState.update { it.copy(authState = AuthUiState.Authenticated(user)) }
                    loadUserData(user)
                }.onFailure {
                    _uiState.update { it.copy(authState = AuthUiState.Unauthenticated) }
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(message = null, errorMessage = null) }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.login(username, password, saveSession = true)
                .onSuccess { user ->
                    _uiState.update {
                        it.copy(
                            authState = AuthUiState.Authenticated(user),
                            isLoading = false,
                            message = "Welcome back, ${user.name.ifBlank { user.email }}"
                        )
                    }
                    loadUserData(user)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = err.message)
                    }
                }
        }
    }

    fun startSignupAndSendOtp(
        name: String,
        surname: String,
        email: String,
        phone: String,
        pass: String,
        ref: String,
        onOtpSent: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.sendSignupOtp(email, phone)
                .onSuccess { msg ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pendingSignup = PendingSignup(name, surname, email, phone, pass, ref),
                            message = msg
                        )
                    }
                    onOtpSent()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                }
        }
    }

    fun resendSignupOtp(onResent: () -> Unit) {
        val pending = _uiState.value.pendingSignup ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.sendSignupOtp(pending.email, pending.phone)
                .onSuccess { msg ->
                    _uiState.update { it.copy(isLoading = false, message = msg) }
                    onResent()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                }
        }
    }

    fun verifyOtpAndRegister(otp: String, onRegistered: () -> Unit) {
        val pending = _uiState.value.pendingSignup ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.verifyOtpAndRegister(
                name = pending.name,
                surname = pending.surname,
                email = pending.email,
                phone = pending.phone,
                pass = pending.pass,
                ref = pending.ref,
                otp = otp
            ).onSuccess { msg ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pendingSignup = null,
                        message = msg
                    )
                }
                onRegistered()
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
            }
        }
    }

    fun cancelOtpScreen() {
        _uiState.update { it.copy(pendingSignup = null) }
    }

    fun logout() {
        repository.logout()
        _uiState.value = MainUiState(authState = AuthUiState.Unauthenticated)
    }

    fun refreshHome() {
        val user = (_uiState.value.authState as? AuthUiState.Authenticated)?.user ?: return
        val saved = repository.getSavedCredentials()
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            val updatedUser = if (saved != null) {
                repository.login(saved.username, saved.password, saveSession = false).getOrDefault(user)
            } else {
                user
            }
            _uiState.update { it.copy(authState = AuthUiState.Authenticated(updatedUser)) }
            loadUserData(updatedUser)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private fun loadUserData(user: UserProfile) {
        viewModelScope.launch {
            val emailOrUid = user.email.ifBlank { user.userId }
            val uid = user.userId.ifBlank { user.email }

            val plansInfoDef = async { repository.getPlansInfo() }
            val userPlansDef = async { repository.getUserPlans(uid) }
            val historyDef = async { repository.getHistory(emailOrUid) }
            val settingsDef = async { repository.getAppSettings() }
            val notifDef = async { repository.getNotifications(uid) }

            val plansInfo = plansInfoDef.await().getOrDefault(_uiState.value.plansInfo)
            val userPlans = userPlansDef.await().getOrDefault(_uiState.value.userPlans)
            val history = historyDef.await().getOrDefault(_uiState.value.history)
            val settings = settingsDef.await().getOrDefault(_uiState.value.appSettings)
            val notifications = notifDef.await().getOrDefault(_uiState.value.notifications)

            _uiState.update {
                it.copy(
                    plansInfo = plansInfo,
                    userPlans = userPlans,
                    history = history,
                    appSettings = settings,
                    notifications = notifications
                )
            }
        }
    }

    fun refreshNotifications() {
        val user = (_uiState.value.authState as? AuthUiState.Authenticated)?.user ?: return
        val uid = user.userId.ifBlank { user.email }
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            repository.getNotifications(uid)
                .onSuccess { list -> _uiState.update { it.copy(notifications = list) } }
                .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun changePassword(oldPass: String, newPass: String, onSuccess: () -> Unit) {
        val user = (_uiState.value.authState as? AuthUiState.Authenticated)?.user ?: return
        val uid = user.userId.ifBlank { user.email }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            repository.changePassword(uid, oldPass, newPass)
                .onSuccess { msg ->
                    val saved = repository.getSavedCredentials()
                    if (saved != null) {
                        sessionManager.saveCredentials(saved.username, newPass)
                    }
                    _uiState.update { it.copy(isSubmitting = false, message = msg) }
                    onSuccess()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = err.message) }
                }
        }
    }

    fun updateProfile(
        newName: String,
        newPhone: String,
        newPhotoUri: Uri?,
        onSuccess: () -> Unit
    ) {
        val user = (_uiState.value.authState as? AuthUiState.Authenticated)?.user ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            repository.updateUserProfile(user, newName, newPhone, newPhotoUri)
                .onSuccess { (updatedUser, msg) ->
                    _uiState.update {
                        it.copy(
                            authState = AuthUiState.Authenticated(updatedUser),
                            isSubmitting = false,
                            message = msg
                        )
                    }
                    onSuccess()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = err.message) }
                }
        }
    }

    fun activatePlan(planName: String, amount: Double, onSuccess: () -> Unit) {
        val user = (_uiState.value.authState as? AuthUiState.Authenticated)?.user ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            repository.activatePlan(user.email, user.userId, planName, amount)
                .onSuccess { msg ->
                    _uiState.update { it.copy(isSubmitting = false, message = msg) }
                    refreshHome()
                    onSuccess()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = err.message) }
                }
        }
    }

    fun submitDeposit(
        amount: Double,
        txnId: String,
        method: String,
        planName: String,
        screenshotUri: Uri,
        onSuccess: () -> Unit
    ) {
        val user = (_uiState.value.authState as? AuthUiState.Authenticated)?.user ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            repository.submitDeposit(user.email, amount, txnId, method, planName, screenshotUri)
                .onSuccess { msg ->
                    _uiState.update { it.copy(isSubmitting = false, message = msg) }
                    refreshHome()
                    onSuccess()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = err.message) }
                }
        }
    }

    fun submitWithdrawal(
        amount: Double,
        details: String,
        method: String,
        onSuccess: () -> Unit
    ) {
        val user = (_uiState.value.authState as? AuthUiState.Authenticated)?.user ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            repository.submitWithdrawal(user.email, amount, details, method)
                .onSuccess { msg ->
                    _uiState.update { it.copy(isSubmitting = false, message = msg) }
                    refreshHome()
                    onSuccess()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = err.message) }
                }
        }
    }

    fun submitKYC(
        panNum: String,
        frontUri: Uri,
        backUri: Uri,
        selfieUri: Uri,
        onSuccess: () -> Unit
    ) {
        val user = (_uiState.value.authState as? AuthUiState.Authenticated)?.user ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            repository.submitKYC(user.email, panNum, frontUri, backUri, selfieUri)
                .onSuccess { msg ->
                    _uiState.update { it.copy(isSubmitting = false, message = msg) }
                    refreshHome()
                    onSuccess()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = err.message) }
                }
        }
    }

    // ==================== ADMIN IN-MEMORY ACTIONS ====================

    fun adminLogin(adminId: String, adminPass: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.adminLogin(adminId, adminPass)
                .onSuccess { msg ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            adminId = adminId.trim(),
                            adminPass = adminPass,
                            isAdminUnlocked = true,
                            message = msg
                        )
                    }
                    refreshAdminData()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                }
        }
    }

    fun lockAdminSession() {
        _uiState.update {
            it.copy(
                adminId = "",
                adminPass = "",
                isAdminUnlocked = false,
                adminDashboard = AdminDashboardData(),
                adminUsers = emptyList(),
                adminUsersWithPlans = emptyList(),
                adminStats = AdminStatsData()
            )
        }
    }

    fun refreshAdminData() {
        val id = _uiState.value.adminId
        val pass = _uiState.value.adminPass
        if (id.isBlank() || pass.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val dashDef = async { repository.adminDashboard(id, pass) }
            val usersDef = async { repository.adminUsers(id, pass) }
            val withPlansDef = async { repository.adminUsersWithPlans(id, pass) }

            val dash = dashDef.await().getOrDefault(_uiState.value.adminDashboard)
            val users = usersDef.await().getOrDefault(_uiState.value.adminUsers)
            val withPlans = withPlansDef.await().getOrDefault(_uiState.value.adminUsersWithPlans)

            _uiState.update {
                it.copy(
                    adminDashboard = dash,
                    adminUsers = users,
                    adminUsersWithPlans = withPlans,
                    isRefreshing = false
                )
            }
        }
    }

    fun fetchAdminStats(start: String, end: String) {
        val id = _uiState.value.adminId
        val pass = _uiState.value.adminPass
        if (id.isBlank() || pass.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.adminStats(id, pass, start, end)
                .onSuccess { stats ->
                    _uiState.update { it.copy(isLoading = false, adminStats = stats) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                }
        }
    }

    fun performAdminAction(
        type: String,
        itemId: String,
        act: String,
        email: String,
        amount: Double
    ) {
        val id = _uiState.value.adminId
        val pass = _uiState.value.adminPass
        if (id.isBlank() || pass.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            repository.adminAction(id, pass, type, itemId, act, email, amount)
                .onSuccess { msg ->
                    _uiState.update { it.copy(isSubmitting = false, message = msg) }
                    refreshAdminData()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = err.message) }
                }
        }
    }

    fun adminPayReturn(uid: String, planId: String) {
        val id = _uiState.value.adminId
        val pass = _uiState.value.adminPass
        if (id.isBlank() || pass.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            repository.adminPayReturn(id, pass, uid, planId)
                .onSuccess { msg ->
                    _uiState.update { it.copy(isSubmitting = false, message = msg) }
                    refreshAdminData()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = err.message) }
                }
        }
    }

    fun adminCredit(uid: String, amount: Double, type: String, onSuccess: () -> Unit) {
        val id = _uiState.value.adminId
        val pass = _uiState.value.adminPass
        if (id.isBlank() || pass.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            repository.adminCredit(id, pass, uid, amount, type)
                .onSuccess { msg ->
                    _uiState.update { it.copy(isSubmitting = false, message = msg) }
                    refreshAdminData()
                    onSuccess()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = err.message) }
                }
        }
    }

    fun adminNotify(uid: String, title: String, message: String, onSuccess: () -> Unit) {
        val id = _uiState.value.adminId
        val pass = _uiState.value.adminPass
        if (id.isBlank() || pass.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            repository.adminNotify(id, pass, uid, title, message)
                .onSuccess { msg ->
                    _uiState.update { it.copy(isSubmitting = false, message = msg) }
                    onSuccess()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = err.message) }
                }
        }
    }
}
