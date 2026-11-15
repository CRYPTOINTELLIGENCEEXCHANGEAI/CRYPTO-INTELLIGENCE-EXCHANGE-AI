package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.TransactionRecord
import com.example.data.model.UserProfile
import com.example.data.model.VerificationRecord
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ProofImageDialog
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatPercent
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    transactions: List<TransactionRecord>,
    verifications: List<VerificationRecord>,
    users: List<UserProfile>,
    isSubmitting: Boolean,
    onApproveTransaction: (Int) -> Unit,
    onRejectTransaction: (Int) -> Unit,
    onApproveKyc: (String) -> Unit,
    onRejectKyc: (String) -> Unit,
    onUpdateUserBalance: (
        email: String,
        invested: Double,
        totalAmount: Double,
        withdrawableLimit: Double,
        interestRate: Double,
        notification: String,
        onDone: () -> Unit
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var previewImagePair by remember { mutableStateOf<Pair<String, String>?>(null) }
    var editingUser by remember { mutableStateOf<UserProfile?>(null) }

    if (previewImagePair != null) {
        ProofImageDialog(
            imageUrl = previewImagePair!!.second,
            title = previewImagePair!!.first,
            onDismiss = { previewImagePair = null }
        )
    }

    if (editingUser != null) {
        EditUserBalanceDialog(
            user = editingUser!!,
            isSubmitting = isSubmitting,
            onSave = { invested, total, limit, interest, notification ->
                onUpdateUserBalance(
                    editingUser!!.email,
                    invested,
                    total,
                    limit,
                    interest,
                    notification
                ) {
                    editingUser = null
                }
            },
            onDismiss = { editingUser = null }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 900.dp)
                .fillMaxSize()
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        val pendingCount = transactions.count { it.status.equals("Pending", true) }
                        Text("Tx ($pendingCount)")
                    },
                    modifier = Modifier.testTag("admin_tab_transactions")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        val pendingKyc = verifications.count { it.userStatus.equals("Pending", true) }
                        Text("KYC ($pendingKyc)")
                    },
                    modifier = Modifier.testTag("admin_tab_kyc")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Balances (${users.size})") },
                    modifier = Modifier.testTag("admin_tab_balances")
                )
            }

            when (selectedTab) {
                0 -> AdminTransactionsTab(
                    transactions = transactions,
                    isSubmitting = isSubmitting,
                    onApprove = onApproveTransaction,
                    onReject = onRejectTransaction,
                    onPreviewProof = { url -> previewImagePair = "Transaction Proof" to url }
                )
                1 -> AdminKycTab(
                    verifications = verifications,
                    isSubmitting = isSubmitting,
                    onApprove = onApproveKyc,
                    onReject = onRejectKyc,
                    onPreviewDoc = { title, url -> previewImagePair = title to url }
                )
                2 -> AdminBalancesTab(
                    users = users,
                    onEditUser = { user -> editingUser = user }
                )
            }
        }
    }
}

