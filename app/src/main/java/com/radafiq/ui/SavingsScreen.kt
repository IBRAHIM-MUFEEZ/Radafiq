package com.radafiq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import java.time.LocalDate
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radafiq.data.models.CustomerSummary
import com.radafiq.data.models.SavingsEntry
import com.radafiq.data.models.SavingsType
import com.radafiq.data.models.IndianAccountCatalog
import com.radafiq.viewmodel.MainViewModel

// ── Full-screen savings detail for a customer ─────────────────────────────────
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CustomerSavingsScreen(
    customerId: String,
    vm: MainViewModel = viewModel(),
    onBack: () -> Unit
) {
    val customers by vm.customers.collectAsState()
    val customer = customers.find { it.id == customerId }

    if (customer == null) {
        LaunchedEffect(Unit) { onBack() }
        RadafiqBackground { Box(modifier = Modifier.fillMaxSize()) }
        return
    }

    var showDepositDialog    by remember { mutableStateOf(false) }
    var showWithdrawDialog   by remember { mutableStateOf(false) }
    var entryToDelete        by remember { mutableStateOf<SavingsEntry?>(null) }
    RadafiqBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = customer.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Savings Account",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                // Balance hero — keyed on snapshotVersion so LazyColumn recomposes
                // this item immediately when savings data changes
                item(key = "balance_${customer.snapshotVersion}") {
                    SavingsBalanceCard(
                        customer = customer,
                        onDeposit   = { showDepositDialog = true },
                        onWithdraw  = { showWithdrawDialog = true }
                    )
                }

                // History header
                item(key = "header_${customer.snapshotVersion}") {
                    Text(
                        text = "HISTORY",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (customer.savingsEntries.isEmpty()) {
                    item(key = "empty_${customer.snapshotVersion}") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyState(
                                title = "No savings yet",
                                subtitle = "Tap Deposit to record the first deposit for ${customer.name}."
                            )
                        }
                    }
                } else {
                    items(customer.savingsEntries, key = { it.id }) { entry ->
                        SavingsEntryRow(
                            entry = entry,
                            onDelete = { entryToDelete = entry }
                        )
                    }
                }
            }
        }
    }

    if (showDepositDialog) {
        SavingsEntryDialog(
            title = "Deposit",
            confirmLabel = "Deposit",
            confirmColor = MaterialTheme.colorScheme.primary,
            showBankAccountSelector = true,
            onDismiss = { showDepositDialog = false },
            onConfirm = { amount, note, date, bankAccountId, bankAccountName ->
                vm.addSavingsDeposit(customer.id, customer.name, amount, note, bankAccountId, bankAccountName, date)
                showDepositDialog = false
            }
        )
    }

    // Per-account breakdown for withdrawal selection
    // Keyed on savingsEntries so it recomputes every time the entry list changes,
    // including after both optimistic and server-confirmed updates.
    val withdrawableAccounts = remember(customer.savingsEntries) {
        val map = linkedMapOf<String, BankBreakdown>()
        customer.savingsEntries.forEach { e ->
            if (e.bankAccountId.isBlank()) return@forEach
            val key = e.bankAccountId
            val existing = map[key]
            if (existing == null) {
                map[key] = BankBreakdown(
                    id = key,
                    name = e.bankAccountName.ifBlank { e.bankAccountId },
                    deposited = if (e.type == SavingsType.DEPOSIT) e.amount else 0.0,
                    withdrawn = if (e.type == SavingsType.WITHDRAWAL) e.amount else 0.0
                )
            } else {
                map[key] = existing.copy(
                    deposited = existing.deposited + if (e.type == SavingsType.DEPOSIT) e.amount else 0.0,
                    withdrawn = existing.withdrawn + if (e.type == SavingsType.WITHDRAWAL) e.amount else 0.0
                )
            }
        }
        map.values.filter { it.balance > 0.0 }.toList()
    }

    if (showWithdrawDialog) {
        SavingsEntryDialog(
            title = "Withdraw",
            confirmLabel = "Withdraw",
            confirmColor = warningColor(),
            showBankAccountSelector = false,
            maxAmount = if (withdrawableAccounts.isNotEmpty()) null else customer.savingsBalance,
            withdrawableAccounts = withdrawableAccounts.ifEmpty { null },
            onDismiss = { showWithdrawDialog = false },
            onConfirm = { amount, note, date, bankAccountId, bankAccountName ->
                vm.addSavingsWithdrawal(customer.id, customer.name, amount, note, bankAccountId, bankAccountName, date)
                showWithdrawDialog = false
            }
        )
    }

    entryToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete entry?") },
            text = {
                Text(
                    "Remove this ${entry.type.label.lowercase()} of ${formatMoney(entry.amount)} on ${entry.date}? This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSavingsEntry(entry.id)
                    entryToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

// ── Per-bank-account breakdown data class ──────────────────────────────────────
private data class BankBreakdown(val id: String, val name: String, val deposited: Double, val withdrawn: Double) {
    val balance: Double get() = deposited - withdrawn
}

// ── Balance hero card ─────────────────────────────────────────────────────────
@Composable
private fun SavingsBalanceCard(
    customer: CustomerSummary,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit
) {
    val bankBreakdown = remember(customer.savingsEntries) {
        val map = linkedMapOf<String, BankBreakdown>()
        customer.savingsEntries.forEach { e ->
            if (e.bankAccountId.isBlank()) return@forEach
            val key = e.bankAccountId
            val existing = map[key]
            if (existing == null) {
                map[key] = BankBreakdown(
                    id = key,
                    name = e.bankAccountName.ifBlank { e.bankAccountId },
                    deposited = if (e.type == SavingsType.DEPOSIT) e.amount else 0.0,
                    withdrawn = if (e.type == SavingsType.WITHDRAWAL) e.amount else 0.0
                )
            } else {
                map[key] = existing.copy(
                    deposited = existing.deposited + if (e.type == SavingsType.DEPOSIT) e.amount else 0.0,
                    withdrawn = existing.withdrawn + if (e.type == SavingsType.WITHDRAWAL) e.amount else 0.0
                )
            }
        }
        map.values.filter { it.deposited > 0.0 }.toList()
    }

    FlowCard(accentColor = MaterialTheme.colorScheme.primary) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Available Balance",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            AnimatedMoney(
                value = customer.savingsBalance,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Bank account savings — not a loan",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Deposit / Withdraw totals
            val totalDeposited = customer.savingsEntries
                .filter { it.type == SavingsType.DEPOSIT }.sumOf { it.amount }
            val totalWithdrawn = customer.savingsEntries
                .filter { it.type == SavingsType.WITHDRAWAL }.sumOf { it.amount }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricPill(
                    label = "Total Deposited",
                    amountValue = totalDeposited,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                MetricPill(
                    label = "Total Withdrawn",
                    amountValue = totalWithdrawn,
                    color = warningColor(),
                    modifier = Modifier.weight(1f)
                )
            }

            // Per-bank breakdown
            if (bankBreakdown.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "PER ACCOUNT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                bankBreakdown.forEach { b ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = b.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = buildAnnotatedString {
                                    append("Dep. ${formatMoney(b.deposited)}")
                                    if (b.withdrawn > 0.0) {
                                        append(" | Wd. ${formatMoney(b.withdrawn)}")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            AnimatedMoney(
                                value = b.balance,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "available",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onDeposit,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Deposit", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Deposit")
                }
                Button(
                    onClick = onWithdraw,
                    modifier = Modifier.weight(1f),
                    enabled = customer.savingsBalance > 0.0,
                    colors = ButtonDefaults.buttonColors(containerColor = warningColor())
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Withdraw", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Withdraw")
                }
            }
        }
    }
}

