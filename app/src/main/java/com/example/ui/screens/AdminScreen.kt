package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminDashboardData
import com.example.data.model.AdminPendingKyc
import com.example.data.model.AdminPendingTx
import com.example.data.model.AdminStatsData
import com.example.data.model.AdminUserWithPlans
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MetricTile
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatCurrency
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess

private enum class AdminSection(val label: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Default.Dashboard),
    DEPOSITS("Deposits", Icons.Default.ArrowDownward),
    WITHDRAWALS("Withdraws", Icons.Default.ArrowUpward),
    KYC("KYC", Icons.Default.VerifiedUser),
    REFERRALS("Referrals", Icons.Default.Redeem),
    USERS("Users", Icons.Default.Group),
    CREDIT_RETURNS("Returns", Icons.Default.Payments),
    MANUAL_CREDIT("Credit", Icons.Default.AccountBalanceWallet),
    NOTIFY("Notify", Icons.Default.Campaign)
}

private data class PendingConfirmAction(
    val title: String,
    val message: String,
    val onConfirm: () -> Unit
)

@Composable
fun AdminScreen(
    isUnlocked: Boolean,
    isLoading: Boolean,
    isRefreshing: Boolean,
    isSubmitting: Boolean,
    dashboard: AdminDashboardData,
    users: List<UserProfile>,
    usersWithPlans: List<AdminUserWithPlans>,
    stats: AdminStatsData,
    onAdminLogin: (adminId: String, adminPass: String) -> Unit,
    onRefreshAdmin: () -> Unit,
    onFetchStats: (start: String, end: String) -> Unit,
    onAdminAction: (type: String, id: String, act: String, email: String, amount: Double) -> Unit,
    onPayReturn: (uid: String, planId: String) -> Unit,
    onManualCredit: (uid: String, amount: Double, type: String, onDone: () -> Unit) -> Unit,
    onSendNotify: (uid: String, title: String, message: String, onDone: () -> Unit) -> Unit,
    onBack: () -> Unit = {},
    onLockAdmin: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!isUnlocked) {
        AdminLoginGate(
            isLoading = isLoading,
            onSubmit = onAdminLogin,
            onBack = onBack,
            modifier = modifier
        )
        return
    }

    var selectedSectionIdx by remember { mutableIntStateOf(0) }
    var confirmDialog by remember { mutableStateOf<PendingConfirmAction?>(null) }

    if (confirmDialog != null) {
        val pending = confirmDialog!!
        AlertDialog(
            onDismissRequest = { confirmDialog = null },
            title = { Text(pending.title) },
            text = { Text(pending.message) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDialog = null
                        pending.onConfirm()
                    },
                    enabled = !isSubmitting
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val sections = AdminSection.entries
    val currentSection = sections[selectedSectionIdx]

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            // Primary quick-switch bottom tabs + full tab bar above for all 9 sections
            NavigationBar {
                val quickTabs = listOf(
                    AdminSection.OVERVIEW,
                    AdminSection.DEPOSITS,
                    AdminSection.WITHDRAWALS,
                    AdminSection.KYC,
                    AdminSection.USERS
                )
                quickTabs.forEach { sec ->
                    val idx = sections.indexOf(sec)
                    NavigationBarItem(
                        selected = selectedSectionIdx == idx,
                        onClick = { selectedSectionIdx = idx },
                        icon = { Icon(sec.icon, contentDescription = sec.label) },
                        label = { Text(sec.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 920.dp)
                    .fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("admin_back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Profile")
                        }
                        Text(
                            text = "Admin Console",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onLockAdmin, modifier = Modifier.testTag("admin_lock_button")) {
                            Icon(Icons.Default.LockOpen, contentDescription = "Lock Admin Session")
                        }
                        IconButton(onClick = onRefreshAdmin) {
                            if (isRefreshing) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Admin Data")
                            }
                        }
                    }
                }

                ScrollableTabRow(
                    selectedTabIndex = selectedSectionIdx,
                    edgePadding = 12.dp
                ) {
                    sections.forEachIndexed { index, sec ->
                        val countBadge = when (sec) {
                            AdminSection.DEPOSITS -> " (${dashboard.deps.size})"
                            AdminSection.WITHDRAWALS -> " (${dashboard.withs.size})"
                            AdminSection.KYC -> " (${dashboard.kycs.size})"
                            AdminSection.REFERRALS -> " (${dashboard.refs.size})"
                            AdminSection.USERS -> " (${users.size})"
                            else -> ""
                        }
                        Tab(
                            selected = selectedSectionIdx == index,
                            onClick = { selectedSectionIdx = index },
                            text = { Text("${sec.label}$countBadge") },
                            modifier = Modifier.testTag("admin_tab_${sec.name.lowercase()}")
                        )
                    }
                }

                when (currentSection) {
                    AdminSection.OVERVIEW -> AdminOverviewSection(
                        dashboard = dashboard,
                        stats = stats,
                        isLoading = isLoading,
                        onFetchStats = onFetchStats
                    )
                    AdminSection.DEPOSITS -> AdminTxListSection(
                        items = dashboard.deps,
                        typeName = "Deposit",
                        approveAct = "Success",
                        rejectAct = "Failed",
                        isSubmitting = isSubmitting,
                        onRequestAction = { item, act ->
                            confirmDialog = PendingConfirmAction(
                                title = "$act Deposit?",
                                message = "Mark deposit ${formatCurrency(item.amount)} from ${item.email} as $act?",
                                onConfirm = {
                                    onAdminAction("Deposit", item.id, act, item.email, item.amount)
                                }
                            )
                        }
                    )
                    AdminSection.WITHDRAWALS -> AdminTxListSection(
                        items = dashboard.withs,
                        typeName = "Withdraw",
                        approveAct = "Success",
                        rejectAct = "Failed",
                        isSubmitting = isSubmitting,
                        onRequestAction = { item, act ->
                            confirmDialog = PendingConfirmAction(
                                title = "$act Withdrawal?",
                                message = "Mark withdrawal ${formatCurrency(item.amount)} for ${item.email} as $act?",
                                onConfirm = {
                                    onAdminAction("Withdraw", item.id, act, item.email, item.amount)
                                }
                            )
                        }
                    )
                    AdminSection.KYC -> AdminKycSection(
                        items = dashboard.kycs,
                        isSubmitting = isSubmitting,
                        onRequestAction = { item, act ->
                            confirmDialog = PendingConfirmAction(
                                title = "$act KYC?",
                                message = "Mark KYC for ${item.userName.ifBlank { item.email }} (${item.panNo}) as $act?",
                                onConfirm = {
                                    onAdminAction("KYC", item.id, act, item.email, 0.0)
                                }
                            )
                        }
                    )
                    AdminSection.REFERRALS -> AdminTxListSection(
                        items = dashboard.refs,
                        typeName = "Referral",
                        approveAct = "Success",
                        rejectAct = "Failed",
                        isSubmitting = isSubmitting,
                        onRequestAction = { item, act ->
                            confirmDialog = PendingConfirmAction(
                                title = "$act Referral Claim?",
                                message = "Mark referral ${formatCurrency(item.amount)} for ${item.email} as $act?",
                                onConfirm = {
                                    onAdminAction("Referral", item.id, act, item.email, item.amount)
                                }
                            )
                        }
                    )
                    AdminSection.USERS -> AdminUsersSection(users = users)
                    AdminSection.CREDIT_RETURNS -> AdminCreditReturnsSection(
                        usersWithPlans = usersWithPlans,
                        isSubmitting = isSubmitting,
                        onRequestPayReturn = { uid, planId, planName ->
                            confirmDialog = PendingConfirmAction(
                                title = "Credit Return?",
                                message = "Pay monthly return for plan $planName (ID: $planId) to UID $uid?",
                                onConfirm = { onPayReturn(uid, planId) }
                            )
                        }
                    )
                    AdminSection.MANUAL_CREDIT -> AdminManualCreditSection(
                        isSubmitting = isSubmitting,
                        onSubmit = { uid, amount, type, onDone ->
                            confirmDialog = PendingConfirmAction(
                                title = "Apply Manual Credit?",
                                message = "Credit ${formatCurrency(amount)} ($type) to UID $uid?",
                                onConfirm = { onManualCredit(uid, amount, type, onDone) }
                            )
                        }
                    )
                    AdminSection.NOTIFY -> AdminNotifySection(
                        isSubmitting = isSubmitting,
                        onSubmit = { uid, title, msg, onDone ->
                            val target = if (uid.isBlank()) "ALL users" else "UID $uid"
                            confirmDialog = PendingConfirmAction(
                                title = "Send Notification?",
                                message = "Send \"$title\" to $target?",
                                onConfirm = { onSendNotify(uid, title, msg, onDone) }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminLoginGate(
    isLoading: Boolean,
    onSubmit: (String, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var adminId by remember { mutableStateOf("") }
    var adminPass by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .testTag("admin_gate_back_button")
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Profile")
        }
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = "Admin Authentication",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = "Admin Console Unlock",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Enter your Admin ID and Password. Credentials stay in memory for this session only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = adminId,
                    onValueChange = { adminId = it },
                    label = { Text("Admin ID") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_gate_id")
                )
                OutlinedTextField(
                    value = adminPass,
                    onValueChange = { adminPass = it },
                    label = { Text("Admin Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_gate_pass")
                )
                Button(
                    onClick = { onSubmit(adminId, adminPass) },
                    enabled = adminId.isNotBlank() && adminPass.isNotBlank() && !isLoading,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("admin_gate_submit")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else {
                        Text("Unlock Admin Panel")
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminOverviewSection(
    dashboard: AdminDashboardData,
    stats: AdminStatsData,
    isLoading: Boolean,
    onFetchStats: (start: String, end: String) -> Unit
) {
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile(
                    title = "Total Users",
                    value = dashboard.totalUsers.toString(),
                    subtitle = "Registered accounts",
                    icon = Icons.Default.Group,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "Pending Deposits",
                    value = dashboard.deps.size.toString(),
                    subtitle = "Awaiting approval",
                    icon = Icons.Default.ArrowDownward,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile(
                    title = "Pending Withdraws",
                    value = dashboard.withs.size.toString(),
                    subtitle = "Payout queue",
                    icon = Icons.Default.ArrowUpward,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "Pending KYC",
                    value = dashboard.kycs.size.toString(),
                    subtitle = "${dashboard.refs.size} referrals pending",
                    icon = Icons.Default.VerifiedUser,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Date Range Statistics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = startDate,
                            onValueChange = { startDate = it },
                            label = { Text("Start (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endDate,
                            onValueChange = { endDate = it },
                            label = { Text("End (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = { onFetchStats(startDate, endDate) },
                        enabled = !isLoading
                    ) {
                        Text("Load Stats")
                    }

                    stats.metrics.forEach { (key, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = key, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = value,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminTxListSection(
    items: List<AdminPendingTx>,
    typeName: String,
    approveAct: String,
    rejectAct: String,
    isSubmitting: Boolean,
    onRequestAction: (AdminPendingTx, String) -> Unit
) {
    val context = LocalContext.current
    if (items.isEmpty()) {
        Box(modifier = Modifier.padding(16.dp)) {
            EmptyStateCard(
                icon = Icons.Default.Check,
                title = "No Pending ${typeName}s",
                description = "All $typeName requests have been processed."
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { "${it.id}_${it.email}_${it.amount}" }) { item ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${item.type.ifBlank { typeName }} • ${formatCurrency(item.amount)}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${item.email} (ID: ${item.id})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusBadge(status = item.status.ifBlank { "Pending" })
                    }
                    if (item.details.isNotBlank()) {
                        Text(text = item.details, style = MaterialTheme.typography.bodySmall)
                    }
                    if (item.proof.startsWith("http")) {
                        TextButton(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.proof)))
                            }
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Proof Link")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onRequestAction(item, approveAct) },
                            enabled = !isSubmitting,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Approve")
                        }
                        OutlinedButton(
                            onClick = { onRequestAction(item, rejectAct) },
                            enabled = !isSubmitting,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reject")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminKycSection(
    items: List<AdminPendingKyc>,
    isSubmitting: Boolean,
    onRequestAction: (AdminPendingKyc, String) -> Unit
) {
    val context = LocalContext.current
    if (items.isEmpty()) {
        Box(modifier = Modifier.padding(16.dp)) {
            EmptyStateCard(
                icon = Icons.Default.VerifiedUser,
                title = "No Pending KYCs",
                description = "All identity verifications have been reviewed."
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { "${it.id}_${it.email}_${it.panNo}" }) { item ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${item.userName.ifBlank { item.email }} (${item.userUid})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Email: ${item.email} • Phone: ${item.userPhone} • PAN: ${item.panNo}",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("ID Front" to item.adFront, "ID Back" to item.adBack, "Selfie" to item.panImg).forEach { (label, url) ->
                            if (url.startsWith("http")) {
                                OutlinedButton(
                                    onClick = {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(label, maxLines = 1)
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onRequestAction(item, "Verified") },
                            enabled = !isSubmitting,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Approve")
                        }
                        OutlinedButton(
                            onClick = { onRequestAction(item, "Rejected") },
                            enabled = !isSubmitting,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reject")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminUsersSection(users: List<UserProfile>) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(users, query) {
        if (query.isBlank()) users
        else users.filter {
            it.name.contains(query, true) || it.email.contains(query, true) || it.userId.contains(query, true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search users") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtered, key = { "${it.userId}_${it.email}" }) { u ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${u.name.ifBlank { u.email }} (${u.userId})",
                                style = MaterialTheme.typography.titleMedium
                            )
                            StatusBadge(status = u.status)
                        }
                        Text(
                            text = "${u.email} • ${u.phone}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Bal: ${formatCurrency(u.totalAmount)} | Inv: ${formatCurrency(u.invested)} | Lim: ${formatCurrency(u.withdrawableLimit)}",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminCreditReturnsSection(
    usersWithPlans: List<AdminUserWithPlans>,
    isSubmitting: Boolean,
    onRequestPayReturn: (uid: String, planId: String, planName: String) -> Unit
) {
    if (usersWithPlans.isEmpty()) {
        Box(modifier = Modifier.padding(16.dp)) {
            EmptyStateCard(
                icon = Icons.Default.Payments,
                title = "No Users With Active Plans",
                description = "Users with active plans available for return payouts will appear here."
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(usersWithPlans, key = { "${it.uid}_${it.email}" }) { userItem ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${userItem.name} (${userItem.uid})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = userItem.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    userItem.plans.forEach { plan ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${plan.name} • ${formatCurrency(plan.amt)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "ID: ${plan.id} • Paid: ${plan.paid}/${plan.max} (${plan.status})",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { onRequestPayReturn(userItem.uid, plan.id, plan.name) },
                                enabled = !isSubmitting
                            ) {
                                Text("Credit return")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminManualCreditSection(
    isSubmitting: Boolean,
    onSubmit: (uid: String, amount: Double, type: String, onDone: () -> Unit) -> Unit
) {
    var uid by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    val types = listOf("Deposit", "Bonus", "Return", "Limit")
    var selectedType by remember { mutableStateOf(types.first()) }
    val amount = amountText.toDoubleOrNull() ?: 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(text = "Manual Credit", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = uid,
            onValueChange = { uid = it },
            label = { Text("Target User ID (UID)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("Credit Amount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            types.forEach { t ->
                FilterChip(
                    selected = selectedType == t,
                    onClick = { selectedType = t },
                    label = { Text(t) }
                )
            }
        }
        Button(
            onClick = {
                onSubmit(uid, amount, selectedType) {
                    uid = ""
                    amountText = ""
                }
            },
            enabled = uid.isNotBlank() && amount > 0.0 && !isSubmitting,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Apply Credit")
        }
    }
}

@Composable
private fun AdminNotifySection(
    isSubmitting: Boolean,
    onSubmit: (uid: String, title: String, message: String, onDone: () -> Unit) -> Unit
) {
    var uid by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(text = "Send Notification", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = uid,
            onValueChange = { uid = it },
            label = { Text("Target UID (leave empty for all users)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Notification Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Message") },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                onSubmit(uid, title, message) {
                    uid = ""
                    title = ""
                    message = ""
                }
            },
            enabled = title.isNotBlank() && message.isNotBlank() && !isSubmitting,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Send Notification")
        }
    }
}
