package com.radafiq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radafiq.data.models.AccountKind
import com.radafiq.data.models.CardSummary
import com.radafiq.data.models.CustomerSummary

private enum class AnalyticsMetric(
    val label: String
) {
    USAGE("Usage Breakdown"),
    PAID("Paid Amount"),
    OUTSTANDING("Outstanding Balance")
}

@Composable
fun AnalyticsScreen(
    cards: List<CardSummary>,
    customers: List<CustomerSummary>,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {}
) {
    val totalUsed = cards.sumOf { it.bill }
    // BUG-39: Use customer-paid amount (bill - payable) not owner's personal payments (pending)
    val totalPaid = cards.sumOf { (it.bill - it.payable).coerceAtLeast(0.0) }
    val totalBalance = cards.sumOf { it.payable }

    val availableKinds = remember(cards) { cards.map { it.accountKind }.distinct() }
    var selectedAccountKindName by rememberSaveable { mutableStateOf("") }
    var selectedAccountMetricName by rememberSaveable { mutableStateOf(AnalyticsMetric.USAGE.name) }
    val selectedAccountKind = availableKinds.firstOrNull { it.name == selectedAccountKindName }
        ?: when {
            availableKinds.contains(AccountKind.CREDIT_CARD) -> AccountKind.CREDIT_CARD
            availableKinds.contains(AccountKind.BANK_ACCOUNT) -> AccountKind.BANK_ACCOUNT
            else -> AccountKind.CREDIT_CARD
        }
    val selectedAccountMetric = analyticsMetricFrom(selectedAccountMetricName)
    val filteredCards = remember(cards, selectedAccountKind) {
        cards.filter { it.accountKind == selectedAccountKind }
    }

    val sortedCustomers = remember(customers.sumOf { it.snapshotVersion }) { customers.sortedBy { it.name.lowercase() } }
    var selectedCustomerId by rememberSaveable { mutableStateOf("") }
    var selectedCustomerMetricName by rememberSaveable { mutableStateOf(AnalyticsMetric.USAGE.name) }
    val selectedCustomer = sortedCustomers.firstOrNull { it.id == selectedCustomerId }
        ?: sortedCustomers.firstOrNull()
    val selectedCustomerMetric = analyticsMetricFrom(selectedCustomerMetricName)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PageHeader(
                title = "Analytics",
                subtitle = "Inspect accounts and customers with quick metric filters.",
                trailing = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }

        if (cards.isEmpty() && customers.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.height(420.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        title = "No analytics yet",
                        subtitle = "Add customer transactions to unlock insights."
                    )
                }
            }
        } else {
            if (cards.isNotEmpty()) {
                item {
                    HeroPanel(
                        title = "Total balance",
                        amountValue = totalBalance,
                        subtitle = "Used ${formatMoney(totalUsed)} minus paid ${formatMoney(totalPaid)}"
                    )
                }

                item {
                    SummaryCard(cards)
                }
            }

            item {
                AccountAnalyticsCard(
                    cards = filteredCards,
                    availableKinds = availableKinds,
                    selectedKind = selectedAccountKind,
                    onKindSelected = { selectedAccountKindName = it.name },
                    selectedMetric = selectedAccountMetric,
                    onMetricSelected = { selectedAccountMetricName = it.name }
                )
            }

            item {
                CustomerAnalyticsCard(
                    customers = sortedCustomers,
                    selectedCustomer = selectedCustomer,
                    onCustomerSelected = { selectedCustomerId = it.id },
                    selectedMetric = selectedCustomerMetric,
                    onMetricSelected = { selectedCustomerMetricName = it.name }
                )
            }
        }
    }
}

