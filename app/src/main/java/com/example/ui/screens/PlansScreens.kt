package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.HistoryItem
import com.example.data.model.PlanOption
import com.example.data.model.PlansInfo
import com.example.data.model.UserPlanItem
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatCurrency
import com.example.ui.theme.GoldChampagne
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusSuccess

@Composable
fun PlansScreen(
    user: UserProfile,
    plansInfo: PlansInfo,
    isSubmitting: Boolean,
    onActivateFromBalance: (plan: PlanOption) -> Unit,
    onDepositAndActivate: (plan: PlanOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmingPlan by remember { mutableStateOf<PlanOption?>(null) }

    if (confirmingPlan != null) {
        val plan = confirmingPlan!!
        AlertDialog(
            onDismissRequest = { confirmingPlan = null },
            title = { Text("Activate ${plan.name}") },
            text = {
                Text(
                    "Activate ${plan.name} for ${formatCurrency(plan.amount)} using your wallet balance (${formatCurrency(user.totalAmount)})?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmingPlan = null
                        onActivateFromBalance(plan)
                    },
                    enabled = !isSubmitting
                ) {
                    Text("Activate Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingPlan = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Investment Plans",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Rate: ${plansInfo.ratePercent}% ${plansInfo.period} • Duration: ${plansInfo.months} months",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (plansInfo.plans.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.WorkspacePremium,
                        title = "Loading Plans",
                        description = "Available investment tiers will appear here once synced."
                    )
                }
            } else {
                items(plansInfo.plans, key = { "${it.name}_${it.amount}" }) { plan ->
                    val hasEnoughBalance = user.totalAmount >= plan.amount && plan.amount > 0.0
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = plan.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    color = GoldChampagne.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = "${plansInfo.ratePercent}% ${plansInfo.period}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = formatCurrency(plan.amount),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Monthly Return",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatCurrency(plan.monthlyReturn),
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        color = StatusSuccess
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Duration",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${plansInfo.months} mo",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Total Return",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatCurrency(plan.totalReturn),
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { confirmingPlan = plan },
                                    enabled = hasEnoughBalance && !isSubmitting,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("activate_balance_${plan.name}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalanceWallet,
                                        contentDescription = null,
                                        modifier = Modifier.height(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Activate from balance", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = { onDepositAndActivate(plan) },
                                    enabled = !isSubmitting,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("deposit_activate_${plan.name}")
                                ) {
                                    Text("Deposit & activate", maxLines = 1)
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
fun MyPlansScreen(
    userPlans: List<UserPlanItem>,
    plansInfo: PlansInfo,
    history: List<HistoryItem>,
    modifier: Modifier = Modifier
) {
    val returnHistory = remember(history) {
        history.filter { it.type.trim().startsWith("Monthly Return", ignoreCase = true) }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "My Active Plans",
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            if (userPlans.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        title = "No Plans Active",
                        description = "Activate a plan from the Plans tab or deposit funds to get started."
                    )
                }
            } else {
                items(userPlans, key = { "${it.id}_${it.name}" }) { plan ->
                    UserPlanSummaryCard(plan = plan, plansInfo = plansInfo)
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Returns History",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (returnHistory.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Payments,
                        title = "No Returns Credited Yet",
                        description = "Monthly return payouts will appear here once credited."
                    )
                }
            } else {
                items(returnHistory, key = { "${it.date}_${it.amount}_${it.details}" }) { item ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                                    text = item.type,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "${item.date} • ${item.details}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "+${formatCurrency(item.amount)}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusSuccess
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                StatusBadge(status = item.status)
                            }
                        }
                    }
                }
            }
        }
    }
}
