package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.AppSettingsData
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.QrCodeDisplay
import com.example.ui.components.formatCurrency
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusPending
import com.example.ui.theme.StatusPendingContainer

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DepositScreen(
    appSettings: AppSettingsData,
    prefillPlanName: String,
    prefillAmount: Double,
    isSubmitting: Boolean,
    onShowMessage: (String) -> Unit,
    onSubmitDeposit: (
        amount: Double,
        txnId: String,
        method: String,
        planName: String,
        screenshotUri: Uri,
        onDone: () -> Unit
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val methods = appSettings.depositMethods
    var selectedMethodIdx by remember(methods) { mutableIntStateOf(0) }
    var amountText by remember(prefillAmount) {
        mutableStateOf(if (prefillAmount > 0.0) prefillAmount.toString() else "")
    }
    var planNameText by remember(prefillPlanName) { mutableStateOf(prefillPlanName) }
    var txnIdText by remember { mutableStateOf("") }
    var screenshotUri by remember { mutableStateOf<Uri?>(null) }
    var submittedPending by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) screenshotUri = uri
    }

    val selectedMethod = methods.getOrNull(selectedMethodIdx)
    val amount = amountText.toDoubleOrNull() ?: 0.0

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Deposit Funds",
                style = MaterialTheme.typography.headlineSmall
            )

            if (submittedPending) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = StatusPendingContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassTop,
                            contentDescription = null,
                            tint = StatusPending
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Deposit Request Pending",
                                style = MaterialTheme.typography.titleMedium,
                                color = StatusPending
                            )
                            Text(
                                text = "Your payment proof is under review. Balance updates automatically once approved.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (methods.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Default.AccountBalanceWallet,
                    title = "Loading Payment Methods",
                    description = "Crypto deposit addresses are syncing from your account settings."
                )
            } else {
                Text(
                    text = "Select Deposit Network / Method",
                    style = MaterialTheme.typography.labelLarge
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    methods.forEachIndexed { idx, method ->
                        FilterChip(
                            selected = selectedMethodIdx == idx,
                            onClick = { selectedMethodIdx = idx },
                            label = { Text(method.title) },
                            modifier = Modifier.testTag("deposit_method_$idx")
                        )
                    }
                }

                if (selectedMethod != null) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = selectedMethod.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            QrCodeDisplay(
                                address = selectedMethod.address,
                                qrUrl = selectedMethod.qrUrl,
                                modifier = Modifier.size(164.dp)
                            )

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = selectedMethod.address.ifBlank { "Address unavailable" },
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Deposit Address", selectedMethod.address))
                                            onShowMessage("Wallet address copied")
                                        },
                                        modifier = Modifier.testTag("copy_deposit_address")
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy address")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (planNameText.isNotBlank()) {
                OutlinedTextField(
                    value = planNameText,
                    onValueChange = { planNameText = it },
                    label = { Text("Plan Name (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Deposit Amount") },
                leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("deposit_amount_input")
            )

            OutlinedTextField(
                value = txnIdText,
                onValueChange = { txnIdText = it },
                label = { Text("Transaction Hash / Reference ID") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("deposit_txnid_input")
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    .testTag("pick_deposit_screenshot"),
                contentAlignment = Alignment.Center
            ) {
                if (screenshotUri != null) {
                    AsyncImage(
                        model = screenshotUri,
                        contentDescription = "Deposit screenshot",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tap to change",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Upload payment screenshot",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Upload Payment Screenshot",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            val canSubmit = selectedMethod != null &&
                amount > 0.0 &&
                txnIdText.isNotBlank() &&
                screenshotUri != null &&
                !isSubmitting

            Button(
                onClick = {
                    onSubmitDeposit(
                        amount,
                        txnIdText,
                        selectedMethod?.title.orEmpty(),
                        planNameText.trim(),
                        screenshotUri!!
                    ) {
                        amountText = ""
                        txnIdText = ""
                        screenshotUri = null
                        submittedPending = true
                    }
                },
                enabled = canSubmit,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_deposit_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text("Submit Deposit")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WithdrawScreen(
    user: UserProfile,
    appSettings: AppSettingsData,
    isSubmitting: Boolean,
    onNavigateToKyc: () -> Unit,
    onSubmitWithdrawal: (amount: Double, details: String, method: String, onDone: () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val methods = appSettings.withdrawMethods
    var selectedMethodIdx by remember(methods) { mutableIntStateOf(0) }
    var amountText by remember { mutableStateOf("") }
    var walletAddress by remember { mutableStateOf("") }

    val selectedMethod = methods.getOrNull(selectedMethodIdx)
    val amount = amountText.toDoubleOrNull() ?: 0.0
    val exceedsLimit = amount > user.withdrawableLimit

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Withdraw Funds",
                style = MaterialTheme.typography.headlineSmall
            )

            if (!user.isKycVerified) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "KYC Required",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Complete KYC to Withdraw",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Current KYC status: ${user.status}. Withdrawals are enabled once your identity verification is approved.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onNavigateToKyc,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("withdraw_complete_kyc_button")
                        ) {
                            Text("Complete KYC")
                        }
                    }
                }
                return@Column
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Withdrawable Limit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = formatCurrency(user.withdrawableLimit),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (methods.isNotEmpty()) {
                Text(
                    text = "Select Payout Method",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    methods.forEachIndexed { idx, method ->
                        FilterChip(
                            selected = selectedMethodIdx == idx,
                            onClick = { selectedMethodIdx = idx },
                            label = { Text(method.title) },
                            modifier = Modifier.testTag("withdraw_method_$idx")
                        )
                    }
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Withdrawal Amount") },
                leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = exceedsLimit,
                supportingText = if (exceedsLimit) {
                    { Text("Cannot exceed ${formatCurrency(user.withdrawableLimit)}", color = StatusError) }
                } else null,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("withdraw_amount_input")
            )

            // 25 / 50 / 75 / 100% Quick Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(25, 50, 75, 100).forEach { pct ->
                    OutlinedButton(
                        onClick = {
                            val computed = user.withdrawableLimit * (pct / 100.0)
                            amountText = String.format(java.util.Locale.US, "%.2f", computed)
                        },
                        enabled = user.withdrawableLimit > 0.0,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("withdraw_pct_$pct")
                    ) {
                        Text("$pct%")
                    }
                }
            }

            OutlinedTextField(
                value = walletAddress,
                onValueChange = { walletAddress = it },
                label = { Text("Destination Wallet Address / Payout Details") },
                supportingText = selectedMethod?.helper?.takeIf { it.isNotBlank() }?.let { helper ->
                    { Text(helper) }
                },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("withdraw_details_input")
            )

            val canSubmit = amount > 0.0 &&
                !exceedsLimit &&
                walletAddress.isNotBlank() &&
                (methods.isEmpty() || selectedMethod != null) &&
                !isSubmitting

            Button(
                onClick = {
                    onSubmitWithdrawal(
                        amount,
                        walletAddress,
                        selectedMethod?.title ?: "Standard"
                    ) {
                        amountText = ""
                        walletAddress = ""
                    }
                },
                enabled = canSubmit,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_withdraw_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text("Submit Withdrawal Request")
                }
            }
        }
    }
}
