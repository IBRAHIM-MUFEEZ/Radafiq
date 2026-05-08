package com.radafiq.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radafiq.data.models.AccountKind
import com.radafiq.data.models.AccountOption
import com.radafiq.data.models.CustomerSummary
import com.radafiq.data.models.CustomerTransaction
import com.radafiq.data.models.IndianAccountCatalog
import com.radafiq.data.models.SplitEntry
import com.radafiq.viewmodel.DraftTransactionState
import com.radafiq.viewmodel.MainViewModel
import java.time.LocalDate
import kotlinx.coroutines.launch

// Count split groups as 1 logical transaction each
internal fun List<CustomerTransaction>.countLogicalTransactions(): Int {
    // BUG-19: Use a MutableSet and check add() return value to avoid double-counting
    val seenSplitGroups = mutableSetOf<String>()
    var count = 0
    forEach { t ->
        if (t.splitGroupId.isBlank()) {
            count++
        } else {
            // add() returns true only if the element was not already present
            if (seenSplitGroups.add(t.splitGroupId)) {
                count++
            }
        }
    }
    return count
}

private enum class CustomerViewMode(val label: String) {
    ACTIVE("Customers"),
    RECYCLE_BIN("Recycle Bin")
}

private enum class TransactionTypeFilter(val label: String) {
    ALL("All Transactions"),
    BANK_ACCOUNT("Bank Account"),
    CREDIT_CARD("Credit Card"),
    PERSON("Person")
}

private sealed class CustomerListItem {
    data class Header(val letter: Char) : CustomerListItem()
    data class Customer(val summary: CustomerSummary) : CustomerListItem()
}