@Composable
private fun AdminTransactionsTab(
    transactions: List<TransactionRecord>,
    isSubmitting: Boolean,
    onApprove: (Int) -> Unit,
    onReject: (Int) -> Unit,
    onPreviewProof: (String) -> Unit
) {
    var pendingOnly by remember { mutableStateOf(true) }
    val displayList = remember(transactions, pendingOnly) {
        if (pendingOnly) transactions.filter { it.status.equals("Pending", true) }
        else transactions
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Show Pending Only",
                style = MaterialTheme.typography.titleSmall
            )
            Switch(
                checked = pendingOnly,
                onCheckedChange = { pendingOnly = it },
                modifier = Modifier.testTag("admin_tx_pending_switch")
            )
        }

        if (displayList.isEmpty()) {
            Box(modifier = Modifier.padding(16.dp)) {
                EmptyStateCard(
                    icon = Icons.Default.ReceiptLong,
                    title = "No Pending Transactions",
                    description = "All deposit and withdrawal requests have been reviewed."
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayList, key = { "${it.rowNumber}_${it.date}" }) { tx ->
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
                                        text = "${tx.type} • ${formatCurrency(tx.amount)}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "${tx.email} • ${tx.date}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusBadge(status = tx.status)
                            }
                            if (tx.details.isNotBlank()) {
                                Text(
                                    text = tx.details,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (tx.proof.startsWith("http")) {
                                OutlinedButton(
                                    onClick = { onPreviewProof(tx.proof) },
                                    modifier = Modifier.testTag("admin_tx_proof_${tx.rowNumber}")
                                ) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Inspect Proof")
                                }
                            }
                            if (tx.status.equals("Pending", true)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { onApprove(tx.rowNumber) },
                                        enabled = !isSubmitting,
                                        colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("admin_approve_tx_${tx.rowNumber}")
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Approve")
                                    }
                                    OutlinedButton(
                                        onClick = { onReject(tx.rowNumber) },
                                        enabled = !isSubmitting,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("admin_reject_tx_${tx.rowNumber}")
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
        }
    }
}

@Composable
private fun AdminKycTab(
    verifications: List<VerificationRecord>,
    isSubmitting: Boolean,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onPreviewDoc: (String, String) -> Unit
) {
    if (verifications.isEmpty()) {
        Box(modifier = Modifier.padding(16.dp)) {
            EmptyStateCard(
                icon = Icons.Default.VerifiedUser,
                title = "No KYC Submissions",
                description = "Submitted identity documents will appear here for review."
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(verifications, key = { "${it.rowNumber}_${it.email}_${it.date}" }) { ver ->
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ver.userName.ifBlank { ver.email },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${ver.email} • PAN: ${ver.pan}",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusBadge(status = ver.userStatus)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Front" to ver.front, "Back" to ver.back, "Selfie" to ver.selfie).forEach { (label, url) ->
                            if (url.startsWith("http")) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onPreviewDoc("${ver.email} - $label", url) },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = label,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(72.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onApprove(ver.email) },
                            enabled = !isSubmitting,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_verify_kyc_${ver.rowNumber}")
                        ) {
                            Text("Verify")
                        }
                        OutlinedButton(
                            onClick = { onReject(ver.email) },
                            enabled = !isSubmitting,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_reject_kyc_${ver.rowNumber}")
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
private fun AdminBalancesTab(
    users: List<UserProfile>,
    onEditUser: (UserProfile) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredUsers = remember(users, searchQuery) {
        if (searchQuery.isBlank()) users
        else users.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.email.contains(searchQuery, ignoreCase = true) ||
                it.userId.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search users by name, email, or ID") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("admin_user_search_input")
        )

        if (filteredUsers.isEmpty()) {
            Box(modifier = Modifier.padding(16.dp)) {
                EmptyStateCard(
                    icon = Icons.Default.AccountBalanceWallet,
                    title = "No Users Found",
                    description = "Try clearing your search filter or refreshing."
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredUsers, key = { it.email }) { user ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onEditUser(user) }
                            .testTag("admin_user_card_${user.userId}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user.name.ifBlank { user.email },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${user.email} • ${user.userId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Bal: ${formatCurrency(user.totalAmount)} | Inv: ${formatCurrency(user.invested)} | Lim: ${formatCurrency(user.withdrawableLimit)} | ${formatPercent(user.interestRate)}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit user balance",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditUserBalanceDialog(
    user: UserProfile,
    isSubmitting: Boolean,
    onSave: (invested: Double, totalAmount: Double, withdrawableLimit: Double, interestRate: Double, notification: String) -> Unit,
    onDismiss: () -> Unit
) {
    var invested by remember(user) { mutableStateOf(user.invested.toString()) }
    var totalAmount by remember(user) { mutableStateOf(user.totalAmount.toString()) }
    var withdrawableLimit by remember(user) { mutableStateOf(user.withdrawableLimit.toString()) }
    var interestRate by remember(user) { mutableStateOf(user.interestRate.toString()) }
    var notification by remember(user) { mutableStateOf(user.notification) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${user.name.ifBlank { user.email }}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = invested,
                    onValueChange = { invested = it },
                    label = { Text("Invested Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.testTag("admin_edit_invested")
                )
                OutlinedTextField(
                    value = totalAmount,
                    onValueChange = { totalAmount = it },
                    label = { Text("Total Balance (Total Amount)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.testTag("admin_edit_total")
                )
                OutlinedTextField(
                    value = withdrawableLimit,
                    onValueChange = { withdrawableLimit = it },
                    label = { Text("Withdrawable Limit") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.testTag("admin_edit_limit")
                )
                OutlinedTextField(
                    value = interestRate,
                    onValueChange = { interestRate = it },
                    label = { Text("Interest (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.testTag("admin_edit_interest")
                )
                OutlinedTextField(
                    value = notification,
                    onValueChange = { notification = it },
                    label = { Text("Dashboard Notification") },
                    maxLines = 2,
                    modifier = Modifier.testTag("admin_edit_notification")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        invested.toDoubleOrNull() ?: user.invested,
                        totalAmount.toDoubleOrNull() ?: user.totalAmount,
                        withdrawableLimit.toDoubleOrNull() ?: user.withdrawableLimit,
                        interestRate.toDoubleOrNull() ?: user.interestRate,
                        notification.trim()
                    )
                },
                enabled = !isSubmitting,
                modifier = Modifier.testTag("admin_save_balance_button")
            ) {
                Text("Save to Sheet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