// ── Single savings entry row ──────────────────────────────────────────────────
@Composable
private fun SavingsEntryRow(
    entry: SavingsEntry,
    onDelete: () -> Unit
) {
    val isDeposit = entry.type == SavingsType.DEPOSIT
    val accentColor = if (isDeposit) MaterialTheme.colorScheme.primary else warningColor()

    FlowCard(accentColor = accentColor) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left accent dot
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = if (isDeposit) Icons.Default.Add else Icons.Default.Remove,
                    contentDescription = entry.type.label,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.type.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor
                    )
                    Text(
                        text = formatDisplayDate(entry.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isDeposit && entry.bankAccountName.isNotBlank()) {
                    Text(
                        text = entry.bankAccountName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (entry.note.isNotBlank()) {
                    Text(
                        text = entry.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = "${if (isDeposit) "+" else "-"}${formatMoney(entry.amount)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ── Deposit / Withdraw dialog ─────────────────────────────────────────────────
@Composable
private fun SavingsEntryDialog(
    title: String,
    confirmLabel: String,
    confirmColor: Color,
    maxAmount: Double? = null,
    showBankAccountSelector: Boolean = false,
    withdrawableAccounts: List<BankBreakdown>? = null,
    onDismiss: () -> Unit,
    onConfirm: (amount: String, note: String, date: String, bankAccountId: String, bankAccountName: String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var note   by remember { mutableStateOf("") }
    var date   by remember { mutableStateOf(LocalDate.now().toString()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Bank account selector state (deposit)
    val allBankAccounts = remember { IndianAccountCatalog.bankAccounts }
    var selectedBankId   by remember { mutableStateOf(allBankAccounts.firstOrNull()?.id ?: "") }
    var selectedBankName by remember { mutableStateOf(allBankAccounts.firstOrNull()?.name ?: "") }
    var bankDropdownExpanded by remember { mutableStateOf(false) }

    // Withdrawal account selector state — keyed on withdrawableAccounts so it
    // re-initialises when the available accounts change (real-time balance updates)
    var selectedWithdrawAcctName by remember(withdrawableAccounts) { mutableStateOf(withdrawableAccounts?.firstOrNull()?.name ?: "") }
    var withdrawDropdownExpanded by remember { mutableStateOf(false) }

    val isWithdrawal = withdrawableAccounts != null
    val selectedAccountBalance = remember(selectedWithdrawAcctName, withdrawableAccounts) {
        if (isWithdrawal && selectedWithdrawAcctName.isNotBlank()) {
            withdrawableAccounts?.find { it.name == selectedWithdrawAcctName }?.balance ?: 0.0
        } else {
            maxAmount ?: 0.0
        }
    }
    val effectiveMaxAmount = if (isWithdrawal) selectedAccountBalance else maxAmount

    val parsedAmount = amount.toDoubleOrNull() ?: 0.0
    val isValid = parsedAmount > 0.0 && (effectiveMaxAmount == null || parsedAmount <= effectiveMaxAmount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Withdrawal: bank account dropdown with balances
                if (isWithdrawal && withdrawableAccounts != null) {
                    if (withdrawableAccounts.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedWithdrawAcctName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Bank Account") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Select account"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { withdrawDropdownExpanded = true }
                            )
                            DropdownMenu(
                                expanded = withdrawDropdownExpanded,
                                onDismissRequest = { withdrawDropdownExpanded = false }
                            ) {
                                withdrawableAccounts.forEach { acct ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(acct.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    text = "Available: ${formatMoney(acct.balance)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedWithdrawAcctName = acct.name
                                            amount = ""
                                            withdrawDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        if (selectedWithdrawAcctName.isNotBlank()) {
                            Text(
                                text = "Available: ${formatMoney(selectedAccountBalance)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = "No accounts available for withdrawal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (maxAmount != null) {
                    Text(
                        text = "Available: ${formatMoney(maxAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    leadingIcon = { Text("₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    isError = amount.isNotBlank() && !isValid
                )
                OutlinedTextField(
                    value = formatDisplayDate(date),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date") },
                    trailingIcon = {
                        androidx.compose.material3.IconButton(onClick = { showDatePicker = true }) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Pick date"
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                )

                if (showDatePicker) {
                    DatePickerDialog(
                        initialDateIso = date,
                        onDateSelected = { iso ->
                            date = iso
                            showDatePicker = false
                        },
                        onDismiss = { showDatePicker = false }
                    )
                }

                if (!isWithdrawal && showBankAccountSelector && allBankAccounts.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedBankName.ifBlank { "— No specific account —" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Bank Account (where savings are held)") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Select bank account"
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { bankDropdownExpanded = true }
                        )
                        DropdownMenu(
                            expanded = bankDropdownExpanded,
                            onDismissRequest = { bankDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("— No specific account —") },
                                onClick = {
                                    selectedBankId = ""
                                    selectedBankName = ""
                                    bankDropdownExpanded = false
                                }
                            )
                            allBankAccounts.forEach { acct ->
                                DropdownMenuItem(
                                    text = { Text(acct.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = {
                                        selectedBankId = acct.id
                                        selectedBankName = acct.name
                                        bankDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isWithdrawal) {
                        val acct = withdrawableAccounts?.find { it.name == selectedWithdrawAcctName }
                        onConfirm(amount, note, date, acct?.id ?: "", acct?.name ?: "")
                    } else {
                        onConfirm(amount, note, date, selectedBankId, selectedBankName)
                    }
                },
                enabled = isValid && (!isWithdrawal || (withdrawableAccounts?.isNotEmpty() == true && selectedWithdrawAcctName.isNotBlank())),
                colors = ButtonDefaults.buttonColors(containerColor = confirmColor)
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
