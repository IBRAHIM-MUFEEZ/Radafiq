package com.radafiq.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radafiq.data.models.AccountKind
import com.radafiq.data.models.CardSummary
import com.radafiq.data.models.CustomerSummary
import com.radafiq.reminders.DueReminderScheduler
import com.radafiq.viewmodel.MainViewModel
import com.radafiq.viewmodel.PersonSummary
import kotlin.math.abs

@Composable
fun AccountsScreen(
    vm: MainViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onOpenAccount: (String) -> Unit = {}
) {
    val aggregates by vm.aggregates.collectAsState()
    val visibleCards = aggregates.visibleCards
    val creditCards = visibleCards.filter { it.accountKind == AccountKind.CREDIT_CARD }
    val bankAccounts = visibleCards.filter { it.accountKind == AccountKind.BANK_ACCOUNT }
    val personCards = aggregates.customerAgg.personSummaries
    val savingsCustomers = aggregates.savingsCustomers

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .radafiqScrollBackground()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageHeader(
                title = "Accounts",
                subtitle = "Monitor banks, credit cards, dues, and usage.",
                trailing = {}
            )
        }

        if (visibleCards.isEmpty()) {
            item {
                Box(modifier = Modifier.height(420.dp)) {
                    EmptyState(
                        title = "No linked totals yet",
                        subtitle = "Customer transactions will appear here automatically."
                    )
                }
            }
        } else {
            item { AccountSectionTitle("Credit Cards") }

            if (creditCards.isEmpty()) {
                item {
                    Text(
                        text = "No credit card totals yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(creditCards, key = { it.id }) { card ->
                    AccountListRow(card = card, onClick = { onOpenAccount(card.id) })
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                AccountSectionTitle("Bank Accounts")
            }

            if (bankAccounts.isEmpty()) {
                item {
                    Text(
                        text = "No bank account totals yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(bankAccounts, key = { it.id }) { card ->
                    AccountListRow(card = card, onClick = { onOpenAccount(card.id) })
                }
            }

            if (personCards.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    AccountSectionTitle("Persons")
                }
                items(personCards, key = { "person_${it.personId}" }) { entry ->
                    PersonAccountRow(name = entry.personName, usedAmount = entry.totalUsed, dueAmount = entry.totalDue)
                }
            }

            // Savings section — customers who have a positive savings balance
            if (savingsCustomers.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    AccountSectionTitle("Savings")
                }
                items(savingsCustomers, key = { "savings_${it.id}" }) { c ->
                    SavingsAccountRow(customer = c)
                }
            }
        }
    }
}

