package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.SessionManager
import com.example.data.model.AppVersionInfo
import com.example.data.model.TransactionRecord
import com.example.data.model.UserPlan
import com.example.data.model.UserProfile
import com.example.data.model.VerificationRecord
import com.example.data.remote.NetworkClient
import com.example.data.repository.MyFundRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object CheckingSession : AuthUiState
    data object Unauthenticated : AuthUiState
    data class Authenticated(val user: UserProfile) : AuthUiState
}

data class MainUiState(
    val authState: AuthUiState = AuthUiState.CheckingSession,
    val appsScriptUrl: String = "",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isSubmitting: Boolean = false,
    val transactions: List<TransactionRecord> = emptyList(),
    val userPlans: List<UserPlan> = emptyList(),
    val adminTransactions: List<TransactionRecord> = emptyList(),
    val adminVerifications: List<VerificationRecord> = emptyList(),
    val adminUsers: List<UserProfile> = emptyList(),
    val availableUpdate: AppVersionInfo? = null,
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
        observeEndpointUrl()
        observeSession()
    }

    private fun observeEndpointUrl() {
        viewModelScope.launch {
            repository.appsScriptUrlFlow.collectLatest { url ->
                _uiState.update { it.copy(appsScriptUrl = url) }
                if (url.isNotBlank()) {
                    checkStartupVersion()
                }
            }
        }
    }

    private fun observeSession() {
        viewModelScope.launch {
            repository.loggedInEmailFlow.collectLatest { savedEmail ->
                if (savedEmail.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(
                            authState = AuthUiState.Unauthenticated,
                            transactions = emptyList(),
                            userPlans = emptyList(),
                            adminTransactions = emptyList(),
                            adminVerifications = emptyList(),
                            adminUsers = emptyList()
                        )
                    }
                } else {
                    restoreUserSession(savedEmail)
                }
            }
        }
    }

    private suspend fun restoreUserSession(email: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        val result = repository.fetchUserProfile(email)
        result.onSuccess { user ->
            _uiState.update { it.copy(authState = AuthUiState.Authenticated(user), isLoading = false) }
            refreshAllData(user)
        }.onFailure { err ->
            _uiState.update {
                it.copy(
                    authState = AuthUiState.Unauthenticated,
                    isLoading = false,
                    errorMessage = err.message
                )
            }
        }
    }

    private fun checkStartupVersion() {
        viewModelScope.launch {
            repository.checkAppVersion().onSuccess { info ->
                if (info.latestVersionCode > BuildConfig.VERSION_CODE) {
                    _uiState.update { it.copy(availableUpdate = info) }
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        val current = _uiState.value.availableUpdate
        if (current != null && !current.forceUpdate) {
            _uiState.update { it.copy(availableUpdate = null) }
        }
    }

    fun saveAppsScriptUrl(url: String) {
        viewModelScope.launch {
            repository.saveAppsScriptUrl(url)
            _uiState.update { it.copy(message = "Backend endpoint saved.") }
        }
    }

    fun clearBannerMessages() {
        _uiState.update { it.copy(message = null, errorMessage = null) }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.login(email, password)
                .onSuccess { user ->
                    _uiState.update {
                        it.copy(
                            authState = AuthUiState.Authenticated(user),
                            isLoading = false,
                            message = "Welcome back, ${user.name.ifBlank { user.email }}"
                        )
                    }
                    refreshAllData(user)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Login failed."
                        )
                    }
                }
        }
    }

    fun signup(name: String, email: String, phone: String, password: String, referredBy: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.signup(name, email, phone, password, referredBy)
                .onSuccess { user ->
                    _uiState.update {
                        it.copy(
                            authState = AuthUiState.Authenticated(user),
                            isLoading = false,
                            message = "Account created! Welcome to MyFund Global."
                        )
                    }
                    refreshAllData(user)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Signup failed."
                        )
                    }
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }

    fun refreshCurrentUser() {
        val auth = _uiState.value.authState
        if (auth is AuthUiState.Authenticated) {
            viewModelScope.launch {
                refreshAllData(auth.user)
            }
        }
    }

    private suspend fun refreshAllData(user: UserProfile) {
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val profileDef = async { repository.fetchUserProfile(user.email) }
            val txDef = async { repository.fetchUserTransactions(user.email) }
            val plansDef = async { repository.fetchUserPlans(user.email) }

            val latestUser = profileDef.await().getOrDefault(user)
            val txList = txDef.await().getOrDefault(emptyList())
            val plansList = plansDef.await().getOrDefault(emptyList())

            _uiState.update {
                it.copy(
                    authState = AuthUiState.Authenticated(latestUser),
                    transactions = txList,
                    userPlans = plansList,
                    isRefreshing = false
                )
            }

            if (latestUser.computedIsAdmin) {
                refreshAdminQueues(latestUser.email)
            }
        }
    }

    fun refreshAdminQueues(adminEmail: String) {
        viewModelScope.launch {
            val txDef = async { repository.fetchAllTransactionsForAdmin(adminEmail) }
            val verDef = async { repository.fetchPendingVerifications(adminEmail) }
            val usersDef = async { repository.fetchAllUsers(adminEmail) }

            _uiState.update {
                it.copy(
                    adminTransactions = txDef.await().getOrDefault(it.adminTransactions),
                    adminVerifications = verDef.await().getOrDefault(it.adminVerifications),
                    adminUsers = usersDef.await().getOrDefault(it.adminUsers)
                )
            }
        }
    }

    fun submitTransaction(
        type: String,
        amount: Double,
        details: String,
        proofUri: Uri?,
        onSuccess: () -> Unit
    ) {
        val user = (_uiState.value.authState as? AuthUiState.Authenticated)?.user ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            repository.submitTransaction(user.email, type, amount, details, proofUri)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            message = "$type request submitted with Pending status."
                        )
                    }
                    refreshAllData(user)
                    onSuccess()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = err.message ?: "Failed to submit $type."
                        )
                    }
                }
        }
    }

    fun submitKyc(
        pan: String,
        frontUri: Uri?,
        backUri: Uri?,
        selfieUri: Uri?,
        onSuccess: () -> Unit
    ) {
        val user = (_uiState.value.authState as? AuthUiState.Authenticated)?.user ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            repository.submitKyc(user.email, pan, frontUri, backUri, selfieUri)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            message = "KYC documents uploaded. Status updated to Pending."
                        )
                    }
                    refreshAllData(user)
                    onSuccess()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = err.message ?: "Failed to submit KYC."
                        )
                    }
                }
        }
    }

    // ==================== ADMIN ACTIONS ====================

    fun updateTransactionStatus(rowNumber: Int, status: String) {
        val user = (_uiState.value.authState as? AuthUiState.Authenticated)?.user ?: return
        if (!user.computedIsAdmin) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            repository.updateTransactionStatus(user.email, rowNumber, status)
                .onSuccess { msg ->
                    _uiState.update { it.copy(isSubmitting = false, message = msg) }
                    refreshAllData(user)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = err.message ?: "Update failed.")
                    }
                }
        }
    }

    fun updateKycStatus(targetEmail: String, status: String) {
        val user = (_uiState.value.authState as? AuthUiState.Authenticated)?.user ?: return
        if (!user.computedIsAdmin) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val note = if (status == "Verified") {
                "Your KYC verification is approved!"
            } else {
                "Your KYC verification was rejected. Please re-submit clear documents."
            }
            repository.updateKycStatus(user.email, targetEmail, status, note)
                .onSuccess { msg ->
                    _uiState.update { it.copy(isSubmitting = false, message = msg) }
                    refreshAllData(user)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = err.message ?: "KYC update failed.")
                    }
                }
        }
    }

    fun updateUserBalance(
        targetEmail: String,
        invested: Double,
        totalAmount: Double,
        withdrawableLimit: Double,
        interestRate: Double,
        notification: String,
        onSuccess: () -> Unit
    ) {
        val user = (_uiState.value.authState as? AuthUiState.Authenticated)?.user ?: return
        if (!user.computedIsAdmin) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            repository.updateUserBalance(
                adminEmail = user.email,
                targetEmail = targetEmail,
                invested = invested,
                totalAmount = totalAmount,
                withdrawableLimit = withdrawableLimit,
                interestRate = interestRate,
                notification = notification
            ).onSuccess { msg ->
                _uiState.update { it.copy(isSubmitting = false, message = msg) }
                refreshAllData(user)
                onSuccess()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = err.message ?: "Balance update failed.")
                }
            }
        }
    }
}
