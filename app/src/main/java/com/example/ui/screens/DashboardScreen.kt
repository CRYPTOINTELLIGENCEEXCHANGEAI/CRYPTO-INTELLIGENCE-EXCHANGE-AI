package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PlansInfo
import com.example.data.model.UserPlanItem
import com.example.data.model.UserProfile
import com.example.ui.components.EditProfileDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MetricTile
import com.example.ui.components.StatusBadge
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatPercent
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExchangeYellow
import com.example.ui.theme.GoldChampagne
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MatteBlackSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    user: UserProfile,
    userPlans: List<UserPlanItem>,
    plansInfo: PlansInfo,
    isRefreshing: Boolean,
    isSubmitting: Boolean = false,
    onRefresh: () -> Unit,
    onUpdateProfile: (name: String, phone: String, photoUri: Uri?, onDone: () -> Unit) -> Unit = { _, _, _, onDone -> onDone() },
    onNavigateToDeposit: () -> Unit,
    onNavigateToWithdraw: () -> Unit,
    onNavigateToKyc: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToMyPlans: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showEditProfileDialog by remember { mutableStateOf(false) }

    if (showEditProfileDialog) {
        EditProfileDialog(
            user = user,
            isSubmitting = isSubmitting,
            onDismiss = { showEditProfileDialog = false },
            onSave = onUpdateProfile
        )
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Greeting & User ID + Avatar + Edit Profile + KYC Badge
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
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
                                UserAvatar(
                                    photoUrl = user.photoUrl,
                                    name = user.name,
                                    size = 50.dp,
                                    showEditBadge = true,
                                    onClick = { showEditProfileDialog = true },
                                    modifier = Modifier.testTag("dashboard_avatar_edit")
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Hello, ${user.name.ifBlank { "Investor" }}",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "UID: ${user.userId.ifBlank { "Pending" }}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                StatusBadge(status = user.status)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = user.phone.ifBlank { user.email },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    onClick = { showEditProfileDialog = true },
                                    shape = RoundedCornerShape(50),
                                    color = ExchangeYellow.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, ExchangeYellow.copy(alpha = 0.55f)),
                                    modifier = Modifier.testTag("dashboard_edit_profile_button")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Profile",
                                            tint = ExchangeYellow,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "Edit Profile",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ExchangeYellow
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Hero Total Balance Card (Matte Black + Emerald Green + Yellow)
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(MatteBlackSurface, EmeraldDeep, EmeraldPrimary)
                                )
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    listOf(ExchangeYellow.copy(alpha = 0.45f), EmeraldAccent.copy(alpha = 0.55f))
                                ),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(22.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TOTAL BALANCE",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.78f),
                                    letterSpacing = 1.1.sp
                                )
                                Surface(
                                    color = ExchangeYellow.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(1.dp, ExchangeYellow.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "${formatPercent(user.interestRate)} Rate",
                                        color = ExchangeYellow,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = formatCurrency(user.totalAmount),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 34.sp,
                                color = Color.White,
                                modifier = Modifier.testTag("dashboard_total_balance")
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Withdrawable Limit",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.74f)
                                    )
                                    Text(
                                        text = formatCurrency(user.withdrawableLimit),
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp,
                                        color = EmeraldAccent
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Total Invested",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.74f)
                                    )
                                    Text(
                                        text = formatCurrency(user.invested),
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // Balance Metric Grid
                item {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isWide = maxWidth >= 600.dp
                        if (isWide) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                MetricTile(
                                    title = "Total Invested",
                                    value = formatCurrency(user.invested),
                                    subtitle = "Principal Capital",
                                    icon = Icons.Default.Savings,
                                    accentColor = EmeraldPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricTile(
                                    title = "Withdrawable Limit",
                                    value = formatCurrency(user.withdrawableLimit),
                                    subtitle = "Available Payout",
                                    icon = Icons.Default.AccountBalanceWallet,
                                    accentColor = EmeraldAccent,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricTile(
                                    title = "Total Withdrawal",
                                    value = formatCurrency(user.totalWithdrawal),
                                    subtitle = "Lifetime Payouts",
                                    icon = Icons.Default.Payments,
                                    accentColor = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricTile(
                                    title = "Return Rate",
                                    value = formatPercent(user.interestRate),
                                    subtitle = "Active ROI",
                                    icon = Icons.Default.Percent,
                                    accentColor = GoldChampagne,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    MetricTile(
                                        title = "Total Invested",
                                        value = formatCurrency(user.invested),
                                        subtitle = "Principal Capital",
                                        icon = Icons.Default.Savings,
                                        accentColor = EmeraldPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    MetricTile(
                                        title = "Withdrawable Limit",
                                        value = formatCurrency(user.withdrawableLimit),
                                        subtitle = "Available Payout",
                                        icon = Icons.Default.AccountBalanceWallet,
                                        accentColor = EmeraldAccent,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    MetricTile(
                                        title = "Total Withdrawal",
                                        value = formatCurrency(user.totalWithdrawal),
                                        subtitle = "Lifetime Payouts",
                                        icon = Icons.Default.Payments,
                                        accentColor = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    MetricTile(
                                        title = "Return Rate",
                                        value = formatPercent(user.interestRate),
                                        subtitle = "Active ROI",
                                        icon = Icons.Default.Percent,
                                        accentColor = GoldChampagne,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Quick Buttons
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionButton(
                            label = "Deposit",
                            icon = Icons.Default.ArrowDownward,
                            onClick = onNavigateToDeposit,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_action_deposit")
                        )
                        QuickActionButton(
                            label = "Withdraw",
                            icon = Icons.Default.ArrowUpward,
                            onClick = onNavigateToWithdraw,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_action_withdraw")
                        )
                        QuickActionButton(
                            label = "KYC",
                            icon = Icons.Default.VerifiedUser,
                            onClick = onNavigateToKyc,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_action_kyc")
                        )
                        QuickActionButton(
                            label = "History",
                            icon = Icons.Default.History,
                            onClick = onNavigateToHistory,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_action_history")
                        )
                    }
                }

                // Active Plans Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Plans",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        TextButton(onClick = onNavigateToMyPlans) {
                            Text("See all (${userPlans.size})")
                        }
                    }
                }

                if (userPlans.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            title = "No Active Plans",
                            description = "Explore investment plans or deposit funds to start earning monthly returns."
                        )
                    }
                } else {
                    items(userPlans, key = { "${it.id}_${it.name}" }) { plan ->
                        UserPlanSummaryCard(plan = plan, plansInfo = plansInfo)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
fun UserPlanSummaryCard(
    plan: UserPlanItem,
    plansInfo: PlansInfo
) {
    val progress = if (plan.max > 0) (plan.paid.toFloat() / plan.max.toFloat()).coerceIn(0f, 1f) else 0f
    val earned = plan.earnedSoFar(plansInfo.ratePercent, plansInfo.period)

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.name.ifBlank { "Investment Plan" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Plan ID: ${plan.id}",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(status = plan.status)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Amount: ${formatCurrency(plan.amt)}",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Earned: ${formatCurrency(earned)}",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(50)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                text = "Payouts: ${plan.paid} / ${plan.max}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