@Composable
fun CustomersScreen(
    selectedAccountIds: Set<String>,
    vm: MainViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onOpenCustomer: (String) -> Unit = {}
) {
    val customers by vm.customers.collectAsState()
    val deletedCustomers by vm.deletedCustomers.collectAsState()
    val syncStatus by vm.syncStatus.collectAsState()
    var viewMode by remember { mutableStateOf(CustomerViewMode.ACTIVE) }
    var searchQuery by remember { mutableStateOf("") }

    val allVisibleCustomers = if (viewMode == CustomerViewMode.ACTIVE) customers else deletedCustomers

    // Sort alphabetically, then filter by search
    val sortedCustomers = remember(allVisibleCustomers) {
        allVisibleCustomers.sortedBy { it.name.uppercase() }
    }
    val visibleCustomers = remember(sortedCustomers, searchQuery) {
        if (searchQuery.isBlank()) sortedCustomers
        else sortedCustomers.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    // Build letter → first list-item index map (accounts for header + search bar items)
    // Item 0 = header, Item 1 = search bar, then customer rows start at index 2
    // When a letter header is inserted, it occupies an item too.
    // We build a flat ordered list of items: LetterHeader | CustomerItem

    val listItems = remember(visibleCustomers, searchQuery) {
        if (visibleCustomers.isEmpty() || searchQuery.isNotBlank()) {
            // No letter headers when searching
            visibleCustomers.map { CustomerListItem.Customer(it) }
        } else {
            val result = mutableListOf<CustomerListItem>()
            var lastLetter: Char? = null
            visibleCustomers.forEach { c ->
                val letter = c.name.firstOrNull()?.uppercaseChar() ?: '#'
                val bucket = if (letter.isLetter()) letter else '#'
                if (bucket != lastLetter) {
                    result.add(CustomerListItem.Header(bucket))
                    lastLetter = bucket
                }
                result.add(CustomerListItem.Customer(c))
            }
            result
        }
    }

    // Letters present in the current list (for the index strip)
    val indexLetters = remember(listItems) {
        listItems.filterIsInstance<CustomerListItem.Header>().map { it.letter }
    }

    // Map letter → lazylist index (offset by 2 for header + search bar fixed items)
    val letterToIndex = remember(listItems) {
        val map = mutableMapOf<Char, Int>()
        listItems.forEachIndexed { i, item ->
            if (item is CustomerListItem.Header) map[item.letter] = i + 2 // +2 for PageHeader + SearchBar
        }
        map
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Rotating animation for sync icon
    val infiniteTransition = rememberInfiniteTransition(label = "sync-rotate")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sync-angle"
    )
    val isSyncing = syncStatus.state == MainViewModel.SyncState.SYNCING

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 36.dp), // right padding leaves room for index strip
            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                PageHeader(
                    title = viewMode.label,
                    subtitle = if (viewMode == CustomerViewMode.ACTIVE) {
                        "Manage customer ledgers, due collections, and settled transactions."
                    } else {
                        "Restore customers or remove old records forever."
                    },
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (syncStatus.message.isNotBlank()) {
                                Text(
                                    text = syncStatus.message,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (syncStatus.state) {
                                        MainViewModel.SyncState.SUCCESS -> MaterialTheme.colorScheme.secondary
                                        MainViewModel.SyncState.ERROR -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 2,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                            IconButton(onClick = { vm.triggerSync() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync to Drive",
                                    tint = when (syncStatus.state) {
                                        MainViewModel.SyncState.SUCCESS -> MaterialTheme.colorScheme.secondary
                                        MainViewModel.SyncState.ERROR -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    modifier = if (isSyncing) Modifier.rotate(rotation) else Modifier
                                )
                            }
                            TextButton(
                                onClick = {
                                    viewMode = if (viewMode == CustomerViewMode.ACTIVE)
                                        CustomerViewMode.RECYCLE_BIN else CustomerViewMode.ACTIVE
                                }
                            ) {
                                Text(if (viewMode == CustomerViewMode.ACTIVE) "Recycle Bin" else "Customers")
                            }
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        }
                    }
                )
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search customers") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (visibleCustomers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            title = if (viewMode == CustomerViewMode.ACTIVE) "No customers yet"
                                    else "Recycle bin is empty",
                            subtitle = if (viewMode == CustomerViewMode.ACTIVE)
                                "Tap + to add your first customer ledger."
                            else
                                "Deleted customers will wait here before permanent removal."
                        )
                    }
                }
            } else {
                items(listItems, key = { item ->
                    when (item) {
                        is CustomerListItem.Header -> "header_${item.letter}"
                        is CustomerListItem.Customer -> item.summary.id
                    }
                }) { item ->
                    when (item) {
                        is CustomerListItem.Header -> {
                            Text(
                                text = item.letter.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 4.dp, bottom = 2.dp, start = 4.dp)
                            )
                        }
                        is CustomerListItem.Customer -> {
                            val customer = item.summary
                            if (viewMode == CustomerViewMode.ACTIVE) {
                                CustomerListRow(
                                    customer = customer,
                                    onClick = { onOpenCustomer(customer.id) }
                                )
                            } else {
                                DeletedCustomerCard(
                                    customer = customer,
                                    onRestore = { vm.restoreCustomer(customer.id) },
                                    onDeleteForever = {
                                        vm.permanentlyDeleteCustomer(
                                            customerId = customer.id,
                                            customerName = customer.name
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Alphabet index strip — only show in active mode with no search query
        if (viewMode == CustomerViewMode.ACTIVE && searchQuery.isBlank() && indexLetters.isNotEmpty()) {
            AlphabetIndexStrip(
                letters = indexLetters,
                onLetterSelected = { letter ->
                    letterToIndex[letter]?.let { idx ->
                        coroutineScope.launch { listState.scrollToItem(idx) }
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = 4.dp, top = 80.dp, bottom = 120.dp)
            )
        }
    }
}

@Composable
private fun AlphabetIndexStrip(
    letters: List<Char>,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    var stripHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .widthIn(min = 20.dp)
            .onSizeChanged { stripHeightPx = it.height }
            .pointerInput(letters) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (letters.isEmpty() || stripHeightPx == 0) return@detectDragGestures
                        val idx = ((offset.y / stripHeightPx) * letters.size)
                            .toInt().coerceIn(0, letters.lastIndex)
                        onLetterSelected(letters[idx])
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        if (letters.isEmpty() || stripHeightPx == 0) return@detectDragGestures
                        val idx = ((change.position.y / stripHeightPx) * letters.size)
                            .toInt().coerceIn(0, letters.lastIndex)
                        onLetterSelected(letters[idx])
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxHeight()
        ) {
            letters.forEach { letter ->
                Text(
                    text = letter.toString(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clickable { onLetterSelected(letter) }
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
fun CustomerCard(
    customer: CustomerSummary,
    vm: MainViewModel,
    selectedAccountIds: Set<String>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    var showAddTransaction by remember { mutableStateOf(false) }
    var transactionToEdit by remember { mutableStateOf<CustomerTransaction?>(null) }
    var transactionFilter by remember(customer.id) { mutableStateOf(TransactionTypeFilter.ALL) }
    // FIX-17: Confirmation dialog state before deleting a customer
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Auto-reopen dialog if a draft exists for this customer (e.g. after biometric lock)
    val draft by vm.draftTransaction.collectAsState()
    LaunchedEffect(draft.customerId) {
        if (draft.customerId == customer.id && !draft.isEmpty) {
            showAddTransaction = true
        }
    }

    val filteredTransactions = remember(customer.transactions, transactionFilter) {
        val predicate: ((CustomerTransaction) -> Boolean)? = when (transactionFilter) {
            TransactionTypeFilter.ALL -> null
            TransactionTypeFilter.BANK_ACCOUNT -> { t -> t.accountKind == AccountKind.BANK_ACCOUNT }
            TransactionTypeFilter.CREDIT_CARD -> { t -> t.accountKind == AccountKind.CREDIT_CARD }
            TransactionTypeFilter.PERSON -> { t -> t.accountKind == AccountKind.PERSON }
        }
        val today = java.time.LocalDate.now()
        // BUG-17: Do NOT pre-sort here — buildGroupedTransactions handles sorting by group date/total.
        // Pre-sorting by individual amount before grouping produces incorrect group order for splits.
        customer.transactions
            .filter { t -> t.isVisibleInTransactions(today) }
            .let { if (predicate == null) it else it.filter(predicate) }
    }
    FlowCard(
        accentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(vertical = 4.dp)
            .animateContentSize()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onToggleExpanded)
                        .padding(4.dp)
                ) {
                    AdaptiveHeaderRow(
                        leading = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .width(48.dp)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        .border(
                                            width = 1.dp,
                                            color = Color.White.copy(alpha = 0.55f),
                                            shape = RoundedCornerShape(24.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initialsFor(customer.name),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Column(modifier = Modifier.padding(start = 12.dp)) {
                                    Text(
                                        text = customer.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${customer.transactions.countLogicalTransactions()} transaction(s)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        },
                        trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Balance",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatMoney(customer.balance),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (customer.balance > 0.0) {
                                            warningColor()
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        // FIX-17: Show confirmation before deleting
                                        showDeleteConfirm = true
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete Customer",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    ResponsiveTwoPane(
                        first = { itemModifier ->
                            MetricPill(
                                label = "Used",
                                value = formatMoney(customer.totalAmount),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = itemModifier
                            )
                        },
                        second = { itemModifier ->
                            MetricPill(
                                label = "Customer Paid",
                                value = formatMoney(customer.creditDueAmount),
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = itemModifier
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    AccentValueRow(
                        label = "Balance Remaining",
                        value = formatMoney(customer.balance),
                        color = if (customer.balance > 0.0) warningColor() else MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "Manual paid ${formatMoney(customer.manualPaidAmount)} • Settled ${formatMoney(customer.settledTransactionAmount)} • Partial ${formatMoney(customer.partialPaidAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )

                    Text(
                        text = "Tap to hide transaction details",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                CustomerSectionHeader(
                    title = "Transactions",
                    actionLabel = "Add",
                    onAction = { showAddTransaction = true }
                )

                TransactionTypeDropdown(
                    selectedFilter = transactionFilter,
                    onFilterSelected = { transactionFilter = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 8.dp)
                )

                val today = java.time.LocalDate.now()

                // BUG-09: Use remember to avoid recomputing grouped transactions on every recomposition
                // filteredTransactions is already visibility-filtered and type-filtered
                val grouped = remember(filteredTransactions) {
                    buildGroupedTransactions(filteredTransactions)
                }

                if (grouped.isEmpty()) {
                    EmptyInlineState("No transactions for this selection.")
                } else {
                    // Compute running balance from the filtered list's total due (not customer.balance
                    // which may include transactions excluded by the current filter)
                    val filteredDue = grouped.sumOf { group ->
                        group.splits.sumOf { split ->
                            if (split.isSettled) 0.0
                            else (split.amount - split.partialPaidAmount).coerceAtLeast(0.0)
                        }
                    }
                    var runningBal = filteredDue
                    val groupsWithBal = grouped.map { group ->
                        val groupDue = group.splits.sumOf { split ->
                            if (split.isSettled) 0.0
                            else (split.amount - split.partialPaidAmount).coerceAtLeast(0.0)
                        }
                        val entry = group to runningBal
                        runningBal -= groupDue
                        entry
                    }
                    // Date-grouped rendering
                    val byDate = groupsWithBal.groupBy { (g, _) -> g.transactionDate }
                    byDate.keys.sortedDescending().forEach { date ->
                        DateSeparatorChip(date = date, today = today)
                        byDate[date]?.forEach { (group, bal) ->
                            if (group.splits.size > 1) {
                                SplitTransactionRow(
                                    group = group,
                                    runningBalance = bal,
                                    onDeleteAll = { group.splits.forEach { vm.deleteTransaction(it.id) } },
                                    onSettledChangeAll = { settled ->
                                        group.splits.forEach { vm.toggleTransactionSettled(it.id, settled) }
                                    },
                                    onSettledChange = { txnId, settled -> vm.toggleTransactionSettled(txnId, settled) },
                                    onPartialPayment = { txnId, amount -> vm.addPartialPayment(txnId, amount) },
                                    onEditSplit = { transactionToEdit = it }
                                )
                            } else {
                                val transaction = group.splits.first()
                                TransactionRow(
                                    transaction = transaction,
                                    runningBalance = bal,
                                    onEdit = { transactionToEdit = transaction },
                                    onDelete = { vm.deleteTransaction(transaction.id) },
                                    onSettledChange = { vm.toggleTransactionSettled(transaction.id, it) },
                                    onPartialPayment = { amount -> vm.addPartialPayment(transaction.id, amount) }
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onToggleExpanded)
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = customer.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Due ${formatMoney(customer.balance)}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (customer.balance > 0.0) {
                                warningColor()
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            textAlign = TextAlign.End
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showAddTransaction) {
        TransactionEditorDialog(
            customer = customer,
            selectedAccountIds = selectedAccountIds,
            transaction = null,
            onDismiss = { showAddTransaction = false },
            onSave = { transactionName, accountId, accountName, accountKind, amount, transactionDate, personName ->
                vm.addTransaction(
                    customerId = customer.id,
                    transactionName = transactionName,
                    customerName = customer.name,
                    accountId = accountId,
                    accountName = accountName,
                    accountKind = accountKind,
                    amount = amount,
                    transactionDate = transactionDate,
                    personName = personName
                )
                showAddTransaction = false
            },
            onSaveSplit = { transactionName, transactionDate, splits ->
                vm.addSplitTransactions(
                    customerId = customer.id,
                    customerName = customer.name,
                    transactionName = transactionName,
                    transactionDate = transactionDate,
                    splits = splits
                )
                showAddTransaction = false
            },
            onSaveEmi = { transactionName, accountId, accountName, months, totalAmount, transactionDate, firstMonthOverride, dateOverrides ->
                vm.addEmiTransactions(
                    customerId = customer.id,
                    transactionName = transactionName,
                    customerName = customer.name,
                    accountId = accountId,
                    accountName = accountName,
                    totalAmount = totalAmount,
                    transactionDate = transactionDate,
                    months = months,
                    firstMonthOverride = firstMonthOverride,
                    dateOverrides = dateOverrides
                )
                showAddTransaction = false
            }
        )
    }

    transactionToEdit?.let { transaction ->
        TransactionEditorDialog(
            customer = customer,
            selectedAccountIds = selectedAccountIds,
            transaction = transaction,
            onDismiss = { transactionToEdit = null },
            onSave = { transactionName, accountId, accountName, accountKind, amount, transactionDate, personName ->
                vm.updateTransaction(
                    transactionId = transaction.id,
                    transactionName = transactionName,
                    accountId = accountId,
                    accountName = accountName,
                    accountKind = accountKind,
                    amount = amount,
                    transactionDate = transactionDate,
                    personName = personName
                )
                transactionToEdit = null
            },
            onSaveSplit = if (transaction.isEmi) { transactionName, transactionDate, splits ->
                vm.convertEmiInstallmentToSplit(
                    originalTransactionId = transaction.id,
                    customerId = customer.id,
                    customerName = customer.name,
                    transactionName = transactionName,
                    transactionDate = transactionDate,
                    emiGroupId = transaction.emiGroupId,
                    emiIndex = transaction.emiIndex,
                    emiTotal = transaction.emiTotal,
                    splits = splits
                )
                transactionToEdit = null
            } else null
        )
    }

    // FIX-17: Confirmation dialog before moving customer to recycle bin
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Move to Recycle Bin?") },
            text = {
                Text(
                    "\"${customer.name}\" will be moved to the Recycle Bin. " +
                    "You can restore or permanently delete them from there.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        vm.deleteCustomer(customerId = customer.id, customerName = customer.name)
                    }
                ) {
                    Text("Move to Bin", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

}

// ── Simple list row — tapping opens the detail screen ────────────────────────
@Composable
fun CustomerListRow(
    customer: CustomerSummary,
    onClick: () -> Unit
) {
    FlowCard(
        accentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initialsFor(customer.name),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        // BUG-07: Guard against blank customer names
                        text = customer.name.ifBlank { "Unnamed Customer" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${customer.transactions.filter { it.isVisibleInTransactions() }.countLogicalTransactions()} transaction(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatMoney(customer.balance),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (customer.balance > 0.0) warningColor() else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (customer.balance > 0.0) "Due" else "Settled",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (customer.savingsBalance > 0.0) {
                    Text(
                        text = "Savings ${formatMoney(customer.savingsBalance)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

// ── Full-screen customer detail ───────────────────────────────────────────────
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customerId: String,
    selectedAccountIds: Set<String>,
    vm: MainViewModel = viewModel(),
    onBack: () -> Unit,
    onOpenSavings: (String) -> Unit = {}
) {
    val customers by vm.customers.collectAsState()
    val customer = customers.find { it.id == customerId }

    if (customer == null) {
        // Customer was deleted — navigate back, show background while waiting
        LaunchedEffect(Unit) { onBack() }
        RadafiqBackground { Box(modifier = Modifier.fillMaxSize()) }
        return
    }

    var showAddTransaction by remember { mutableStateOf(false) }
    var transactionToEdit by remember { mutableStateOf<CustomerTransaction?>(null) }
    var transactionFilter by remember { mutableStateOf(TransactionTypeFilter.ALL) }
    var showShareDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Auto-reopen dialog if a draft exists for this customer (e.g. after biometric lock)
    val draft by vm.draftTransaction.collectAsState()
    LaunchedEffect(draft.customerId) {
        if (draft.customerId == customerId && !draft.isEmpty) {
            showAddTransaction = true
        }
    }

    val filteredTransactions = remember(customer.transactions, transactionFilter) {
        val predicate: ((CustomerTransaction) -> Boolean)? = when (transactionFilter) {
            TransactionTypeFilter.ALL -> null
            TransactionTypeFilter.BANK_ACCOUNT -> { t -> t.accountKind == AccountKind.BANK_ACCOUNT }
            TransactionTypeFilter.CREDIT_CARD -> { t -> t.accountKind == AccountKind.CREDIT_CARD }
            TransactionTypeFilter.PERSON -> { t -> t.accountKind == AccountKind.PERSON }
        }
        val today = LocalDate.now()
        // BUG-35: Do NOT pre-sort here — buildGroupedTransactions handles sorting by group date/total.
        // Pre-sorting by individual amount before grouping produces incorrect group order for splits.
        val visible = customer.transactions
            .filter { t -> t.isVisibleInTransactions(today) }
            .let { if (predicate == null) it else it.filter(predicate) }
        buildGroupedTransactions(visible)
    }
    val groupedTransactions = filteredTransactions

    // Account breakdown — group all visible transactions by account name+kind
    data class AccountBreakdown(
        val accountId: String,
        val accountName: String,
        val accountKind: AccountKind,
        val totalUsed: Double,
        val totalDue: Double
    )
    val accountBreakdowns = remember(customer.transactions) {
        val today = LocalDate.now()
        val visible = customer.transactions.filter { it.isVisibleInTransactions(today) }
        val map = linkedMapOf<String, AccountBreakdown>()
        visible.forEach { t ->
            val key = "${t.accountKind.name}::${t.accountId}"
            val due = if (t.isSettled) 0.0 else (t.amount - t.partialPaidAmount).coerceAtLeast(0.0)
            val existing = map[key]
            if (existing == null) {
                map[key] = AccountBreakdown(
                    accountId = t.accountId,
                    accountName = t.accountName,
                    accountKind = t.accountKind,
                    totalUsed = t.amount,
                    totalDue = due
                )
            } else {
                map[key] = existing.copy(
                    totalUsed = existing.totalUsed + t.amount,
                    totalDue = existing.totalDue + due
                )
            }
        }
        map.values.sortedWith(
            compareByDescending<AccountBreakdown> { it.totalDue }
                .thenByDescending { it.totalUsed }
        )
    }

    // ── Account-level payment state ───────────────────────────────────────────
    // Holds the breakdown row for which the payment dialog is open
    var accountPayTarget by remember { mutableStateOf<AccountBreakdown?>(null) }
    var accountPayAmount by remember { mutableStateOf("") }
    // Per-account saving flag — only the tapped row shows spinner
    var accountPaySavingId by remember { mutableStateOf<String?>(null) }

    /**
     * Distribute [paymentAmount] across all unsettled transactions for [accountId],
     * sorted oldest-first. Greedily settles transactions that are fully covered and
     * applies a partial payment to the first transaction that is only partially covered.
     *
     * Uses the *Await suspend variants so each Firestore write completes before the
     * next one starts — preventing fire-and-forget race conditions.
     * Amounts are rounded to 2 decimal places to eliminate floating-point residuals.
     */
    suspend fun applyAccountPayment(accountId: String, paymentAmount: Double) {
        val today = LocalDate.now()
        val unsettled = customer.transactions
            .filter { t ->
                t.accountId == accountId &&
                !t.isSettled &&
                t.isVisibleInTransactions(today)
            }
            .sortedBy { it.transactionDate }

        // Round to paise to eliminate floating-point residuals (e.g. 0.000000001)
        fun round(v: Double) = Math.round(v * 100).toDouble() / 100.0

        var remaining = round(paymentAmount)
        for (txn in unsettled) {
            if (remaining <= 0.0) break
            val due = round((txn.amount - txn.partialPaidAmount).coerceAtLeast(0.0))
            if (due <= 0.0) continue
            if (remaining >= due) {
                // Fully covers — await the settle write before moving on
                vm.toggleTransactionSettledAwait(txn.id, true)
                remaining = round(remaining - due)
            } else {
                // Partially covers — await the partial payment write and stop
                vm.addPartialPaymentAwait(txn.id, remaining)
                remaining = 0.0
            }
        }
    }

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
                                text = "Balance: ${formatMoney(customer.balance)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (customer.balance > 0.0) warningColor()
                                        else MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { onOpenSavings(customer.id) }) {
                            Icon(
                                imageVector = Icons.Filled.Savings,
                                contentDescription = "Savings",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { showShareDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share Statement"
                            )
                        }
                        IconButton(onClick = { showAddTransaction = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add Transaction")
                        }
                        IconButton(onClick = {
                            vm.deleteCustomer(customerId = customer.id, customerName = customer.name)
                        }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete Customer",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Summary strip
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricPill(
                            label = "Used",
                            value = formatMoney(customer.totalAmount),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricPill(
                            label = "Paid",
                            value = formatMoney(customer.creditDueAmount),
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricPill(
                            label = "Balance",
                            value = formatMoney(customer.balance),
                            color = if (customer.balance > 0.0) warningColor()
                                    else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Savings balance chip (only if there are savings)
                if (customer.savingsBalance > 0.0 || customer.savingsEntries.isNotEmpty()) {
                    item {
                        AccentValueRow(
                            label = "Savings Balance",
                            value = formatMoney(customer.savingsBalance),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Account breakdown — credit card and bank account totals
                if (accountBreakdowns.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Account Breakdown",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            accountBreakdowns.forEach { breakdown ->
                                val accent = accountAccent(breakdown.accountKind)
                                val kindLabel = when (breakdown.accountKind) {
                                    AccountKind.CREDIT_CARD -> "Credit Card"
                                    AccountKind.BANK_ACCOUNT -> "Bank Account"
                                    else -> breakdown.accountKind.label
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(accent.copy(alpha = 0.07f))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(accent)
                                            )
                                            Column {
                                                Text(
                                                    text = breakdown.accountName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = kindLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = accent
                                                )
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = formatMoney(breakdown.totalUsed),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = accent
                                            )
                                            Text(
                                                text = if (breakdown.totalDue > 0.0)
                                                    "Due ${formatMoney(breakdown.totalDue)}"
                                                else "✓ Settled",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (breakdown.totalDue > 0.0) warningColor()
                                                        else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    // Pay buttons — only show when there is an outstanding due
                                    if (breakdown.totalDue > 0.0) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                                        ) {
                                            // Partial pay button
                                            OutlinedButton(
                                                onClick = {
                                                    accountPayTarget = breakdown
                                                    accountPayAmount = ""
                                                },
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                                    horizontal = 12.dp, vertical = 4.dp
                                                ),
                                                border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                                                enabled = accountPaySavingId != breakdown.accountId
                                            ) {
                                                Text(
                                                    "Partial Pay",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = accent
                                                )
                                            }
                                            // Full payment button
                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        accountPaySavingId = breakdown.accountId
                                                        try {
                                                            applyAccountPayment(
                                                                breakdown.accountId,
                                                                breakdown.totalDue
                                                            )
                                                        } finally {
                                                            accountPaySavingId = null
                                                        }
                                                    }
                                                },
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                                    horizontal = 12.dp, vertical = 4.dp
                                                ),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = accent
                                                ),
                                                enabled = accountPaySavingId != breakdown.accountId
                                            ) {
                                                Text(
                                                    if (accountPaySavingId == breakdown.accountId) "Saving…" else "Full Payment",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Filter chips
                item {
                    TransactionFilterChips(
                        selectedFilter = transactionFilter,
                        onFilterSelected = { transactionFilter = it }
                    )
                }

                if (filteredTransactions.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyState(
                                title = "No transactions",
                                subtitle = "Tap + to add the first transaction for ${customer.name}."
                            )
                        }
                    }
                } else {
                    // Compute running balance from the filtered list's total due (not customer.balance
                    // which may include transactions excluded by the current filter)
                    val filteredDue = filteredTransactions.sumOf { group ->
                        group.splits.sumOf { split ->
                            if (split.isSettled) 0.0
                            else (split.amount - split.partialPaidAmount).coerceAtLeast(0.0)
                        }
                    }
                    val groupsWithBalance: List<Pair<TransactionGroup, Double>> = run {
                        var runningBal = filteredDue
                        filteredTransactions.map { group ->
                            val groupDue = group.splits.sumOf { split ->
                                if (split.isSettled) 0.0
                                else (split.amount - split.partialPaidAmount).coerceAtLeast(0.0)
                            }
                            val entry = group to runningBal
                            // After this group, balance was lower by the due amount of this group
                            runningBal -= groupDue
                            entry
                        }
                    }

                    // Group by date for date-header separators
                    val byDate = groupsWithBalance.groupBy { (group, _) -> group.transactionDate }
                    val sortedDates = byDate.keys.sortedDescending()
                    val today = LocalDate.now()

                    sortedDates.forEach { date ->
                        // Date header
                        item(key = "date_$date") {
                            DateSeparatorChip(date = date, today = today)
                        }
                        // Transactions under this date
                        val groupsForDate = byDate[date] ?: emptyList()
                        items(groupsForDate, key = { (group, _) -> group.key }) { (group, runningBal) ->
                            if (group.splits.size > 1) {
                                SplitTransactionRow(
                                    group = group,
                                    runningBalance = runningBal,
                                    onDeleteAll = { group.splits.forEach { vm.deleteTransaction(it.id) } },
                                    onSettledChangeAll = { settled ->
                                        group.splits.forEach { vm.toggleTransactionSettled(it.id, settled) }
                                    },
                                    onSettledChange = { txnId, settled -> vm.toggleTransactionSettled(txnId, settled) },
                                    onPartialPayment = { txnId, amount -> vm.addPartialPayment(txnId, amount) },
                                    onEditSplit = { transactionToEdit = it }
                                )
                            } else {
                                val transaction = group.splits.first()
                                TransactionRow(
                                    transaction = transaction,
                                    runningBalance = runningBal,
                                    onEdit = { transactionToEdit = transaction },
                                    onDelete = { vm.deleteTransaction(transaction.id) },
                                    onSettledChange = { vm.toggleTransactionSettled(transaction.id, it) },
                                    onPartialPayment = { amount -> vm.addPartialPayment(transaction.id, amount) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddTransaction) {
        TransactionEditorDialog(
            customer = customer,
            selectedAccountIds = selectedAccountIds,
            transaction = null,
            onDismiss = { showAddTransaction = false },
            onSave = { transactionName, accountId, accountName, accountKind, amount, transactionDate, personName ->
                vm.addTransaction(
                    customerId = customer.id,
                    transactionName = transactionName,
                    customerName = customer.name,
                    accountId = accountId,
                    accountName = accountName,
                    accountKind = accountKind,
                    amount = amount,
                    transactionDate = transactionDate,
                    personName = personName
                )
                showAddTransaction = false
            },
            onSaveSplit = { transactionName, transactionDate, splits ->
                vm.addSplitTransactions(
                    customerId = customer.id,
                    customerName = customer.name,
                    transactionName = transactionName,
                    transactionDate = transactionDate,
                    splits = splits
                )
                showAddTransaction = false
            },
            onSaveEmi = { transactionName, accountId, accountName, months, totalAmount, transactionDate, firstMonthOverride, dateOverrides ->
                vm.addEmiTransactions(
                    customerId = customer.id,
                    transactionName = transactionName,
                    customerName = customer.name,
                    accountId = accountId,
                    accountName = accountName,
                    totalAmount = totalAmount,
                    transactionDate = transactionDate,
                    months = months,
                    firstMonthOverride = firstMonthOverride,
                    dateOverrides = dateOverrides
                )
                showAddTransaction = false
            }
        )
    }

    transactionToEdit?.let { transaction ->
        TransactionEditorDialog(
            customer = customer,
            selectedAccountIds = selectedAccountIds,
            transaction = transaction,
            onDismiss = { transactionToEdit = null },
            onSave = { transactionName, accountId, accountName, accountKind, amount, transactionDate, personName ->
                vm.updateTransaction(
                    transactionId = transaction.id,
                    transactionName = transactionName,
                    accountId = accountId,
                    accountName = accountName,
                    accountKind = accountKind,
                    amount = amount,
                    transactionDate = transactionDate,
                    personName = personName
                )
                transactionToEdit = null
            },
            onSaveSplit = if (transaction.isEmi) { transactionName, transactionDate, splits ->
                vm.convertEmiInstallmentToSplit(
                    originalTransactionId = transaction.id,
                    customerId = customer.id,
                    customerName = customer.name,
                    transactionName = transactionName,
                    transactionDate = transactionDate,
                    emiGroupId = transaction.emiGroupId,
                    emiIndex = transaction.emiIndex,
                    emiTotal = transaction.emiTotal,
                    splits = splits
                )
                transactionToEdit = null
            } else null
        )
    }

    if (showShareDialog) {
        ShareStatementDialog(
            customer = customer,
            onDismiss = { showShareDialog = false }
        )
    }

    // ── Account-level payment dialog ──────────────────────────────────────────
    accountPayTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (accountPaySavingId == null) accountPayTarget = null },
            title = {
                Text(
                    text = "Pay — ${target.accountName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val enteredAmount = accountPayAmount.toDoubleOrNull()
                val exceedsOutstanding = enteredAmount != null && enteredAmount > target.totalDue
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Outstanding: ${formatMoney(target.totalDue)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = warningColor()
                    )
                    Text(
                        text = "Enter an amount to pay. The payment will be applied to the oldest unsettled transactions first, settling each one fully before moving to the next.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = accountPayAmount,
                        onValueChange = { accountPayAmount = it },
                        label = { Text("Amount") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = accountPaySavingId == null,
                        isError = exceedsOutstanding,
                        supportingText = if (exceedsOutstanding) {
                            { Text("Cannot exceed outstanding ${formatMoney(target.totalDue)}") }
                        } else null
                    )
                }
            },
            confirmButton = {
                val enteredAmount = accountPayAmount.toDoubleOrNull()
                Button(
                    onClick = {
                        val amount = enteredAmount ?: return@Button
                        if (amount <= 0.0 || amount > target.totalDue) return@Button
                        coroutineScope.launch {
                            accountPaySavingId = target.accountId
                            try {
                                applyAccountPayment(target.accountId, amount)
                            } finally {
                                accountPaySavingId = null
                                accountPayTarget = null
                            }
                        }
                    },
                    enabled = accountPaySavingId == null &&
                        enteredAmount != null &&
                        enteredAmount > 0.0 &&
                        enteredAmount <= target.totalDue
                ) {
                    Text(if (accountPaySavingId != null) "Saving…" else "Apply Payment")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { accountPayTarget = null },
                    enabled = accountPaySavingId == null
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun initialsFor(name: String): String {
    val parts = name
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)

    return parts.joinToString(separator = "") { part ->
        part.first().uppercaseChar().toString()
    }.ifBlank { "RA" }
}

@Composable
private fun CustomerSectionHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    AdaptiveHeaderRow(
        leading = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        },
        trailing = {
            TextButton(onClick = onAction) {
                Icon(Icons.Filled.Add, contentDescription = actionLabel)
                Spacer(modifier = Modifier.width(4.dp))
                Text(actionLabel)
            }
        }
    )
}

@Composable
private fun EmptyInlineState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DeletedCustomerCard(
    customer: CustomerSummary,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    FlowCard(accentColor = dangerColor()) {
        Column {
            Text(
                text = customer.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${customer.transactions.countLogicalTransactions()} transaction(s) in recycle bin",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            ResponsiveTwoPane(
                first = { itemModifier ->
                    MetricPill(
                        label = "Used",
                        value = formatMoney(customer.totalAmount),
                        color = dangerColor(),
                        modifier = itemModifier
                    )
                },
                second = { itemModifier ->
                    MetricPill(
                        label = "Balance",
                        value = formatMoney(customer.balance),
                        color = warningColor(),
                        modifier = itemModifier
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ResponsiveTwoPane(
                first = { itemModifier ->
                    OutlinedButton(
                        onClick = onRestore,
                        modifier = itemModifier
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = "Restore Customer")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restore")
                    }
                },
                second = { itemModifier ->
                    OutlinedButton(
                        onClick = onDeleteForever,
                        modifier = itemModifier
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete Forever")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete Forever")
                    }
                }
            )
        }
    }
}

@Composable
fun TransactionRow(
    transaction: CustomerTransaction,
    runningBalance: Double = -1.0,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSettledChange: (Boolean) -> Unit,
    onPartialPayment: (String) -> Unit = {}
) {
    val lineThrough = if (transaction.isSettled) TextDecoration.LineThrough else TextDecoration.None
    val contentAlpha = if (transaction.isSettled) 0.56f else 1f
    var showPartialPaymentDialog by remember { mutableStateOf(false) }
    var showSettleConfirm by remember { mutableStateOf<Boolean?>(null) } // null=hidden, true=marking paid, false=marking unpaid
    val remaining = (transaction.amount - transaction.partialPaidAmount).coerceAtLeast(0.0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        // Main row: info left, amount + actions right
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: name + meta
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = transaction.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = lineThrough,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = transaction.accountName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (transaction.accountKind == AccountKind.PERSON && transaction.personName.isNotBlank()) {
                    Text(
                        text = "From: ${transaction.personName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                        modifier = Modifier.padding(top = 1.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Running balance
                if (runningBalance >= 0.0) {
                    Text(
                        text = "Bal. ${formatMoney(runningBalance)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (runningBalance > 0.0) warningColor()
                                else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1
                    )
                }
            }

            // Right: amount + action icons
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatMoney(transaction.amount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textDecoration = lineThrough,
                    color = warningColor().copy(alpha = contentAlpha)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Checkbox(
                        checked = transaction.isSettled,
                        onCheckedChange = { newValue -> showSettleConfirm = newValue },
                        modifier = Modifier.size(32.dp)
                    )
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        if (transaction.partialPaidAmount > 0.0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Partial paid: ${formatMoney(transaction.partialPaidAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Remaining: ${formatMoney(remaining)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (remaining > 0.0) warningColor() else MaterialTheme.colorScheme.primary
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (transaction.isSettled) {
                    "Paid and cleared${transaction.settledDate.takeIf { it.isNotBlank() }?.let { " on $it" }.orEmpty()}"
                } else {
                    "Pending collection"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (transaction.isSettled) MaterialTheme.colorScheme.primary else warningColor()
            )
            if (!transaction.isSettled) {
                TextButton(
                    onClick = { showPartialPaymentDialog = true },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "+ Partial Pay",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    if (showPartialPaymentDialog) {
        PartialPaymentDialog(
            transaction = transaction,
            onDismiss = { showPartialPaymentDialog = false },
            onSave = { amount ->
                onPartialPayment(amount)
                showPartialPaymentDialog = false
            }
        )
    }

    // Settle / Unsettle confirmation dialog
    showSettleConfirm?.let { markingAsPaid ->
        AlertDialog(
            onDismissRequest = { showSettleConfirm = null },
            title = {
                Text(if (markingAsPaid) "Mark as Paid?" else "Mark as Unpaid?")
            },
            text = {
                Text(
                    if (markingAsPaid)
                        "Mark \"${transaction.name}\" as fully paid and settled?"
                    else
                        "Mark \"${transaction.name}\" as unpaid? This will restore it to outstanding."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSettleConfirm = null
                    onSettledChange(markingAsPaid)
                }) {
                    Text(
                        if (markingAsPaid) "Yes, Mark Paid" else "Yes, Mark Unpaid",
                        color = if (markingAsPaid) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettleConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PartialPaymentDialog(
    transaction: CustomerTransaction,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    val remaining = (transaction.amount - transaction.partialPaidAmount).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Partial Payment") },
        text = {
            Column {
                Text(
                    text = "Transaction: ${transaction.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Total: ${formatMoney(transaction.amount)} • Remaining: ${formatMoney(remaining)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Payment Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    leadingIcon = { Text("₹") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(amount) },
                enabled = amount.toDoubleOrNull()?.let { it > 0.0 && it <= remaining } == true
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DueAmountDialog(
    customer: CustomerSummary,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var dueAmount by remember(customer.id) {
        mutableStateOf(customer.manualPaidAmount.takeIf { it > 0.0 }?.toString().orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Adjust Manual Paid Amount",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column {
                Text(
                    text = "Use this when you want to enter a manual paid adjustment outside the transaction checkbox list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = dueAmount,
                    onValueChange = { dueAmount = it },
                    label = { Text("Manual Paid Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    leadingIcon = { Text("₹") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(dueAmount) },
                enabled = dueAmount.toDoubleOrNull() != null
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
fun TransactionEditorDialog(
    customer: CustomerSummary,
    selectedAccountIds: Set<String>,
    transaction: CustomerTransaction?,
    onDismiss: () -> Unit,
    onSave: (
        transactionName: String,
        accountId: String,
        accountName: String,
        accountKind: AccountKind,
        amount: String,
        transactionDate: String,
        personName: String
    ) -> Unit,
    onSaveSplit: ((
        transactionName: String,
        transactionDate: String,
        splits: List<SplitEntry>
    ) -> Unit)? = null,
    onSaveEmi: ((
        transactionName: String,
        accountId: String,
        accountName: String,
        months: Int,
        totalAmount: Double,
        transactionDate: String,
        firstMonthOverride: Double?,
        dateOverrides: Map<Int, String>
    ) -> Unit)? = null,
    vm: MainViewModel = viewModel()
) {
    val availableKinds = remember(selectedAccountIds, transaction?.accountKind) {
        selectedAccountKinds(selectedAccountIds, transaction?.accountKind)
    }
    val defaultKind = remember(availableKinds) {
        when {
            availableKinds.contains(AccountKind.CREDIT_CARD) -> AccountKind.CREDIT_CARD
            availableKinds.isNotEmpty() -> availableKinds.first()
            else -> AccountKind.CREDIT_CARD
        }
    }

    // Load draft from ViewModel if it belongs to this customer and this is a new transaction
    val draft by vm.draftTransaction.collectAsState()
    val hasDraft = transaction == null && draft.customerId == customer.id && !draft.isEmpty

    // Compute "frequently used" account IDs from ALL customers' transaction history
    val allCustomers by vm.customers.collectAsState()
    val frequentAccountIds = remember(allCustomers) {
        val freq = mutableMapOf<String, Int>()
        for (c in allCustomers) {
            for (t in c.transactions) {
                freq[t.accountId] = (freq[t.accountId] ?: 0) + 1
            }
        }
        freq.filter { it.value > 0 }.keys
    }

    var transactionName by remember(transaction?.id) {
        mutableStateOf(if (hasDraft) draft.transactionName else transaction?.name.orEmpty())
    }
    var selectedKind by remember(transaction?.id, availableKinds) {
        mutableStateOf(
            if (hasDraft) AccountKind.values().firstOrNull { it.name == draft.selectedKindName } ?: defaultKind
            else transaction?.accountKind ?: defaultKind
        )
    }
    var selectedAccountId by remember(transaction?.id) {
        mutableStateOf(if (hasDraft) draft.selectedAccountId else transaction?.accountId.orEmpty())
    }
    var personName by remember(transaction?.id) {
        mutableStateOf(if (hasDraft) draft.personName else transaction?.personName.orEmpty())
    }
    var amountExpression by remember(transaction?.id) {
        mutableStateOf(
            if (hasDraft) draft.amountExpression
            else transaction?.amount?.takeIf { it > 0.0 }?.toString().orEmpty()
        )
    }
    var transactionDate by remember(transaction?.id) {
        mutableStateOf(
            if (hasDraft) draft.transactionDate
            else transaction?.transactionDate?.ifBlank { LocalDate.now().toString() }
                ?: LocalDate.now().toString()
        )
    }

    // EMI state — only for new credit card transactions
    var emiEnabled by remember { mutableStateOf(if (hasDraft) draft.emiEnabled else false) }
    var emiMonths by remember { mutableStateOf(if (hasDraft) draft.emiMonths else "") }
    var emiFirstMonthOverride by remember { mutableStateOf(if (hasDraft) draft.emiFirstMonthOverride else "") }
    var emiManualDates by remember { mutableStateOf(if (hasDraft) draft.emiManualDates else false) }
    var emiDateOverrides by remember { mutableStateOf(if (hasDraft) draft.emiDateOverrides else mapOf<Int, String>()) }

    // Split state — only for new transactions
    var splitEnabled by remember { mutableStateOf(if (hasDraft) draft.splitEnabled else false) }
    var splitEntries by remember {
        mutableStateOf(if (hasDraft && draft.splitEntries.isNotEmpty()) draft.splitEntries else listOf(SplitEntry(), SplitEntry()))
    }

    // EMI-to-split conversion state — only when editing an existing EMI installment
    val isEditingEmi = transaction != null && transaction.isEmi
    var emiSplitEnabled by remember(transaction?.id) { mutableStateOf(false) }
    var emiSplitEntries by remember(transaction?.id) {
        mutableStateOf(
            listOf(
                SplitEntry(
                    accountKind = transaction?.accountKind ?: AccountKind.CREDIT_CARD,
                    accountId = transaction?.accountId.orEmpty(),
                    accountName = transaction?.accountName.orEmpty(),
                    personName = transaction?.personName.orEmpty(),
                    amount = transaction?.amount?.toString().orEmpty()
                ),
                SplitEntry()
            )
        )
    }

    // Persist draft to ViewModel on every state change (new transactions only)
    if (transaction == null) {
        LaunchedEffect(
            transactionName, selectedKind, selectedAccountId, personName,
            amountExpression, transactionDate, splitEnabled, splitEntries,
            emiEnabled, emiMonths, emiFirstMonthOverride, emiManualDates, emiDateOverrides
        ) {
            vm.saveDraftTransaction(
                DraftTransactionState(
                    customerId = customer.id,
                    transactionName = transactionName,
                    selectedKindName = selectedKind.name,
                    selectedAccountId = selectedAccountId,
                    personName = personName,
                    amountExpression = amountExpression,
                    transactionDate = transactionDate,
                    splitEnabled = splitEnabled,
                    splitEntries = splitEntries,
                    emiEnabled = emiEnabled,
                    emiMonths = emiMonths,
                    emiFirstMonthOverride = emiFirstMonthOverride,
                    emiManualDates = emiManualDates,
                    emiDateOverrides = emiDateOverrides
                )
            )
        }
    }

    val isNewCreditCard = transaction == null && selectedKind == AccountKind.CREDIT_CARD
    val calculatedAmount = remember(amountExpression) { evaluateAmountExpression(amountExpression) }
    val emiMonthsInt = emiMonths.toIntOrNull()?.takeIf { it in 2..120 }
    val baseEmi = if (calculatedAmount != null && emiMonthsInt != null) calculatedAmount / emiMonthsInt else null
    val firstOverride = emiFirstMonthOverride.toDoubleOrNull()
        ?.takeIf { it > 0.0 }

    val accountOptions = remember(selectedKind, selectedAccountIds, transaction?.accountId, customer.transactions) {
        selectedAccountOptions(
            accountKind = selectedKind,
            selectedAccountIds = selectedAccountIds,
            pinnedAccountId = transaction?.accountId,
            transactions = customer.transactions
        )
    }
    val selectedAccount = accountOptions.firstOrNull { it.id == selectedAccountId }
        ?: accountOptions.firstOrNull()

    LaunchedEffect(availableKinds) {
        if (selectedKind !in availableKinds && availableKinds.isNotEmpty()) selectedKind = defaultKind
    }
    LaunchedEffect(selectedKind, accountOptions) {
        if (accountOptions.isNotEmpty() && selectedAccountId !in accountOptions.map { it.id }) {
            selectedAccountId = accountOptions.first().id
        }
        if (selectedKind != AccountKind.CREDIT_CARD) emiEnabled = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (transaction == null) "Add Transaction" else "Edit Transaction",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                OutlinedTextField(
                    value = transactionName,
                    onValueChange = { transactionName = it },
                    label = { Text("Transaction Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )

                var showDatePicker by remember { mutableStateOf(false) }

                OutlinedTextField(
                    value = formatDisplayDate(transactionDate),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Transaction Date") },
                    placeholder = { Text("dd/mm/yyyy") },
                    trailingIcon = {
                        androidx.compose.material3.IconButton(onClick = { showDatePicker = true }) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.DateRange,
                                contentDescription = "Pick date"
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clickable { showDatePicker = true }
                )

                if (showDatePicker) {
                    DatePickerDialog(
                        initialDateIso = transactionDate,
                        onDateSelected = { iso ->
                            transactionDate = iso
                            showDatePicker = false
                        },
                        onDismiss = { showDatePicker = false }
                    )
                }

                // ── Split toggle (new transactions only) ──────────────────
                if (transaction == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Split across accounts",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Divide total across multiple accounts/persons",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = splitEnabled,
                            onCheckedChange = { splitEnabled = it; emiEnabled = false }
                        )
                    }
                }

                if (splitEnabled && transaction == null) {
                    // Split rows
                    splitEntries.forEachIndexed { index, entry ->
                        SplitEntryRow(
                            index = index,
                            entry = entry,
                            selectedAccountIds = selectedAccountIds,
                            transactions = customer.transactions,
                            frequentAccountIds = frequentAccountIds,
                            onEntryChange = { updated ->
                                splitEntries = splitEntries.toMutableList().also { it[index] = updated }
                            },
                            onRemove = if (splitEntries.size > 2) ({
                                splitEntries = splitEntries.toMutableList().also { it.removeAt(index) }
                            }) else null
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    // Split total — just show the sum, no over-allocation warning
                    // (in split mode the entries define the total; there's no pre-set budget)
                    val splitTotal = splitEntries.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
                    if (splitTotal > 0.0) {
                        Text(
                            text = "Split Total: ${formatMoney(splitTotal)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    TextButton(
                        onClick = {
                            splitEntries = splitEntries + SplitEntry()
                        },
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add another account")
                    }
                } else if (!splitEnabled) {
                    if (availableKinds.isEmpty() || (selectedKind != AccountKind.PERSON && selectedAccount == null)) {
                        Text(
                            text = "No accounts are enabled in Settings. Select at least one bank or credit card there to add transactions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    } else {
                        AccountKindDropdown(
                            options = availableKinds,
                            selectedKind = selectedKind,
                            onKindSelected = { selectedKind = it },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        )

                        if (selectedKind == AccountKind.PERSON) {
                            OutlinedTextField(
                                value = personName,
                                onValueChange = { personName = it },
                                label = { Text("Person Name") },
                                placeholder = { Text("Who gave you the money?") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            )
                        } else {
                            val account = selectedAccount
                            if (account != null) {
                                AccountOptionDropdown(
                                    label = if (selectedKind == AccountKind.BANK_ACCOUNT) "Bank Account" else "Credit Card",
                                    selectedOption = account,
                                    options = accountOptions,
                                    onOptionSelected = { selectedAccountId = it.id },
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                    frequentAccountIds = frequentAccountIds
                                )
                            }
                        }
                    }
                }

                // ── EMI installment split toggle ──────────────────────────────
                // Only shown when editing an existing EMI transaction
                if (isEditingEmi && onSaveSplit != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Split this installment",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Distribute EMI ${transaction!!.emiIndex + 1}/${transaction.emiTotal} across multiple accounts or persons",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = emiSplitEnabled,
                            onCheckedChange = { emiSplitEnabled = it }
                        )
                    }

                    if (emiSplitEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        emiSplitEntries.forEachIndexed { index, entry ->
                            SplitEntryRow(
                                index = index,
                                entry = entry,
                                selectedAccountIds = selectedAccountIds,
                                transactions = customer.transactions,
                                frequentAccountIds = frequentAccountIds,
                                onEntryChange = { updated ->
                                    emiSplitEntries = emiSplitEntries.toMutableList().also { it[index] = updated }
                                },
                                onRemove = if (emiSplitEntries.size > 1) ({
                                    emiSplitEntries = emiSplitEntries.toMutableList().also { it.removeAt(index) }
                                }) else null
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        val emiSplitTotal = emiSplitEntries.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
                        val emiOriginal = transaction!!.amount
                        Text(
                            text = "Split Total: ${formatMoney(emiSplitTotal)} / ${formatMoney(emiOriginal)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (kotlin.math.abs(emiSplitTotal - emiOriginal) < 0.01)
                                MaterialTheme.colorScheme.primary else warningColor(),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        TextButton(
                            onClick = { emiSplitEntries = emiSplitEntries + SplitEntry() },
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add another account")
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 18.dp, vertical = 20.dp)
                ) {
                    val splitTotal = if (splitEnabled && transaction == null)
                        splitEntries.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
                    else null

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (splitEnabled && transaction == null) "Split Total" else "Amount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (splitTotal != null) formatMoney(splitTotal)
                                   else if (amountExpression.isBlank()) "₹0" else "₹$amountExpression",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (splitTotal != null) {
                            Text(
                                text = "${splitEntries.count { (it.amount.toDoubleOrNull() ?: 0.0) > 0.0 }} of ${splitEntries.size} entries filled",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                if (!splitEnabled || transaction != null) {
                    Text(
                        text = if (calculatedAmount != null) "= ${formatMoney(calculatedAmount)}"
                        else "Enter an amount or arithmetic expression",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                    )

                    CalculatorPad(
                        expression = amountExpression,
                        onExpressionChange = { amountExpression = it },
                        calculatedAmount = calculatedAmount
                    )
                }

                // ── EMI section (credit card + new transaction only) ──
                if (isNewCreditCard && onSaveEmi != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Split into EMIs",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Credit card only",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = emiEnabled, onCheckedChange = { emiEnabled = it })
                    }

                    if (emiEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = emiMonths,
                            onValueChange = {
                                emiMonths = it.filter { c -> c.isDigit() }
                                emiDateOverrides = mapOf() // reset overrides when months change
                            },
                            label = { Text("Number of Months") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (baseEmi != null) {
                            Text(
                                text = "Each EMI: ${formatMoney(baseEmi)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = emiFirstMonthOverride,
                                onValueChange = { emiFirstMonthOverride = it },
                                label = { Text("First Month Amount (optional)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                placeholder = { Text(formatMoney(baseEmi)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (firstOverride != null && emiMonthsInt != null) {
                                val totalBilled = firstOverride + baseEmi * (emiMonthsInt - 1)
                                Text(
                                    text = "Month 1: ${formatMoney(firstOverride)} • Months 2–$emiMonthsInt: ${formatMoney(baseEmi)} each • Total billed: ${formatMoney(totalBilled)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Auto vs Manual date toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Set dates manually",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (emiManualDates) "Pick each EMI date" else "Auto: same date every month",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = emiManualDates,
                                    onCheckedChange = {
                                        emiManualDates = it
                                        emiDateOverrides = mapOf()
                                    }
                                )
                            }

                            // Per-EMI date pickers when manual
                            if (emiManualDates && emiMonthsInt != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val baseDate = runCatching {
                                    java.time.LocalDate.parse(transactionDate)
                                }.getOrDefault(java.time.LocalDate.now())

                                var showEmiDatePickerIndex by remember { mutableStateOf<Int?>(null) }

                                repeat(emiMonthsInt) { i ->
                                    val autoDate = baseDate.plusMonths(i.toLong()).toString()
                                    val displayDate = emiDateOverrides[i] ?: autoDate
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "EMI ${i + 1}/$emiMonthsInt",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.width(72.dp)
                                        )
                                        OutlinedTextField(
                                            value = formatDisplayDate(displayDate),
                                            onValueChange = {},
                                            readOnly = true,
                                            singleLine = true,
                                            trailingIcon = {
                                                androidx.compose.material3.IconButton(
                                                    onClick = { showEmiDatePickerIndex = i }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.DateRange,
                                                        contentDescription = "Pick date"
                                                    )
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { showEmiDatePickerIndex = i }
                                        )
                                    }

                                    if (showEmiDatePickerIndex == i) {
                                        DatePickerDialog(
                                            initialDateIso = displayDate,
                                            onDateSelected = { iso ->
                                                emiDateOverrides = emiDateOverrides + (i to iso)
                                                showEmiDatePickerIndex = null
                                            },
                                            onDismiss = { showEmiDatePickerIndex = null }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // EMI installment being converted to split
                    if (isEditingEmi && emiSplitEnabled && onSaveSplit != null) {
                        onSaveSplit.invoke(transactionName, transactionDate, emiSplitEntries)
                        onDismiss()
                        return@TextButton
                    }
                    if (splitEnabled && transaction == null) {
                        // Save as split transactions — amount comes from split entries
                        onSaveSplit?.invoke(transactionName, transactionDate, splitEntries)
                        vm.clearDraftTransaction()
                        onDismiss()
                        return@TextButton
                    }
                    val amount = calculatedAmount ?: return@TextButton
                    val isPerson = selectedKind == AccountKind.PERSON
                    val activeAccount = if (isPerson) null else (selectedAccount ?: return@TextButton)
                    val resolvedAccountId   = if (isPerson) "person_${personName.trim().lowercase().replace(" ", "_")}" else activeAccount!!.id
                    val resolvedAccountName = if (isPerson) personName.trim() else activeAccount!!.name

                    if (emiEnabled && emiMonthsInt != null && onSaveEmi != null) {
                        onSaveEmi(
                            transactionName,
                            resolvedAccountId,
                            resolvedAccountName,
                            emiMonthsInt,
                            amount,
                            transactionDate,
                            firstOverride,
                            if (emiManualDates) emiDateOverrides else mapOf()
                        )
                    } else {
                        onSave(
                            transactionName,
                            resolvedAccountId,
                            resolvedAccountName,
                            selectedKind,
                            amount.toString(),
                            transactionDate,
                            if (isPerson) personName.trim() else ""
                        )
                    }
                    vm.clearDraftTransaction()
                    onDismiss()
                },
                enabled = transactionName.isNotBlank() &&
                    transactionDate.isNotBlank() &&
                    (isEditingEmi && emiSplitEnabled && onSaveSplit != null &&
                        emiSplitEntries.count { (it.amount.toDoubleOrNull() ?: 0.0) > 0.0 } >= 1 ||
                     splitEnabled && transaction == null && onSaveSplit != null &&
                        splitEntries.count { (it.amount.toDoubleOrNull() ?: 0.0) > 0.0 } >= 2 ||
                     !splitEnabled && !(isEditingEmi && emiSplitEnabled) && calculatedAmount != null &&
                        (selectedKind == AccountKind.PERSON && personName.isNotBlank() ||
                         selectedKind != AccountKind.PERSON && selectedAccount != null)) &&
                    (!emiEnabled || emiMonthsInt != null)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                vm.clearDraftTransaction()
                onDismiss()
            }) { Text("Cancel") }
        }
    )
}

private fun selectedAccountKinds(
    selectedAccountIds: Set<String>,
    pinnedKind: AccountKind?
): List<AccountKind> {
    val selectedKinds = IndianAccountCatalog.availableKinds(selectedAccountIds).toMutableList()
    if (pinnedKind != null && pinnedKind !in selectedKinds) {
        selectedKinds.add(pinnedKind)
    }
    // PERSON is always available — no catalog entry needed
    if (AccountKind.PERSON !in selectedKinds) selectedKinds.add(AccountKind.PERSON)
    return AccountKind.values().filter { it in selectedKinds }
}

private fun selectedAccountOptions(
    accountKind: AccountKind,
    selectedAccountIds: Set<String>,
    pinnedAccountId: String? = null,
    transactions: List<CustomerTransaction>? = null
): List<AccountOption> {
    val options = IndianAccountCatalog.optionsFor(accountKind, selectedAccountIds).toMutableList()
    val pinnedOption = pinnedAccountId?.let(IndianAccountCatalog::optionById)

    if (
        pinnedOption != null &&
        pinnedOption.accountKind == accountKind &&
        options.none { it.id == pinnedOption.id }
    ) {
        options.add(pinnedOption)
    }

    if (transactions != null) {
        val freq = transactions
            .filter { it.accountKind == accountKind }
            .groupingBy { it.accountId }
            .eachCount()
        options.sortByDescending { freq[it.id] ?: 0 }
    }

    return options
}

@Composable
private fun CalculatorPad(
    expression: String,
    onExpressionChange: (String) -> Unit,
    calculatedAmount: Double?
) {
    val rows = listOf(
        listOf("7", "8", "9", "/"),
        listOf("4", "5", "6", "*"),
        listOf("1", "2", "3", "-"),
        listOf("0", ".", "=", "+"),
        listOf("CLR", "DEL", "(", ")")
    )

    rows.forEach { row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            row.forEach { label ->
                Button(
                    onClick = {
                        when (label) {
                            "CLR" -> onExpressionChange("")
                            "DEL" -> onExpressionChange(expression.dropLast(1))
                            "=" -> {
                                if (calculatedAmount != null) {
                                    onExpressionChange(trimAmount(calculatedAmount))
                                }
                            }
                            else -> onExpressionChange(expression + label)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.48f)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 2.dp)
                ) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun TransactionFilterChips(
    selectedFilter: TransactionTypeFilter,
    onFilterSelected: (TransactionTypeFilter) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = selectedFilter.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selectedFilter == TransactionTypeFilter.ALL)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Filter",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            TransactionTypeFilter.values().forEach { filter ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = filter.label,
                            color = if (filter == selectedFilter)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onFilterSelected(filter)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ── Split transaction grouping ────────────────────────────────────────────────

@Composable
private fun DateSeparatorChip(date: String, today: java.time.LocalDate) {
    val label = remember(date, today) {
        runCatching {
            val d = java.time.LocalDate.parse(date)
            val formatted = d.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yy"))
            when (d) {
                today -> "$formatted • Today"
                today.minusDays(1) -> "$formatted • Yesterday"
                else -> formatted
            }
        }.getOrDefault(date)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(999.dp))
                .padding(horizontal = 14.dp, vertical = 5.dp)
        )
    }
}

data class TransactionGroup(
    val key: String,                          // splitGroupId or transaction.id
    val name: String,
    val transactionDate: String,
    val splits: List<CustomerTransaction>
)

private fun buildGroupedTransactions(
    transactions: List<CustomerTransaction>,
    filter: ((CustomerTransaction) -> Boolean)? = null
): List<TransactionGroup> {
    val result = mutableListOf<TransactionGroup>()
    val splitMap = linkedMapOf<String, MutableList<CustomerTransaction>>()

    transactions.forEach { t ->
        if (t.splitGroupId.isNotBlank()) {
            splitMap.getOrPut(t.splitGroupId) { mutableListOf() }.add(t)
        } else {
            result.add(TransactionGroup(key = t.id, name = t.name, transactionDate = t.transactionDate, splits = listOf(t)))
        }
    }

    splitMap.forEach { (groupId, splits) ->
        val first = splits.first()
        // Use the earliest date among splits so the group date is stable regardless of insertion order
        val groupDate = splits.minOf { it.transactionDate }
        result.add(TransactionGroup(key = groupId, name = first.name, transactionDate = groupDate, splits = splits))
    }

    val grouped = result.sortedWith(
        compareByDescending<TransactionGroup> { it.transactionDate }
            .thenByDescending { it.splits.sumOf { s -> s.amount } }
    )

    // Apply filter: for split groups, keep the group if ANY split matches; for singles, apply directly
    return if (filter == null) grouped
    else grouped.filter { group ->
        group.splits.any { filter(it) }
    }
}

@Composable
private fun SplitTransactionRow(
    group: TransactionGroup,
    runningBalance: Double = -1.0,
    onDeleteAll: () -> Unit,
    onSettledChangeAll: (Boolean) -> Unit,
    onSettledChange: (transactionId: String, settled: Boolean) -> Unit = { _, _ -> },
    onPartialPayment: (transactionId: String, amount: String) -> Unit = { _, _ -> },
    onEditSplit: ((CustomerTransaction) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf(false) }
    val total = group.splits.sumOf { it.amount }
    val totalDue = group.splits.sumOf { split ->
        if (split.isSettled) 0.0 else (split.amount - split.partialPaidAmount).coerceAtLeast(0.0)
    }
    val allSettled = group.splits.all { it.isSettled }
    val lineThrough = if (allSettled) TextDecoration.LineThrough else TextDecoration.None
    val contentAlpha = if (allSettled) 0.56f else 1f
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSettleConfirm by remember { mutableStateOf<Boolean?>(null) } // null=hidden, true=marking paid, false=marking unpaid

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .clickable { showDetailDialog = true }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Split",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = lineThrough,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${group.splits.size} accounts • ${group.transactionDate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (runningBalance >= 0.0) {
                    Text(
                        text = "Bal. ${formatMoney(runningBalance)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (runningBalance > 0.0) warningColor()
                                else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatMoney(total),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textDecoration = lineThrough,
                        color = warningColor().copy(alpha = contentAlpha)
                    )
                    if (!allSettled) {
                        Text(
                            text = "Due ${formatMoney(totalDue)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = warningColor(),
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                }
                Checkbox(
                    checked = allSettled,
                    onCheckedChange = { newValue -> showSettleConfirm = newValue },
                    modifier = Modifier.padding(start = 4.dp)
                )
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (onEditSplit != null) {
                    IconButton(onClick = { showDetailDialog = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit split",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete all splits",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Expanded split breakdown
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            group.splits.forEach { split ->
                val splitDue = if (split.isSettled) 0.0
                               else (split.amount - split.partialPaidAmount).coerceAtLeast(0.0)
                val accent = accountAccent(split.accountKind)
                val displayName = if (split.accountKind == AccountKind.PERSON && split.personName.isNotBlank())
                    split.personName else split.accountName
                var showPartialDialog by remember(split.id) { mutableStateOf(false) }
                var showSplitSettleConfirm by remember(split.id) { mutableStateOf<Boolean?>(null) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(accent)
                            )
                            Column {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = split.accountKind.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = formatMoney(split.amount),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                            Text(
                                text = if (split.isSettled) "✓ Settled"
                                       else if (split.partialPaidAmount > 0.0) "Partial • Due ${formatMoney(splitDue)}"
                                       else "Due ${formatMoney(splitDue)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (split.isSettled) MaterialTheme.colorScheme.primary else warningColor()
                            )
                        }
                    }
                    // Per-entry action buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Mark Paid / Mark Unpaid
                        OutlinedButton(
                            onClick = { showSplitSettleConfirm = !split.isSettled },
                            modifier = Modifier.height(28.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (split.isSettled) MaterialTheme.colorScheme.primary else warningColor()
                            ),
                            border = BorderStroke(1.dp, if (split.isSettled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else warningColor().copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = if (split.isSettled) "✓ Paid" else "Mark Paid",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        // Partial payment (only if not settled)
                        if (!split.isSettled) {
                            OutlinedButton(
                                onClick = { showPartialDialog = true },
                                modifier = Modifier.height(28.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.secondary
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                            ) {
                                Text("+ Partial", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        // Edit button (opens the transaction editor for this split entry)
                        if (onEditSplit != null) {
                            OutlinedButton(
                                onClick = { onEditSplit(split) },
                                modifier = Modifier.height(28.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            ) {
                                Text("Edit", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // Per-split settle confirmation
                showSplitSettleConfirm?.let { markingAsPaid ->
                    AlertDialog(
                        onDismissRequest = { showSplitSettleConfirm = null },
                        title = { Text(if (markingAsPaid) "Mark as Paid?" else "Mark as Unpaid?") },
                        text = {
                            Text(
                                if (markingAsPaid)
                                    "Mark \"$displayName\" (${formatMoney(split.amount)}) as fully paid?"
                                else
                                    "Mark \"$displayName\" as unpaid? This will restore it to outstanding."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showSplitSettleConfirm = null
                                onSettledChange(split.id, markingAsPaid)
                            }) {
                                Text(
                                    if (markingAsPaid) "Yes, Mark Paid" else "Yes, Mark Unpaid",
                                    color = if (markingAsPaid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSplitSettleConfirm = null }) { Text("Cancel") }
                        }
                    )
                }

                // Per-split partial payment dialog
                if (showPartialDialog) {
                    PartialPaymentDialog(
                        transaction = split,
                        onDismiss = { showPartialDialog = false },
                        onSave = { amount ->
                            onPartialPayment(split.id, amount)
                            showPartialDialog = false
                        }
                    )
                }
            }
        }
    }

    if (showDetailDialog) {
        SplitDetailDialog(
            group = group,
            onDismiss = { showDetailDialog = false },
            onEditSplit = onEditSplit,
            onSettledChange = onSettledChange,
            onPartialPayment = onPartialPayment
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete split transaction?") },
            text = { Text("This will delete all ${group.splits.size} split entries for \"${group.name}\". Cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { onDeleteAll(); showDeleteConfirm = false }) {
                    Text("Delete all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // Settle / Unsettle confirmation dialog for split group
    showSettleConfirm?.let { markingAsPaid ->
        AlertDialog(
            onDismissRequest = { showSettleConfirm = null },
            title = {
                Text(if (markingAsPaid) "Mark all as Paid?" else "Mark all as Unpaid?")
            },
            text = {
                Text(
                    if (markingAsPaid)
                        "Mark all ${group.splits.size} entries of \"${group.name}\" as fully paid and settled?"
                    else
                        "Mark all ${group.splits.size} entries of \"${group.name}\" as unpaid? This will restore them to outstanding."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSettleConfirm = null
                    onSettledChangeAll(markingAsPaid)
                }) {
                    Text(
                        if (markingAsPaid) "Yes, Mark Paid" else "Yes, Mark Unpaid",
                        color = if (markingAsPaid) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettleConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SplitDetailDialog(
    group: TransactionGroup,
    onDismiss: () -> Unit,
    onEditSplit: ((CustomerTransaction) -> Unit)? = null,
    onSettledChange: (transactionId: String, settled: Boolean) -> Unit = { _, _ -> },
    onPartialPayment: (transactionId: String, amount: String) -> Unit = { _, _ -> }
) {
    val total = group.splits.sumOf { it.amount }
    val totalPaid = group.splits.sumOf { split ->
        if (split.isSettled) split.amount else split.partialPaidAmount
    }
    val totalDue = (total - totalPaid).coerceAtLeast(0.0)
    val allSettled = group.splits.all { it.isSettled }
    val settledCount = group.splits.count { it.isSettled }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Split",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = group.transactionDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Total due — prominent hero row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (allSettled) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            else warningColor().copy(alpha = 0.08f)
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Total Due",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatMoney(totalDue),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (allSettled) MaterialTheme.colorScheme.primary else warningColor()
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Total Amount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatMoney(total),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Summary strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f))
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Paid", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatMoney(totalPaid), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Parts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${group.splits.size}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (allSettled) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                else warningColor().copy(alpha = 0.08f)
                            )
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Settled", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "$settledCount/${group.splits.size}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (allSettled) MaterialTheme.colorScheme.primary else warningColor()
                        )
                    }
                }

                // Per-split detail rows
                group.splits.forEach { split ->
                    val accent = accountAccent(split.accountKind)
                    val displayName = if (split.accountKind == AccountKind.PERSON && split.personName.isNotBlank())
                        split.personName else split.accountName
                    val splitDue = if (split.isSettled) 0.0
                                   else (split.amount - split.partialPaidAmount).coerceAtLeast(0.0)
                    var showSplitSettleConfirm by remember(split.id) { mutableStateOf<Boolean?>(null) }
                    var showPartialDialog by remember(split.id) { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
                            .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(accent)
                                )
                                Column {
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = split.accountKind.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accent
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatMoney(split.amount),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = accent
                                )
                                Text(
                                    text = if (split.isSettled) "Settled"
                                           else if (split.partialPaidAmount > 0.0) "Partial • Due ${formatMoney(splitDue)}"
                                           else "Due ${formatMoney(splitDue)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (split.isSettled) MaterialTheme.colorScheme.primary
                                            else warningColor()
                                )
                                if (onEditSplit != null) {
                                    TextButton(
                                        onClick = {
                                            onDismiss()
                                            onEditSplit(split)
                                        },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text(
                                            "Edit",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                        if (split.partialPaidAmount > 0.0 && !split.isSettled) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Partial paid: ${formatMoney(split.partialPaidAmount)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "Remaining: ${formatMoney(splitDue)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = warningColor()
                                )
                            }
                        }
                        // Per-entry action buttons
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showSplitSettleConfirm = !split.isSettled },
                                modifier = Modifier.height(30.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (split.isSettled) MaterialTheme.colorScheme.primary else warningColor()
                                ),
                                border = BorderStroke(1.dp, if (split.isSettled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else warningColor().copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = if (split.isSettled) "✓ Paid" else "Mark Paid",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (!split.isSettled) {
                                OutlinedButton(
                                    onClick = { showPartialDialog = true },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.secondary
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                                ) {
                                    Text("+ Partial", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    // Per-split settle confirmation
                    showSplitSettleConfirm?.let { markingAsPaid ->
                        AlertDialog(
                            onDismissRequest = { showSplitSettleConfirm = null },
                            title = { Text(if (markingAsPaid) "Mark as Paid?" else "Mark as Unpaid?") },
                            text = {
                                Text(
                                    if (markingAsPaid)
                                        "Mark \"$displayName\" (${formatMoney(split.amount)}) as fully paid?"
                                    else
                                        "Mark \"$displayName\" as unpaid? This will restore it to outstanding."
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showSplitSettleConfirm = null
                                    onSettledChange(split.id, markingAsPaid)
                                }) {
                                    Text(
                                        if (markingAsPaid) "Yes, Mark Paid" else "Yes, Mark Unpaid",
                                        color = if (markingAsPaid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showSplitSettleConfirm = null }) { Text("Cancel") }
                            }
                        )
                    }

                    if (showPartialDialog) {
                        PartialPaymentDialog(
                            transaction = split,
                            onDismiss = { showPartialDialog = false },
                            onSave = { amount ->
                                onPartialPayment(split.id, amount)
                                showPartialDialog = false
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun SplitEntryRow(
    index: Int,
    entry: SplitEntry,
    selectedAccountIds: Set<String>,
    transactions: List<CustomerTransaction> = emptyList(),
    frequentAccountIds: Set<String> = emptySet(),
    onEntryChange: (SplitEntry) -> Unit,
    onRemove: (() -> Unit)?
) {
    val availableKinds = remember(selectedAccountIds) {
        selectedAccountKinds(selectedAccountIds, null)
    }
    val accountOptions = remember(entry.accountKind, selectedAccountIds, transactions) {
        selectedAccountOptions(
            entry.accountKind, selectedAccountIds,
            pinnedAccountId = entry.accountId.ifBlank { null },
            transactions = transactions
        )
    }
    val selectedAccount = accountOptions.firstOrNull { it.id == entry.accountId }
        ?: accountOptions.firstOrNull()

    // Auto-sync the displayed account into entry state so accountId is never blank on save
    LaunchedEffect(selectedAccount?.id, entry.accountId) {
        if (entry.accountKind != AccountKind.PERSON &&
            selectedAccount != null &&
            entry.accountId != selectedAccount.id
        ) {
            onEntryChange(entry.copy(accountId = selectedAccount.id, accountName = selectedAccount.name))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Split ${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        AccountKindDropdown(
            options = availableKinds,
            selectedKind = entry.accountKind,
            onKindSelected = { kind ->
                onEntryChange(entry.copy(accountKind = kind, accountId = "", accountName = "", personName = ""))
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        if (entry.accountKind == AccountKind.PERSON) {
            OutlinedTextField(
                value = entry.personName,
                onValueChange = { onEntryChange(entry.copy(personName = it, accountName = it)) },
                label = { Text("Person Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        } else {
            val acct = selectedAccount
            if (acct != null) {
                AccountOptionDropdown(
                    label = if (entry.accountKind == AccountKind.BANK_ACCOUNT) "Bank Account" else "Credit Card",
                    selectedOption = acct,
                    options = accountOptions,
                    onOptionSelected = { opt ->
                        onEntryChange(entry.copy(accountId = opt.id, accountName = opt.name))
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    frequentAccountIds = frequentAccountIds
                )
            }
        }

        OutlinedTextField(
            value = entry.amount,
            onValueChange = { onEntryChange(entry.copy(amount = it)) },
            label = { Text("Amount") },
            leadingIcon = { Text("₹") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionTypeDropdown(
    selectedFilter: TransactionTypeFilter,
    onFilterSelected: (TransactionTypeFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedFilter.label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Transaction Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            TransactionTypeFilter.values().forEach { filter ->
                DropdownMenuItem(
                    text = { Text(filter.label) },
                    onClick = {
                        expanded = false
                        onFilterSelected(filter)
                    }
                )
            }
        }
    }
}

private fun trimAmount(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        value.toString()
    }
}

private fun formatMonthYear(isoDate: String): String {
    return runCatching {
        java.time.LocalDate.parse(isoDate).format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy"))
    }.getOrDefault(isoDate)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialog(
    initialDateIso: String,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialMillis = remember(initialDateIso) {
        runCatching {
            java.time.LocalDate.parse(initialDateIso)
                .atStartOfDay(java.time.ZoneOffset.UTC)
                .toInstant().toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())
    }
    val state = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = initialMillis
    )

    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) {
                    val iso = java.time.Instant.ofEpochMilli(millis)
                        .atZone(java.time.ZoneOffset.UTC)
                        .toLocalDate()
                        .toString()
                    onDateSelected(iso)
                } else onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        androidx.compose.material3.DatePicker(state = state)
    }
}

private fun evaluateAmountExpression(expression: String): Double? {
    val parser = AmountExpressionParser(expression)
    return parser.parse()
}

private class AmountExpressionParser(
    private val source: String
) {
    private var index = 0

    fun parse(): Double? {
        return try {
            val value = parseExpression()
            skipSpaces()
            if (index == source.length && value.isFinite()) value else null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun parseExpression(): Double {
        var value = parseTerm()
        while (true) {
            skipSpaces()
            value = when {
                consume('+') -> value + parseTerm()
                consume('-') -> value - parseTerm()
                else -> return value
            }
        }
    }

    private fun parseTerm(): Double {
        var value = parseFactor()
        while (true) {
            skipSpaces()
            value = when {
                consume('*') -> value * parseFactor()
                consume('/') -> {
                    val divisor = parseFactor()
                    if (divisor == 0.0) throw IllegalArgumentException()
                    value / divisor
                }
                else -> return value
            }
        }
    }

    private fun parseFactor(): Double {
        skipSpaces()
        if (consume('+')) return parseFactor()
        if (consume('-')) return -parseFactor()

        if (consume('(')) {
            val value = parseExpression()
            if (!consume(')')) throw IllegalArgumentException()
            return value
        }

        return parseNumber()
    }

    private fun parseNumber(): Double {
        skipSpaces()
        val start = index
        while (index < source.length && (source[index].isDigit() || source[index] == '.')) {
            index++
        }
        if (start == index) throw IllegalArgumentException()
        return source.substring(start, index).toDoubleOrNull()
            ?: throw IllegalArgumentException()
    }

    private fun consume(char: Char): Boolean {
        skipSpaces()
        return if (index < source.length && source[index] == char) {
            index++
            true
        } else {
            false
        }
    }

    private fun skipSpaces() {
        while (index < source.length && source[index].isWhitespace()) {
            index++
        }
    }
}
@Composable
private fun ShareStatementDialog(
    customer: CustomerSummary,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var isGenerating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val profileRepository = remember { com.radafiq.data.profile.UserProfileRepository() }
    val profileState by profileRepository.state.collectAsState()
    val userName = profileState.profile?.displayName?.takeIf { it.isNotBlank() }
        ?: profileState.profile?.email?.takeIf { it.isNotBlank() }
        ?: "Radafiq User"
    // Capture current theme so the PDF matches what the user sees
    val isDarkTheme = androidx.compose.ui.platform.LocalConfiguration.current.let {
        (it.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    // Start observing profile as soon as dialog opens
    LaunchedEffect(Unit) {
        profileRepository.observeCurrentUserProfile()
    }

    LaunchedEffect(isGenerating) {
        if (isGenerating) {
            try {
                val generator = com.radafiq.data.backup.StatementGenerator(context)
                val statementUri = generator.generateStatement(customer, userName, isDarkTheme).getOrThrow()

                // Share the PDF
                val shareIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_STREAM, statementUri)
                    type = "application/pdf"
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    android.content.Intent.createChooser(
                        shareIntent,
                        "Share Statement"
                    )
                )
                isGenerating = false
                onDismiss()
            } catch (e: Exception) {
                isGenerating = false
                errorMessage = "Failed to generate statement: ${e.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    if (isGenerating) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Generating Statement") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.padding(16.dp)
                    )
                    Text(
                        "Creating PDF statement...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {}
        )
    } else if (errorMessage.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { errorMessage = "" },
            title = { Text("Error") },
            text = { Text(errorMessage) },
            confirmButton = {
                Button(onClick = { errorMessage = "" }) {
                    Text("OK")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Share Statement") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Generate a PDF statement with customer details, transactions, and EMI schedule.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowCard(
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Customer",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    customer.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Total Used",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    formatMoney(customer.totalAmount),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Balance",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    formatMoney(customer.balance),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (customer.balance > 0.0) warningColor()
                                            else MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                "${customer.transactions.filter { it.isVisibleInTransactions() }.countLogicalTransactions()} transaction(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Generated by",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    userName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isGenerating = true
                    }
                ) {
                    Text("Generate & Share")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}
