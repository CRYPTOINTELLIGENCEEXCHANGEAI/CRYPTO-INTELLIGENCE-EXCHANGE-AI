package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.HistoryItem
import com.example.data.model.NotificationItem
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ProofImageDialog
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatCurrency
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    history: List<HistoryItem>,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("All", "Deposit", "Withdraw", "Returns")
    var selectedTab by remember { mutableIntStateOf(0) }
    var previewProofUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val filtered = remember(history, selectedTab) {
        when (selectedTab) {
            1 -> history.filter { it.type.contains("Deposit", ignoreCase = true) }
            2 -> history.filter { it.type.contains("Withdraw", ignoreCase = true) }
            3 -> history.filter {
                it.type.startsWith("Monthly Return", ignoreCase = true) ||
                    it.type.contains("Return", ignoreCase = true)
            }
            else -> history
        }
    }

    if (previewProofUrl != null) {
        ProofImageDialog(
            imageUrl = previewProofUrl.orEmpty(),
            title = "Payment Proof",
            onDismiss = { previewProofUrl = null }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                        modifier = Modifier.testTag("history_tab_${title.lowercase()}")
                    )
                }
            }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.padding(16.dp)) {
                    EmptyStateCard(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        title = "No Records Found",
                        description = "Transactions under '${tabs[selectedTab]}' will appear here."
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { "${it.date}_${it.type}_${it.amount}_${it.details}" }) { item ->
                        val isPositive = item.type.contains("Deposit", ignoreCase = true) ||
                            item.type.contains("Return", ignoreCase = true) ||
                            item.type.contains("Credit", ignoreCase = true)
                        val icon = when {
                            item.type.contains("Withdraw", ignoreCase = true) -> Icons.Default.ArrowUpward
                            item.type.contains("Return", ignoreCase = true) -> Icons.Default.Savings
                            else -> Icons.Default.ArrowDownward
                        }

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
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isPositive) StatusSuccess.copy(alpha = 0.12f)
                                                else StatusError.copy(alpha = 0.12f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = item.type,
                                            tint = if (isPositive) StatusSuccess else StatusError,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.type.ifBlank { "Transaction" },
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = item.date,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${if (isPositive) "+" else "-"}${formatCurrency(item.amount)}",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isPositive) StatusSuccess else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        StatusBadge(status = item.status)
                                    }
                                }

                                if (item.details.isNotBlank()) {
                                    Text(
                                        text = item.details,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (item.proof.startsWith("http")) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(
                                            onClick = { previewProofUrl = item.proof },
                                            modifier = Modifier.testTag("history_view_proof")
                                        ) {
                                            Text("View proof")
                                        }
                                        TextButton(
                                            onClick = {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(item.proof))
                                                )
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.OpenInNew,
                                                contentDescription = "Open proof in browser",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Open link")
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
}

@Composable
fun NotificationsScreen(
    notifications: List<NotificationItem>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .padding(16.dp)
            ) {
                EmptyStateCard(
                    icon = Icons.Default.NotificationsNone,
                    title = "No Notifications",
                    description = "Updates on your deposits, withdrawals, KYC, and returns will appear here."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Notifications",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                items(notifications, key = { "${it.date}_${it.title}_${it.message}" }) { item ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                if (item.date.isNotBlank()) {
                                    Text(
                                        text = item.date,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (item.message.isNotBlank()) {
                                Text(
                                    text = item.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
