package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DepositScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.KycScreen
import com.example.ui.screens.MyPlansScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.OtpVerificationScreen
import com.example.ui.screens.PlansScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.WithdrawScreen
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

object AppRoutes {
    const val HOME = "home"
    const val PLANS = "plans"
    const val MY_PLANS = "my_plans"
    const val HISTORY = "history"
    const val PROFILE = "profile"
    const val DEPOSIT = "deposit"
    const val WITHDRAW = "withdraw"
    const val KYC = "kyc"
    const val NOTIFICATIONS = "notifications"
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
    val coroutineScope = rememberCoroutineScope()

    var prefillDepositPlan by remember { mutableStateOf("") }
    var prefillDepositAmount by remember { mutableDoubleStateOf(0.0) }

    LaunchedEffect(uiState.authState, uiState.message, uiState.errorMessage) {
        if (uiState.authState is AuthUiState.Authenticated) {
            val msg = uiState.message ?: uiState.errorMessage
            if (!msg.isNullOrBlank()) {
                snackbarHostState.showSnackbar(msg)
                viewModel.clearMessages()
            }
        }
    }

    when (val auth = uiState.authState) {
        AuthUiState.Splash -> {
            SplashScreen()
        }

        AuthUiState.Unauthenticated -> {
            val pending = uiState.pendingSignup
            if (pending != null) {
                OtpVerificationScreen(
                    email = pending.email,
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    successMessage = uiState.message,
                    onVerifyOtp = { otp ->
                        viewModel.verifyOtpAndRegister(otp) {
                            // PendingSignup cleared -> transitions back to Login automatically
                        }
                    },
                    onResendOtp = {
                        viewModel.resendSignupOtp {}
                    },
                    onBack = { viewModel.cancelOtpScreen() }
                )
            } else {
                AuthScreen(
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    successMessage = uiState.message,
                    onLogin = { username, pass -> viewModel.login(username, pass) },
                    onStartSignup = { name, surname, email, phone, pass, ref ->
                        viewModel.startSignupAndSendOtp(name, surname, email, phone, pass, ref) {}
                    }
                )
            }
        }

        is AuthUiState.Authenticated -> {
            val user = auth.user
            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route ?: AppRoutes.HOME

            val topLevelDestinations = listOf(
                NavDestination(
                    route = AppRoutes.HOME,
                    label = "Home",
                    selectedIcon = Icons.Filled.Dashboard,
                    unselectedIcon = Icons.Outlined.Dashboard,
                    tag = "nav_home"
                ),
                NavDestination(
                    route = AppRoutes.PLANS,
                    label = "Plans",
                    selectedIcon = Icons.Filled.WorkspacePremium,
                    unselectedIcon = Icons.Outlined.WorkspacePremium,
                    tag = "nav_plans"
                ),
                NavDestination(
                    route = AppRoutes.MY_PLANS,
                    label = "My Plans",
                    selectedIcon = Icons.AutoMirrored.Filled.TrendingUp,
                    unselectedIcon = Icons.AutoMirrored.Filled.TrendingUp,
                    tag = "nav_my_plans"
                ),
                NavDestination(
                    route = AppRoutes.HISTORY,
                    label = "History",
                    selectedIcon = Icons.Filled.History,
                    unselectedIcon = Icons.Outlined.History,
                    tag = "nav_history"
                ),
                NavDestination(
                    route = AppRoutes.PROFILE,
                    label = "Profile",
                    selectedIcon = Icons.Filled.Person,
                    unselectedIcon = Icons.Outlined.Person,
                    tag = "nav_profile"
                )
            )

            val topLevelRoutes = topLevelDestinations.map { it.route }.toSet()
            val isTopLevel = currentRoute in topLevelRoutes

            fun navigateTab(route: String) {
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            fun openSecondary(route: String) {
                navController.navigate(route) {
                    launchSingleTop = true
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavRail = maxWidth >= 700.dp

                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        if (currentRoute != AppRoutes.ADMIN) {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = when (currentRoute) {
                                            AppRoutes.DEPOSIT -> "Deposit"
                                            AppRoutes.WITHDRAW -> "Withdraw"
                                            AppRoutes.KYC -> "KYC Verification"
                                            AppRoutes.NOTIFICATIONS -> "Notifications"
                                            else -> "Crypto Intelligence Exchange AI"
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                navigationIcon = {
                                    if (!isTopLevel) {
                                        IconButton(onClick = { navController.popBackStack() }) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Back"
                                            )
                                        }
                                    }
                                },
                                actions = {
                                    IconButton(
                                        onClick = {
                                            viewModel.refreshNotifications()
                                            openSecondary(AppRoutes.NOTIFICATIONS)
                                        },
                                        modifier = Modifier.testTag("topbar_notifications_button")
                                    ) {
                                        BadgedBox(
                                            badge = {
                                                if (uiState.notifications.isNotEmpty()) {
                                                    Badge { Text(uiState.notifications.size.toString()) }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Notifications,
                                                contentDescription = "Notifications"
                                            )
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background
                                )
                            )
                        }
                    },
                    bottomBar = {
                        if (!useNavRail && isTopLevel) {
                            NavigationBar {
                                topLevelDestinations.forEach { dest ->
                                    val selected = currentRoute == dest.route
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateTab(dest.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = dest.label
                                            )
                                        },
                                        label = { Text(dest.label, maxLines = 1) },
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
                        if (useNavRail && isTopLevel) {
                            NavigationRail {
                                topLevelDestinations.forEach { dest ->
                                    val selected = currentRoute == dest.route
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { navigateTab(dest.route) },
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
                            startDestination = AppRoutes.HOME,
                            modifier = Modifier.weight(1f)
                        ) {
                            composable(AppRoutes.HOME) {
                                DashboardScreen(
                                    user = user,
                                    userPlans = uiState.userPlans,
                                    plansInfo = uiState.plansInfo,
                                    isRefreshing = uiState.isRefreshing,
                                    isSubmitting = uiState.isSubmitting,
                                    onRefresh = { viewModel.refreshHome() },
                                    onUpdateProfile = { name, phone, uri, onDone ->
                                        viewModel.updateProfile(name, phone, uri, onDone)
                                    },
                                    onNavigateToDeposit = {
                                        prefillDepositPlan = ""
                                        prefillDepositAmount = 0.0
                                        openSecondary(AppRoutes.DEPOSIT)
                                    },
                                    onNavigateToWithdraw = { openSecondary(AppRoutes.WITHDRAW) },
                                    onNavigateToKyc = { openSecondary(AppRoutes.KYC) },
                                    onNavigateToHistory = { navigateTab(AppRoutes.HISTORY) },
                                    onNavigateToMyPlans = { navigateTab(AppRoutes.MY_PLANS) }
                                )
                            }

                            composable(AppRoutes.PLANS) {
                                PlansScreen(
                                    user = user,
                                    plansInfo = uiState.plansInfo,
                                    isSubmitting = uiState.isSubmitting,
                                    onActivateFromBalance = { plan ->
                                        viewModel.activatePlan(plan.name, plan.amount) {
                                            navigateTab(AppRoutes.MY_PLANS)
                                        }
                                    },
                                    onDepositAndActivate = { plan ->
                                        prefillDepositPlan = plan.name
                                        prefillDepositAmount = plan.amount
                                        openSecondary(AppRoutes.DEPOSIT)
                                    }
                                )
                            }

                            composable(AppRoutes.MY_PLANS) {
                                MyPlansScreen(
                                    userPlans = uiState.userPlans,
                                    plansInfo = uiState.plansInfo,
                                    history = uiState.history
                                )
                            }

                            composable(AppRoutes.HISTORY) {
                                HistoryScreen(history = uiState.history)
                            }

                            composable(AppRoutes.PROFILE) {
                                ProfileScreen(
                                    user = user,
                                    isSubmitting = uiState.isSubmitting,
                                    onUpdateProfile = { name, phone, uri, onDone ->
                                        viewModel.updateProfile(name, phone, uri, onDone)
                                    },
                                    onChangePassword = { oldPass, newPass, onDone ->
                                        viewModel.changePassword(oldPass, newPass, onDone)
                                    },
                                    onOpenAdminPanel = {
                                        openSecondary(AppRoutes.ADMIN)
                                    },
                                    onLogout = { viewModel.logout() }
                                )
                            }

                            composable(AppRoutes.DEPOSIT) {
                                DepositScreen(
                                    appSettings = uiState.appSettings,
                                    prefillPlanName = prefillDepositPlan,
                                    prefillAmount = prefillDepositAmount,
                                    isSubmitting = uiState.isSubmitting,
                                    onShowMessage = { text ->
                                        coroutineScope.launch { snackbarHostState.showSnackbar(text) }
                                    },
                                    onSubmitDeposit = { amt, txnId, method, planName, uri, onDone ->
                                        viewModel.submitDeposit(amt, txnId, method, planName, uri, onDone)
                                    }
                                )
                            }

                            composable(AppRoutes.WITHDRAW) {
                                WithdrawScreen(
                                    user = user,
                                    appSettings = uiState.appSettings,
                                    isSubmitting = uiState.isSubmitting,
                                    onNavigateToKyc = { openSecondary(AppRoutes.KYC) },
                                    onSubmitWithdrawal = { amt, details, method, onDone ->
                                        viewModel.submitWithdrawal(amt, details, method, onDone)
                                    }
                                )
                            }

                            composable(AppRoutes.KYC) {
                                KycScreen(
                                    user = user,
                                    isSubmitting = uiState.isSubmitting,
                                    onSubmitKyc = { pan, front, back, selfie, onDone ->
                                        viewModel.submitKYC(pan, front, back, selfie, onDone)
                                    }
                                )
                            }

                            composable(AppRoutes.NOTIFICATIONS) {
                                NotificationsScreen(notifications = uiState.notifications)
                            }

                            composable(AppRoutes.ADMIN) {
                                AdminScreen(
                                    isUnlocked = uiState.isAdminUnlocked,
                                    isLoading = uiState.isLoading,
                                    isRefreshing = uiState.isRefreshing,
                                    isSubmitting = uiState.isSubmitting,
                                    dashboard = uiState.adminDashboard,
                                    users = uiState.adminUsers,
                                    usersWithPlans = uiState.adminUsersWithPlans,
                                    stats = uiState.adminStats,
                                    onAdminLogin = { id, pass -> viewModel.adminLogin(id, pass) },
                                    onRefreshAdmin = { viewModel.refreshAdminData() },
                                    onFetchStats = { start, end -> viewModel.fetchAdminStats(start, end) },
                                    onAdminAction = { type, id, act, email, amt ->
                                        viewModel.performAdminAction(type, id, act, email, amt)
                                    },
                                    onPayReturn = { uid, planId ->
                                        viewModel.adminPayReturn(uid, planId)
                                    },
                                    onManualCredit = { uid, amt, type, onDone ->
                                        viewModel.adminCredit(uid, amt, type, onDone)
                                    },
                                    onSendNotify = { uid, title, msg, onDone ->
                                        viewModel.adminNotify(uid, title, msg, onDone)
                                    },
                                    onBack = { navController.popBackStack() },
                                    onLockAdmin = { viewModel.lockAdminSession() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
