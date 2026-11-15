package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.EndpointConfigDialog
import com.example.ui.components.UpdatePromptDialog
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DepositWithdrawScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.KycScreen
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.MainViewModel

object AppRoutes {
    const val DASHBOARD = "dashboard"
    const val TRANSFER_DEPOSIT = "transfer/deposit"
    const val TRANSFER_WITHDRAW = "transfer/withdraw"
    const val KYC = "kyc"
    const val HISTORY = "history"
    const val ADMIN = "admin"
}

private data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyFundApp(
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showEndpointDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message, uiState.errorMessage) {
        val msg = uiState.message ?: uiState.errorMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBannerMessages()
        }
    }

    if (uiState.availableUpdate != null) {
        val info = uiState.availableUpdate!!
        UpdatePromptDialog(
            versionInfo = info,
            onUpdateNow = { url ->
                if (url.startsWith("http")) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            },
            onDismiss = { viewModel.dismissUpdateDialog() }
        )
    }

    if (showEndpointDialog) {
        EndpointConfigDialog(
            currentUrl = uiState.appsScriptUrl,
            onSave = { viewModel.saveAppsScriptUrl(it) },
            onDismiss = { showEndpointDialog = false }
        )
    }

    when (val auth = uiState.authState) {
        AuthUiState.CheckingSession -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        AuthUiState.Unauthenticated -> {
            AuthScreen(
                appsScriptUrl = uiState.appsScriptUrl,
                isLoading = uiState.isLoading,
                errorMessage = uiState.errorMessage,
                onLogin = { email, pass -> viewModel.login(email, pass) },
                onSignup = { name, email, phone, pass, ref ->
                    viewModel.signup(name, email, phone, pass, ref)
                },
                onSaveEndpointUrl = { viewModel.saveAppsScriptUrl(it) }
            )
        }

        is AuthUiState.Authenticated -> {
            val user = auth.user
            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route ?: AppRoutes.DASHBOARD

            val destinations = buildList {
                add(
                    NavDestination(
                        route = AppRoutes.DASHBOARD,
                        label = "Home",
                        selectedIcon = Icons.Filled.Dashboard,
                        unselectedIcon = Icons.Outlined.Dashboard,
                        tag = "nav_dashboard"
                    )
                )
                add(
                    NavDestination(
                        route = AppRoutes.TRANSFER_DEPOSIT,
                        label = "Transact",
                        selectedIcon = Icons.Filled.SwapHoriz,
                        unselectedIcon = Icons.Outlined.SwapHoriz,
                        tag = "nav_transfer"
                    )
                )
                add(
                    NavDestination(
                        route = AppRoutes.KYC,
                        label = "KYC",
                        selectedIcon = Icons.Filled.VerifiedUser,
                        unselectedIcon = Icons.Outlined.VerifiedUser,
                        tag = "nav_kyc"
                    )
                )
                add(
                    NavDestination(
                        route = AppRoutes.HISTORY,
                        label = "History",
                        selectedIcon = Icons.Filled.History,
                        unselectedIcon = Icons.Outlined.History,
                        tag = "nav_history"
                    )
                )
                if (user.computedIsAdmin) {
                    add(
                        NavDestination(
                            route = AppRoutes.ADMIN,
                            label = "Admin",
                            selectedIcon = Icons.Filled.AdminPanelSettings,
                            unselectedIcon = Icons.Outlined.AdminPanelSettings,
                            tag = "nav_admin"
                        )
                    )
                }
            }

            fun navigateSingleTop(route: String) {
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavRail = maxWidth >= 700.dp

                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "Crypto Intelligence Exchange AI",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            ),
                            actions = {
                                IconButton(
                                    onClick = { viewModel.refreshCurrentUser() },
                                    modifier = Modifier.testTag("topbar_refresh_button")
                                ) {
                                    if (uiState.isRefreshing) {
                                        CircularProgressIndicator(strokeWidth = 2.dp)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Refresh Google Sheet data"
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { showEndpointDialog = true },
                                    modifier = Modifier.testTag("topbar_endpoint_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SettingsEthernet,
                                        contentDescription = "Configure Google Apps Script URL"
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.logout() },
                                    modifier = Modifier.testTag("topbar_logout_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = "Sign Out"
                                    )
                                }
                            }
                        )
                    },
                    bottomBar = {
                        if (!useNavRail) {
                            NavigationBar {
                                destinations.forEach { dest ->
                                    val selected = currentRoute == dest.route ||
                                        (dest.route == AppRoutes.TRANSFER_DEPOSIT && currentRoute == AppRoutes.TRANSFER_WITHDRAW)
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateSingleTop(dest.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = dest.label
                                            )
                                        },
                                        label = { Text(dest.label) },
                                        modifier = Modifier.testTag(dest.tag)
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useNavRail) {
                            NavigationRail {
                                destinations.forEach { dest ->
                                    val selected = currentRoute == dest.route ||
                                        (dest.route == AppRoutes.TRANSFER_DEPOSIT && currentRoute == AppRoutes.TRANSFER_WITHDRAW)
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { navigateSingleTop(dest.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = dest.label
                                            )
                                        },
                                        label = { Text(dest.label) },
                                        modifier = Modifier.testTag(dest.tag)
                                    )
                                }
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = AppRoutes.DASHBOARD,
                            modifier = Modifier.weight(1f)
                        ) {
                            composable(AppRoutes.DASHBOARD) {
                                DashboardScreen(
                                    user = user,
                                    plans = uiState.userPlans,
                                    onNavigateToTransfer = { isDeposit ->
                                        navigateSingleTop(
                                            if (isDeposit) AppRoutes.TRANSFER_DEPOSIT
                                            else AppRoutes.TRANSFER_WITHDRAW
                                        )
                                    },
                                    onNavigateToKyc = { navigateSingleTop(AppRoutes.KYC) },
                                    onNavigateToHistory = { navigateSingleTop(AppRoutes.HISTORY) },
                                    onNavigateToAdmin = { navigateSingleTop(AppRoutes.ADMIN) }
                                )
                            }

                            composable(AppRoutes.TRANSFER_DEPOSIT) {
                                DepositWithdrawScreen(
                                    user = user,
                                    initialIsDeposit = true,
                                    isSubmitting = uiState.isSubmitting,
                                    onSubmit = { type, amount, details, proofUri, onDone ->
                                        viewModel.submitTransaction(type, amount, details, proofUri, onDone)
                                    }
                                )
                            }

                            composable(AppRoutes.TRANSFER_WITHDRAW) {
                                DepositWithdrawScreen(
                                    user = user,
                                    initialIsDeposit = false,
                                    isSubmitting = uiState.isSubmitting,
                                    onSubmit = { type, amount, details, proofUri, onDone ->
                                        viewModel.submitTransaction(type, amount, details, proofUri, onDone)
                                    }
                                )
                            }

                            composable(AppRoutes.KYC) {
                                KycScreen(
                                    user = user,
                                    isSubmitting = uiState.isSubmitting,
                                    onSubmitKyc = { pan, front, back, selfie, onDone ->
                                        viewModel.submitKyc(pan, front, back, selfie, onDone)
                                    }
                                )
                            }

                            composable(AppRoutes.HISTORY) {
                                HistoryScreen(transactions = uiState.transactions)
                            }

                            composable(AppRoutes.ADMIN) {
                                AdminScreen(
                                    transactions = uiState.adminTransactions,
                                    verifications = uiState.adminVerifications,
                                    users = uiState.adminUsers,
                                    isSubmitting = uiState.isSubmitting,
                                    onApproveTransaction = { row ->
                                        viewModel.updateTransactionStatus(row, "Success")
                                    },
                                    onRejectTransaction = { row ->
                                        viewModel.updateTransactionStatus(row, "Rejected")
                                    },
                                    onApproveKyc = { targetEmail ->
                                        viewModel.updateKycStatus(targetEmail, "Verified")
                                    },
                                    onRejectKyc = { targetEmail ->
                                        viewModel.updateKycStatus(targetEmail, "Rejected")
                                    },
                                    onUpdateUserBalance = { email, inv, tot, lim, intRate, note, onDone ->
                                        viewModel.updateUserBalance(
                                            targetEmail = email,
                                            invested = inv,
                                            totalAmount = tot,
                                            withdrawableLimit = lim,
                                            interestRate = intRate,
                                            notification = note,
                                            onSuccess = onDone
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