@Composable
fun AccountListRow(
    card: CardSummary,
    onClick: () -> Unit
) {
    var showBalance by remember { mutableStateOf(true) }
    FlowCard(
        accentColor = accountAccent(card.accountKind),
        modifier = Modifier.bounceClick(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = card.accountKind.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.clickable { showBalance = !showBalance }
            ) {
                AnimatedMoney(
                    value = if (showBalance) card.payable else card.bill,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (showBalance && card.payable > 0.0) warningColor() else accountAccent(card.accountKind),
                    animationKey = showBalance
                )
                Text(
                    text = if (showBalance) "Balance" else "Total used",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AccountSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun PersonAccountRow(
    name: String,
    usedAmount: Double,
    dueAmount: Double
) {
    val accent = accountAccent(AccountKind.PERSON)
    var showBalance by remember { mutableStateOf(true) }
    FlowCard(accentColor = accent, modifier = Modifier.bounceClick { }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (showBalance) "Person • Used ${formatMoney(usedAmount)}" else "Person • Balance ${formatMoney(dueAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.clickable { showBalance = !showBalance }
            ) {
                AnimatedMoney(
                    value = if (showBalance) dueAmount else usedAmount,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (showBalance && dueAmount > 0.0) warningColor() else accent,
                    animationKey = showBalance
                )
                Text(
                    text = if (showBalance) (if (dueAmount > 0.0) "Balance" else "Settled") else "Total used",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SavingsAccountRow(customer: com.radafiq.data.models.CustomerSummary) {
    val accent = MaterialTheme.colorScheme.secondary
    var showDeposited by remember { mutableStateOf(false) }
    val totalDeposited = customer.savingsEntries
        .filter { it.type == com.radafiq.data.models.SavingsType.DEPOSIT }.sumOf { it.amount }
    val latestBankName = customer.savingsEntries
        .filter { it.bankAccountName.isNotBlank() }
        .maxByOrNull { it.date }?.bankAccountName

    FlowCard(accentColor = accent, modifier = Modifier.bounceClick { }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (latestBankName != null) latestBankName else "Savings",
                    style = MaterialTheme.typography.bodySmall,
                    color = accent,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.clickable { showDeposited = !showDeposited }
            ) {
                AnimatedMoney(
                    value = if (showDeposited) totalDeposited else customer.savingsBalance,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    animationKey = showDeposited
                )
                Text(
                    text = if (showDeposited) "Total Deposited" else "Balance",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AccountCard(
    card: CardSummary,
    onAdjustPaid: ((String) -> Unit)? = null,
    onUpdateCreditDue: ((amount: String, dueDate: String, remindersEnabled: Boolean, reminderEmail: String, reminderWhatsApp: String) -> Unit)? = null
) {
    var showPaidEditor by remember { mutableStateOf(false) }
    var showDueEditor by remember { mutableStateOf(false) }

    FlowCard(accentColor = accountAccent(card.accountKind)) {
        AdaptiveHeaderRow(
            leading = {
                Column {
                    Text(
                        text = card.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = card.accountKind.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            trailing = {
                if (card.accountKind == AccountKind.CREDIT_CARD && onUpdateCreditDue != null) {
                    Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.End
                    ) {
                        AnimatedMoney(
                            value = card.payable,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (onAdjustPaid != null) {
                                TextButton(onClick = { showPaidEditor = true }) {
                                    Text("Adjust Paid")
                                }
                            }
                            TextButton(onClick = { showDueEditor = true }) {
                                Text("Edit Due")
                            }
                        }
                    }
                } else {
                    AnimatedMoney(
                        value = card.payable,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        ResponsiveTwoPane(
            first = { itemModifier ->
                MetricPill(
                    label = "Used",
                    amountValue = card.bill,
                    color = accountAccent(card.accountKind),
                    modifier = itemModifier
                )
            },
            second = { itemModifier ->
                MetricPill(
                    label = "Personal Paid",
                    amountValue = card.pending,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = itemModifier
                )
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        AccentValueRow(
            label = "Balance",
            amountValue = card.payable,
            color = accountAccent(card.accountKind)
        )

        if (card.accountKind == AccountKind.CREDIT_CARD) {
            CreditCardDueStatus(card)
        }
    }

    if (showPaidEditor && onAdjustPaid != null) {
        CreditCardPaidDialog(
            card = card,
            onDismiss = { showPaidEditor = false },
            onSave = { amount ->
                onAdjustPaid(amount)
                showPaidEditor = false
            }
        )
    }

    if (showDueEditor && onUpdateCreditDue != null) {
        CreditCardDueDialog(
            card = card,
            onDismiss = { showDueEditor = false },
            onSave = { amount, dueDate, remindersEnabled, reminderEmail, reminderWhatsApp ->
                onUpdateCreditDue(amount, dueDate, remindersEnabled, reminderEmail, reminderWhatsApp)
                showDueEditor = false
            }
        )
    }
}

@Composable
fun CreditCardPaidDialog(
    card: CardSummary,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var paidAmount by remember(card.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Adjust paid for ${card.name}",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column {
                Text(
                    text = "Add the extra paid amount you want to record for this credit card.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = paidAmount,
                    onValueChange = { paidAmount = it },
                    label = { Text("Paid Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    leadingIcon = { Text("₹") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(paidAmount) },
                enabled = (paidAmount.toDoubleOrNull() ?: 0.0) > 0.0
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CreditCardDueStatus(card: CardSummary) {
    val remainingDue = card.dueAmount - card.pending
    val statusColor = when {
        card.dueAmount <= 0.0 -> MaterialTheme.colorScheme.onSurfaceVariant
        remainingDue > 0.0 -> warningColor()
        remainingDue < 0.0 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    val message = when {
        card.dueAmount <= 0.0 -> "No card due amount set."
        remainingDue > 0.0 -> "You still need to pay ${formatMoney(remainingDue)} on this credit card."
        remainingDue < 0.0 -> "You have overpaid ${formatMoney(abs(remainingDue))} on this credit card."
        else -> "This credit card due is fully covered."
    }
    val balanceLabel = when {
        card.dueAmount <= 0.0 -> "Due status"
        remainingDue < 0.0 -> "Overpaid"
        else -> "Remaining due"
    }
    val balanceAmountValue = when {
        card.dueAmount <= 0.0 -> 0.0
        remainingDue < 0.0 -> abs(remainingDue)
        else -> remainingDue
    }
    val progress = when {
        card.dueAmount <= 0.0 -> 0f
        card.pending >= card.dueAmount -> 1f
        else -> (card.pending / card.dueAmount).toFloat().coerceIn(0f, 1f)
    }

    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f))

    Column(modifier = Modifier.padding(top = 12.dp)) {
        AdaptiveHeaderRow(
            leading = {
                Column {
                    Text(
                        text = "Credit card due",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (card.dueDate.isBlank()) "Add a due date to track this card." else "Due ${card.dueDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            },
            trailing = {
                StatusBadge(
                    text = when {
                        card.dueAmount <= 0.0 -> "Unset"
                        remainingDue < 0.0 -> "Overpaid"
                        remainingDue > 0.0 -> "Pending"
                        else -> "Covered"
                    },
                    color = statusColor
                )
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        ResponsiveTwoPane(
            first = { itemModifier ->
                MetricPill(
                    label = "Due",
                    amountValue = card.dueAmount,
                    color = statusColor,
                    modifier = itemModifier
                )
            },
            second = { itemModifier ->
                MetricPill(
                    label = "Personal Paid",
                    amountValue = card.pending,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = itemModifier
                )
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp)),
            color = statusColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        AccentValueRow(
            label = balanceLabel,
            amountValue = balanceAmountValue,
            color = statusColor
        )

        Text(
            text = "Customer used for this card: ${formatMoney(card.bill)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (card.remindersEnabled) {
            Text(
                text = "Daily reminders are enabled from 5 days before the due date.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (card.reminderEmail.isNotBlank()) {
                Text(
                    text = "Email target: ${card.reminderEmail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (card.reminderWhatsApp.isNotBlank()) {
                Text(
                    text = "WhatsApp target: ${card.reminderWhatsApp}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = statusColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun CreditCardDueDialog(
    card: CardSummary,
    onDismiss: () -> Unit,
    onSave: (amount: String, dueDate: String, remindersEnabled: Boolean, reminderEmail: String, reminderWhatsApp: String) -> Unit
) {
    var dueAmount by remember(card.id, card.dueAmount) {
        mutableStateOf(card.dueAmount.takeIf { it > 0.0 }?.toString().orEmpty())
    }
    var dueDate by remember(card.id, card.dueDate) {
        mutableStateOf(card.dueDate)
    }
    var remindersEnabled by remember(card.id, card.remindersEnabled) {
        mutableStateOf(card.remindersEnabled)
    }
    var reminderEmail by remember(card.id, card.reminderEmail) {
        mutableStateOf(card.reminderEmail)
    }
    var reminderWhatsApp by remember(card.id, card.reminderWhatsApp) {
        mutableStateOf(card.reminderWhatsApp)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit ${card.name} due",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = dueAmount,
                    onValueChange = { dueAmount = it },
                    label = { Text("Credit Card Due Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    leadingIcon = { Text("₹") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("Due Date (YYYY-MM-DD)") },
                    singleLine = true,
                    // FIX-19: Show error when date format is invalid
                    isError = dueDate.isNotBlank() &&
                        runCatching { java.time.LocalDate.parse(dueDate) }.isFailure,
                    supportingText = if (dueDate.isNotBlank() &&
                        runCatching { java.time.LocalDate.parse(dueDate) }.isFailure
                    ) {
                        { Text("Use format YYYY-MM-DD, e.g. 2025-06-15") }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Daily due reminders",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "One reminder per day from 5 days before the due date until the due date.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = remindersEnabled,
                        onCheckedChange = { remindersEnabled = it },
                        colors = radafiqSwitchColors()
                    )
                }
                if (remindersEnabled) {
                    OutlinedTextField(
                        value = reminderEmail,
                        onValueChange = { reminderEmail = it },
                        label = { Text("Reminder Email") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = reminderWhatsApp,
                        onValueChange = { reminderWhatsApp = it },
                        label = { Text("WhatsApp Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        dueAmount,
                        dueDate,
                        remindersEnabled,
                        reminderEmail,
                        reminderWhatsApp
                    )
                },
                // FIX-19: Validate date format — must parse as a valid LocalDate
                enabled = dueAmount.toDoubleOrNull() != null &&
                    dueDate.isNotBlank() &&
                    runCatching { java.time.LocalDate.parse(dueDate) }.isSuccess
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ── Account detail screen ─────────────────────────────────────────────────────
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    accountId: String,
    vm: MainViewModel = viewModel(),
    onBack: () -> Unit,
    onOpenCustomer: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val dueReminderScheduler = remember(context) { DueReminderScheduler(context) }
    val cards by vm.cards.collectAsState()
    val customers by vm.customers.collectAsState()
    val card = cards.find { it.id == accountId }

    if (card == null) {
        LaunchedEffect(Unit) { onBack() }
        RadafiqBackground { Box(modifier = Modifier.fillMaxSize()) }
        return
    }

    val snapshotKey = customers.sumOf { it.snapshotVersion }
    // Customers that have at least one transaction on this account
    val accountCustomers = remember(snapshotKey, accountId) {
        customers.mapNotNull { customer ->
            val txns = customer.transactions.filter { it.accountId == accountId && it.isVisibleInTransactions() }
            if (txns.isEmpty()) null
            else {
                val used = txns.sumOf { it.amount }
                // Settled: full amount paid. Unsettled: only partial counts.
                val paid = txns.filter { it.isSettled }.sumOf { it.amount } +
                    txns.filter { !it.isSettled }.sumOf { it.partialPaidAmount }
                val due = (used - paid).coerceAtLeast(0.0)
                Triple(customer, used, due)
            }
        }.sortedByDescending { it.third }
    }

    val accentColor = accountAccent(card.accountKind)

    RadafiqBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = card.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = card.accountKind.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Back")
                        }
                    },
                    actions = {},
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .radafiqScrollBackground()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Summary strip
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricPill(
                            label = "Total Used",
                            amountValue = card.bill,
                            color = accentColor,
                            modifier = Modifier.weight(1f)
                        )
                        MetricPill(
                            label = "Personal Paid",
                            amountValue = card.pending,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricPill(
                            label = "Balance",
                            amountValue = card.payable,
                            color = if (card.payable > 0.0) warningColor() else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Credit card due section
                if (card.accountKind == AccountKind.CREDIT_CARD) {
                    item {
                        FlowCard(accentColor = accentColor) {
                            var showPaidEditor by remember { mutableStateOf(false) }
                            var showDueEditor by remember { mutableStateOf(false) }

                            AdaptiveHeaderRow(
                                leading = {
                                    Text(
                                        text = "Credit Card Due",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                trailing = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(onClick = { showPaidEditor = true }) { Text("Adjust Paid") }
                                        TextButton(onClick = { showDueEditor = true }) { Text("Edit Due") }
                                    }
                                }
                            )
                            CreditCardDueStatus(card)

                            if (showPaidEditor) {
                                CreditCardPaidDialog(
                                    card = card,
                                    onDismiss = { showPaidEditor = false },
                                    onSave = { amount ->
                                        vm.addPayment(
                                            accountId = card.id,
                                            accountName = card.name,
                                            accountKind = card.accountKind,
                                            amount = amount
                                        )
                                        showPaidEditor = false
                                    }
                                )
                            }
                            if (showDueEditor) {
                                CreditCardDueDialog(
                                    card = card,
                                    onDismiss = { showDueEditor = false },
                                    onSave = { amount, dueDate, remindersEnabled, reminderEmail, reminderWhatsApp ->
                                        vm.updateCreditCardDue(
                                            accountId = card.id,
                                            accountName = card.name,
                                            amount = amount,
                                            dueDate = dueDate,
                                            remindersEnabled = remindersEnabled,
                                            reminderEmail = reminderEmail,
                                            reminderWhatsApp = reminderWhatsApp
                                        )
                                        dueReminderScheduler.syncDueReminders(
                                            accountId = card.id,
                                            accountName = card.name,
                                            dueDate = dueDate,
                                            enabled = remindersEnabled
                                        )
                                        showDueEditor = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Customers header
                item {
                    Text(
                        text = "Customers",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (accountCustomers.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyState(
                                title = "No customers yet",
                                subtitle = "Customers who use this account will appear here."
                            )
                        }
                    }
                } else {
                    items(accountCustomers, key = { it.first.id }) { (customer, used, due) ->
                        AccountCustomerRow(
                            customer = customer,
                            usedAmount = used,
                            dueAmount = due,
                            accentColor = accentColor,
                            onClick = { onOpenCustomer(customer.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCustomerRow(
    customer: CustomerSummary,
    usedAmount: Double,
    dueAmount: Double,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit = {}
) {
    FlowCard(accentColor = accentColor) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Used ${formatMoney(usedAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                AnimatedMoney(
                    value = dueAmount,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (dueAmount > 0.0) warningColor() else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (dueAmount > 0.0) "Due" else "Settled",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