@Composable
fun SummaryCard(cards: List<CardSummary>) {
    val totalUsed = cards.sumOf { it.bill }
    // BUG-30: Use customer-paid amount (bill - payable) not owner's personal payments (pending)
    val totalPaid = cards.sumOf { (it.bill - it.payable).coerceAtLeast(0.0) }
    val totalBalance = cards.sumOf { it.payable }

    FlowCard(accentColor = MaterialTheme.colorScheme.primary) {
        Text(
            "Overall summary",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        ResponsiveTwoPane(
            first = { itemModifier ->
                MetricPill(
                    label = "Used",
                    amountValue = totalUsed,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = itemModifier
                )
            },
            second = { itemModifier ->
                MetricPill(
                    label = "Paid",
                    amountValue = totalPaid,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = itemModifier
                )
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        AccentValueRow(
            label = "Balance",
            amountValue = totalBalance,
            color = if (totalBalance > 0.0) warningColor() else MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun AccountAnalyticsCard(
    cards: List<CardSummary>,
    availableKinds: List<AccountKind>,
    selectedKind: AccountKind,
    onKindSelected: (AccountKind) -> Unit,
    selectedMetric: AnalyticsMetric,
    onMetricSelected: (AnalyticsMetric) -> Unit
) {
    FlowCard(accentColor = MaterialTheme.colorScheme.secondary) {
        Text(
            text = "Account analytics",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (availableKinds.isEmpty()) {
            Text(
                text = "No enabled accounts are available for analytics right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            AccountKindDropdown(
                options = availableKinds,
                selectedKind = selectedKind,
                onKindSelected = onKindSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            AnalyticsMetricDropdown(
                selectedMetric = selectedMetric,
                onMetricSelected = onMetricSelected,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "${cards.size} ${selectedKind.label.lowercase()} account(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (cards.isEmpty()) {
                Text(
                    text = "No ${selectedKind.label.lowercase()} entries have activity yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(10.dp))

                cards.forEachIndexed { index, card ->
                    AnalyticsEntryCard(
                        title = card.name,
                        subtitle = card.accountKind.label,
                        metricLabel = selectedMetric.label,
                        amountValue = cardMetricValue(card, selectedMetric),
                        color = cardMetricColor(card, selectedMetric)
                    )

                    if (index != cards.lastIndex) {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerAnalyticsCard(
    customers: List<CustomerSummary>,
    selectedCustomer: CustomerSummary?,
    onCustomerSelected: (CustomerSummary) -> Unit,
    selectedMetric: AnalyticsMetric,
    onMetricSelected: (AnalyticsMetric) -> Unit
) {
    FlowCard(accentColor = MaterialTheme.colorScheme.primary) {
        Text(
            text = "Customer analytics",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (customers.isEmpty() || selectedCustomer == null) {
            Text(
                text = "No customer records are available for analytics yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            CustomerDropdown(
                customers = customers,
                selectedCustomer = selectedCustomer,
                onCustomerSelected = onCustomerSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            AnalyticsMetricDropdown(
                selectedMetric = selectedMetric,
                onMetricSelected = onMetricSelected,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = selectedCustomer.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${selectedCustomer.transactions.countLogicalTransactions()} transaction(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            ResponsiveTwoPane(
                first = { itemModifier ->
                    MetricPill(
                        label = "Used",
                        amountValue = selectedCustomer.totalAmount,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = itemModifier
                    )
                },
                second = { itemModifier ->
                    MetricPill(
                        label = "Paid",
                        amountValue = selectedCustomer.creditDueAmount,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = itemModifier
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            AccentValueRow(
                label = selectedMetric.label,
                amountValue = customerMetricValue(selectedCustomer, selectedMetric),
                color = customerMetricColor(selectedCustomer, selectedMetric)
            )

            Text(
                text = "Outstanding balance: ${formatMoney(selectedCustomer.balance)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun AnalyticsEntryCard(
    title: String,
    subtitle: String,
    metricLabel: String,
    amountValue: Double,
    color: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(14.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(12.dp))
        AccentValueRow(
            label = metricLabel,
            amountValue = amountValue,
            color = color
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalyticsMetricDropdown(
    selectedMetric: AnalyticsMetric,
    onMetricSelected: (AnalyticsMetric) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedMetric.label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Metric") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            AnalyticsMetric.values().forEach { metric ->
                DropdownMenuItem(
                    text = { Text(metric.label) },
                    onClick = {
                        expanded = false
                        onMetricSelected(metric)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerDropdown(
    customers: List<CustomerSummary>,
    selectedCustomer: CustomerSummary,
    onCustomerSelected: (CustomerSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedCustomer.name,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Customer") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            customers.forEach { customer ->
                DropdownMenuItem(
                    text = { Text(customer.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        expanded = false
                        onCustomerSelected(customer)
                    }
                )
            }
        }
    }
}

private fun analyticsMetricFrom(name: String): AnalyticsMetric {
    return AnalyticsMetric.values().firstOrNull { it.name == name } ?: AnalyticsMetric.USAGE
}

private fun cardMetricValue(
    card: CardSummary,
    metric: AnalyticsMetric
): Double {
    return when (metric) {
        AnalyticsMetric.USAGE -> card.bill
        // BUG-30: Use customer-paid amount (bill - payable) not owner's personal payments (pending)
        AnalyticsMetric.PAID -> (card.bill - card.payable).coerceAtLeast(0.0)
        AnalyticsMetric.OUTSTANDING -> card.payable
    }
}

@Composable
private fun cardMetricColor(
    card: CardSummary,
    metric: AnalyticsMetric
): Color {
    return when (metric) {
        AnalyticsMetric.USAGE -> accountAccent(card.accountKind)
        AnalyticsMetric.PAID -> MaterialTheme.colorScheme.secondary
        AnalyticsMetric.OUTSTANDING -> warningColor()
    }
}

private fun customerMetricValue(
    customer: CustomerSummary,
    metric: AnalyticsMetric
): Double {
    return when (metric) {
        AnalyticsMetric.USAGE -> customer.totalAmount
        // creditDueAmount is already the total paid (manual + settled + partial)
        // as computed in FirebaseRepository.toSummary()
        AnalyticsMetric.PAID -> customer.creditDueAmount
        AnalyticsMetric.OUTSTANDING -> customer.balance
    }
}

@Composable
private fun customerMetricColor(
    customer: CustomerSummary,
    metric: AnalyticsMetric
): Color {
    return when (metric) {
        AnalyticsMetric.USAGE -> MaterialTheme.colorScheme.primary
        AnalyticsMetric.PAID -> MaterialTheme.colorScheme.secondary
        AnalyticsMetric.OUTSTANDING -> if (customer.balance > 0.0) warningColor() else MaterialTheme.colorScheme.primary
    }
}
