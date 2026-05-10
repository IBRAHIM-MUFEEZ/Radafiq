package com.radafiq.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Divider
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.radafiq.data.models.AccountKind
import com.radafiq.data.models.CardSummary
import com.radafiq.data.models.CustomerSummary
import com.radafiq.viewmodel.MainViewModel
import java.time.LocalDate
import java.time.LocalTime

enum class DashboardTab {
    HOME, ACCOUNTS, CUSTOMERS, EMI_SCHEDULE
}

private data class DashboardTabItem(
    val tab: DashboardTab,
    val label: String,
    val icon: ImageVector
)

private val DashboardTabs = listOf(
    DashboardTabItem(DashboardTab.HOME, "Home", Icons.Default.Home),
    DashboardTabItem(DashboardTab.ACCOUNTS, "Accounts", Icons.Default.CreditCard),
    DashboardTabItem(DashboardTab.CUSTOMERS, "Customers", Icons.Default.AccountBox),
    DashboardTabItem(DashboardTab.EMI_SCHEDULE, "EMI", Icons.Default.CalendarMonth)
)

@Composable
fun DashboardScreen(
    navController: NavController,
    selectedAccountIds: Set<String>,
    onOpenSettings: () -> Unit,
    profileName: String,
    vm: MainViewModel = viewModel(),
    onOpenCustomer: (String) -> Unit = {},
    onOpenAccount: (String) -> Unit = {}
) {
    var currentScreen by rememberSaveable { mutableStateOf(DashboardTab.HOME) }
    val cards by vm.cards.collectAsState()
    val customers by vm.customers.collectAsState()
    val driveOperationMessage by vm.driveOperationMessage.collectAsState()

    // Only show accounts that have been used in at least one customer transaction
    val usedAccountIds = remember(customers) {
        customers.flatMap { it.transactions }.map { it.accountId }.toSet()
    }
    val visibleCards = remember(cards, usedAccountIds) {
        if (usedAccountIds.isEmpty()) emptyList()
        else cards.filter { it.id in usedAccountIds }
    }

    RadafiqBackground {
        Scaffold(
            containerColor = Color.Transparent,
            floatingActionButton = {
                if (currentScreen == DashboardTab.CUSTOMERS) {
                    ExtendedFloatingActionButton(
                        onClick = { navController.navigate("addTransaction") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = { Icon(Icons.Default.Add, contentDescription = "Add Customer") },
                        text = { Text("Add Customer") }
                    )
                }
            },
            bottomBar = {
                DashboardBottomBar(
                    currentScreen = currentScreen,
                    onTabSelected = { currentScreen = it }
                )
            }
        ) { padding ->
            val tabIndex = DashboardTabs.indexOfFirst { it.tab == currentScreen }
            AnimatedContent(
                targetState = currentScreen to tabIndex,
                transitionSpec = {
                    val (_, targetIdx) = targetState
                    val (_, initialIdx) = initialState
                    val forward = targetIdx > initialIdx
                    (slideInHorizontally(
                        animationSpec = tween(300),
                        initialOffsetX = { if (forward) it else -it }
                    ) + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(300),
                        targetOffsetX = { if (forward) -it else it }
                    ) + fadeOut(tween(200)))
                },
                label = "dashboard-tab"
            ) { (tab, _) ->
                when (tab) {
                    DashboardTab.HOME -> HomeScreen(
                        cards = visibleCards,
                        customers = customers,
                        profileName = profileName,
                        driveOperationMessage = driveOperationMessage,
                        modifier = Modifier.padding(padding),
                        onOpenSettings = onOpenSettings
                    )

                    DashboardTab.ACCOUNTS -> AccountsScreen(
                        cards = visibleCards,
                        vm = vm,
                        modifier = Modifier.padding(padding),
                        onOpenSettings = onOpenSettings,
                        onOpenAccount = onOpenAccount
                    )

                    DashboardTab.CUSTOMERS -> CustomersScreen(
                        selectedAccountIds = selectedAccountIds,
                        vm = vm,
                        modifier = Modifier.padding(padding),
                        onOpenSettings = onOpenSettings,
                        onOpenCustomer = onOpenCustomer
                    )

                    DashboardTab.EMI_SCHEDULE -> EmiScheduleScreen(
                        customers = customers,
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardBottomBar(
    currentScreen: DashboardTab,
    onTabSelected: (DashboardTab) -> Unit
) {
    val springSpec = spring<Float>(dampingRatio = 0.5f, stiffness = 500f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.74f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                )
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DashboardTabs.forEach { item ->
                val selected = currentScreen == item.tab
                val animatedScale by animateFloatAsState(
                    targetValue = if (selected) 1.15f else 1f,
                    animationSpec = springSpec,
                    label = "iconScale"
                )
                val animatedBgAlpha by animateFloatAsState(
                    targetValue = if (selected) 0.14f else 0f,
                    animationSpec = tween(250),
                    label = "bgAlpha"
                )
                val animatedColor: Color by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(250),
                    label = "iconColor"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = animatedBgAlpha))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(item.tab) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .scale(animatedScale)
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = animatedColor
                        )
                    }
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = animatedColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// Lightweight summary for a person (aggregated from customer transactions)
private data class PersonSummary(
    val personId: String,
    val personName: String,
    val totalUsed: Double,
    val totalDue: Double
)

@Composable
fun HomeScreen(
    cards: List<CardSummary>,
    customers: List<CustomerSummary>,
    profileName: String,
    driveOperationMessage: String?,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit
) {
    val totalUsed = cards.sumOf { it.bill }
    // totalPaid = what customers have paid = total used minus what is still outstanding
    // (cards.pending is the owner's own payments to the bank, not customer payments)
    val totalPaid = cards.sumOf { (it.bill - it.payable).coerceAtLeast(0.0) }
    val totalBalance = cards.sumOf { it.payable }

    // Compute per-account breakdowns from customer transactions:
    //   emiOutstandingByAccount — ALL unpaid EMI installments (past + current + future)
    //   nonEmiDueByAccount      — unpaid non-EMI transactions only (shown as "Current Due")
    val emiOutstandingByAccount = remember(customers) {
        val map = mutableMapOf<String, Double>()
        customers.forEach { customer ->
            customer.transactions.forEach { t ->
                if (t.accountKind == AccountKind.PERSON) return@forEach
                if (!t.isEmi) return@forEach
                val due = if (t.isSettled) 0.0 else (t.amount - t.partialPaidAmount).coerceAtLeast(0.0)
                if (due <= 0.0) return@forEach
                map[t.accountId] = (map[t.accountId] ?: 0.0) + due
            }
        }
        map
    }

    val nonEmiDueByAccount = remember(customers) {
        val map = mutableMapOf<String, Double>()
        customers.forEach { customer ->
            customer.transactions.forEach { t ->
                if (t.accountKind == AccountKind.PERSON) return@forEach
                if (t.isEmi) return@forEach // EMIs go to emiOutstandingByAccount
                if (t.isScheduledForFutureMonth()) return@forEach
                val due = if (t.isSettled) 0.0 else (t.amount - t.partialPaidAmount).coerceAtLeast(0.0)
                if (due <= 0.0) return@forEach
                map[t.accountId] = (map[t.accountId] ?: 0.0) + due
            }
        }
        map
    }

    // Aggregate person transactions across all customers
    val personSummaries = remember(customers) {
        val map = linkedMapOf<String, Triple<String, Double, Double>>() // id -> (name, used, due)
        customers.forEach { customer ->
            customer.transactions
                .filter { it.accountKind == AccountKind.PERSON && it.isVisibleInTransactions() }
                .forEach { txn ->
                    val key = txn.accountId
                    val name = txn.personName.ifBlank { txn.accountName }
                    val used = txn.amount
                    val due = if (txn.isSettled) 0.0 else (txn.amount - txn.partialPaidAmount).coerceAtLeast(0.0)
                    val existing = map[key]
                    if (existing == null) {
                        map[key] = Triple(name, used, due)
                    } else {
                        map[key] = Triple(existing.first, existing.second + used, existing.third + due)
                    }
                }
        }
        map.entries.map { (id, v) -> PersonSummary(id, v.first, v.second, v.third) }
            .sortedByDescending { it.totalDue }
    }

    val availableKinds = remember(cards, personSummaries) {
        val kinds = cards.map { it.accountKind }.toMutableList()
        if (personSummaries.isNotEmpty() && AccountKind.PERSON !in kinds) kinds.add(AccountKind.PERSON)
        kinds.distinct()
    }
    var selectedActivityKindName by rememberSaveable { mutableStateOf("ALL") }
    var isFilterOpen by rememberSaveable { mutableStateOf(false) }

    val activityCards = when (selectedActivityKindName) {
        "ALL" -> cards
        AccountKind.PERSON.name -> emptyList()
        else -> cards.filter { it.accountKind.name == selectedActivityKindName }
    }
    val activityPersons = when (selectedActivityKindName) {
        "ALL", AccountKind.PERSON.name -> personSummaries
        else -> emptyList()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            HomeGreetingHeader(
                profileName = profileName,
                onOpenSettings = onOpenSettings
            )
        }

        if (!driveOperationMessage.isNullOrBlank()) {
            item {
                DriveOperationCard(message = driveOperationMessage)
            }
        }

        item {
            HeroPanel(
                title = "Outstanding Balance",
                amount = formatMoney(totalBalance),
                subtitle = "${cards.size} active account(s) currently contributing to your ledger flow."
            )
        }

        item {
            ResponsiveTwoPane(
                first = { itemModifier ->
                    MetricPill(
                        label = "Total Used",
                        value = formatMoney(totalUsed),
                        color = warningColor(),
                        modifier = itemModifier
                    )
                },
                second = { itemModifier ->
                    MetricPill(
                        label = "Total Paid",
                        value = formatMoney(totalPaid),
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = itemModifier
                    )
                }
            )
        }

        item {
            AdaptiveHeaderRow(
                leading = {
                    Column {
                        Text(
                            text = "Account Activity",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Live summary of your accounts and person balances.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                },
                trailing = {
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(999.dp)
                                )
                                .clickable { isFilterOpen = true }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = when (selectedActivityKindName) {
                                    "ALL" -> "All Accounts"
                                    else -> availableKinds.firstOrNull { it.name == selectedActivityKindName }?.label
                                        ?: "All Accounts"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select account filter",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = isFilterOpen,
                            onDismissRequest = { isFilterOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Accounts") },
                                onClick = {
                                    selectedActivityKindName = "ALL"
                                    isFilterOpen = false
                                }
                            )
                            availableKinds.forEach { kind ->
                                DropdownMenuItem(
                                    text = { Text(kind.label) },
                                    onClick = {
                                        selectedActivityKindName = kind.name
                                        isFilterOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }

        if (activityCards.isEmpty() && activityPersons.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        title = "No activity yet",
                        subtitle = "As customers and payments are recorded, the adapted activity cards will appear here."
                    )
                }
            }
        } else {
            itemsIndexed(activityCards.sortedByDescending { it.payable }.take(6), key = { _, c -> c.id }) { idx, card ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(300 + idx * 50)) + slideInVertically(tween(300 + idx * 50)) { it / 4 },
                    label = "cardAnim"
                ) {
                    HomeActivityCard(
                        card = card,
                        currentDue = nonEmiDueByAccount[card.id] ?: 0.0,
                        emiOutstanding = emiOutstandingByAccount[card.id] ?: 0.0
                    )
                }
            }
            // FIX-20: Show "Show all" link when list is truncated
            if (activityCards.size > 6) {
                item {
                    androidx.compose.material3.TextButton(
                        onClick = { /* no-op: user can switch to Accounts tab */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Showing 6 of ${activityCards.size} accounts — open Accounts tab to see all",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            itemsIndexed(activityPersons.take(6), key = { _, p -> "person_${p.personId}" }) { idx, person ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(300 + idx * 50)) + slideInVertically(tween(300 + idx * 50)) { it / 4 },
                    label = "personCardAnim"
                ) {
                    HomePersonCard(person = person)
                }
            }
            if (activityPersons.size > 6) {
                item {
                    androidx.compose.material3.TextButton(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Showing 6 of ${activityPersons.size} persons — open Accounts tab to see all",
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
private fun DriveOperationCard(message: String) {
    FlowCard(accentColor = MaterialTheme.colorScheme.primary) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Google Drive Sync",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            StatusBadge(
                text = "In Progress",
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun HomeGreetingHeader(
    profileName: String,
    onOpenSettings: () -> Unit
) {
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 0..11 -> "Good Morning,"
            in 12..16 -> "Good Afternoon,"
            else -> "Good Evening,"
        }
    }
    val resolvedProfileName = profileName.trim().ifBlank { "Your Profile" }
    val initials = remember(resolvedProfileName) {
        resolvedProfileName
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString(separator = "") { it.first().uppercaseChar().toString() }
            .ifBlank { "RA" }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = greeting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = resolvedProfileName,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun HomeActivityCard(card: CardSummary, currentDue: Double = 0.0, emiOutstanding: Double = 0.0) {
    val isOutgoing = card.accountKind == AccountKind.CREDIT_CARD
    val accentColor = if (isOutgoing) warningColor() else MaterialTheme.colorScheme.secondary
    val supportingText = buildString {
        append(card.accountKind.label)
        if (card.dueDate.isNotBlank()) {
            append(" • Due ")
            append(card.dueDate)
        }
    }

    FlowCard(accentColor = accountAccent(card.accountKind)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isOutgoing) Icons.Default.NorthEast else Icons.Default.SouthWest,
                        contentDescription = null,
                        tint = accentColor
                    )
                }

                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = card.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Credit card: show current due (non-EMI) + EMI outstanding (all EMIs)
                    if (isOutgoing) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Current Due pill — non-EMI unpaid transactions only
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(warningColor().copy(alpha = 0.08f))
                                    .border(1.dp, warningColor().copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "CURRENT DUE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = formatMoney(currentDue),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = warningColor(),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            // EMI Outstanding pill — all unpaid EMI installments
                            if (emiOutstanding > 0.0) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "EMI OUTSTANDING",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = formatMoney(emiOutstanding),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bank account: show single amount on the right
            if (!isOutgoing) {
                Text(
                    text = "+${formatMoney(card.payable)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
        }
    }
}

@Composable
private fun HomePersonCard(person: PersonSummary) {
    val personAccent = MaterialTheme.colorScheme.primary

    FlowCard(accentColor = personAccent) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(personAccent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    val initials = person.personName
                        .trim()
                        .split(Regex("\\s+"))
                        .filter { it.isNotBlank() }
                        .take(2)
                        .joinToString("") { it.first().uppercaseChar().toString() }
                        .ifBlank { "P" }
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = personAccent
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = person.personName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Person • Used ${formatMoney(person.totalUsed)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatMoney(person.totalDue),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (person.totalDue > 0.0) warningColor() else personAccent
                )
                Text(
                    text = if (person.totalDue > 0.0) "Due" else "Settled",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── EMI Schedule Tab ─────────────────────────────────────────────────────────

private data class EmiGroupRow(
    val groupId: String,
    val transactionIds: List<String>,  // all doc IDs for this installment (>1 if split)
    val customerName: String,
    val planName: String,
    val accountName: String,           // joined account names if split
    val emiIndex: Int,
    val emiTotal: Int,
    val amount: Double,                // combined amount across all split parts
    val transactionDate: String,
    val dueDate: String,
    val isSettled: Boolean,            // true only if ALL parts settled
    val isPast: Boolean,
    val isCurrent: Boolean,
    val isSplitInstallment: Boolean    // true if this installment was split
)

@Composable
fun EmiScheduleScreen(
    customers: List<CustomerSummary>,
    modifier: Modifier = Modifier,
    vm: MainViewModel = viewModel()
) {
    val today = remember { LocalDate.now() }

    // Collect every EMI instalment across all customers, only from groups
    // Collect every EMI instalment across all customers, grouped by plan.
    // Shows all EMI groups that have at least one instalment recorded.
    val allRows: List<EmiGroupRow> = remember(customers, today) {
        val result = mutableListOf<EmiGroupRow>()

        customers.forEach { customer ->
            val emiGroups = customer.transactions
                .filter { it.isEmi }
                .groupBy { t ->
                    t.emiGroupId.ifBlank {
                        t.name.substringBefore(" — EMI").trim() + "_" + customer.name
                    }
                }

            emiGroups.forEach { (groupKey, instalments) ->
                // Group by emiIndex — split installments share the same emiIndex
                val byIndex = instalments.groupBy { it.emiIndex }

                byIndex.forEach { (emiIndex, parts) ->
                    val first = parts.first()
                    val date = runCatching { LocalDate.parse(first.transactionDate) }.getOrNull()
                    val isPast = date == null || !date.isAfter(today)
                    val isCurrent = date != null &&
                        date.year == today.year && date.monthValue == today.monthValue
                    val planName = first.name.substringBefore(" — EMI").trim()
                    val combinedAmount = parts.sumOf { it.amount }
                    val allSettled = parts.all { it.isSettled }
                    val isSplit = parts.size > 1
                    val accountName = if (isSplit)
                        parts.mapNotNull { it.accountName.ifBlank { null } }.distinct().joinToString(", ")
                    else
                        first.accountName

                    result.add(
                        EmiGroupRow(
                            groupId = groupKey,
                            transactionIds = parts.map { it.id },
                            customerName = customer.name,
                            planName = planName,
                            accountName = accountName,
                            emiIndex = emiIndex,
                            emiTotal = first.emiTotal,
                            amount = combinedAmount,
                            transactionDate = first.transactionDate,
                            dueDate = first.dueDate,
                            isSettled = allSettled,
                            isPast = isPast,
                            isCurrent = isCurrent,
                            isSplitInstallment = isSplit
                        )
                    )
                }
            }
        }

        result.sortWith(
            compareBy<EmiGroupRow> { if (it.isCurrent) 0 else if (!it.isPast) 1 else 2 }
                .thenBy { it.transactionDate }
                .thenBy { it.customerName }
        )
        result
    }

    // Group rows by EMI group for the amortization table view
    val groupedByPlan: List<Pair<String, List<EmiGroupRow>>> = remember(allRows) {
        allRows
            .groupBy { it.groupId }
            .entries
            .sortedBy { (_, rows) -> rows.first().transactionDate }
            .map { (groupId, rows) -> groupId to rows }
    }

    val totalUpcoming = remember(allRows) {
        allRows.filter { !it.isPast && !it.isSettled }.sumOf { it.amount }
    }
    val totalPaid = remember(allRows) {
        allRows.filter { it.isSettled }.sumOf { it.amount }
    }
    val totalPending = remember(allRows) {
        allRows.filter { it.isPast && !it.isSettled }.sumOf { it.amount }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PageHeader(
                title = "EMI Schedule",
                subtitle = "Amortization view of all active EMI plans across customers."
            )
        }

        if (allRows.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        title = "No EMI plans yet",
                        subtitle = "When a customer has an EMI transaction added, the full schedule appears here."
                    )
                }
            }
            return@LazyColumn
        }

        // Summary strip
        item {
            FlowCard(accentColor = MaterialTheme.colorScheme.primary) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricPill(
                        label = "Upcoming",
                        value = formatMoney(totalUpcoming),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        label = "Pending",
                        value = formatMoney(totalPending),
                        color = warningColor(),
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        label = "Settled",
                        value = formatMoney(totalPaid),
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // One amortization card per EMI group
        groupedByPlan.forEachIndexed { index, (_, rows) ->
            val first = rows.first()
            val groupTotal = rows.sumOf { it.amount }
            // Settled = any installment marked as settled (regardless of past/future)
            val groupPaid = rows.filter { it.isSettled }.sumOf { it.amount }
            // Pending = past and NOT settled (overdue)
            val groupPending = rows.filter { it.isPast && !it.isSettled }.sumOf { it.amount }
            // Upcoming = future and NOT settled
            val groupUpcoming = rows.filter { !it.isPast && !it.isSettled }.sumOf { it.amount }

            item(key = first.groupId.ifBlank { "emi_plan_$index" }) {
                EmiAmortizationCard(
                    customerName = first.customerName,
                    planName = first.planName,
                    accountName = first.accountName,
                    rows = rows,
                    groupTotal = groupTotal,
                    groupPaid = groupPaid,
                    groupPending = groupPending,
                    groupUpcoming = groupUpcoming,
                    today = today,
                    vm = vm,
                    onDeleteGroup = { rows.flatMap { it.transactionIds }.forEach { vm.deleteTransaction(it) } }
                )
            }
        }
    }
}

@Composable
private fun EmiAmortizationCard(
    customerName: String,
    planName: String,
    accountName: String,
    rows: List<EmiGroupRow>,
    groupTotal: Double,
    groupPaid: Double,
    groupPending: Double,
    groupUpcoming: Double,
    today: LocalDate,
    vm: MainViewModel,
    onDeleteGroup: () -> Unit
) {
    val groupId = rows.firstOrNull()?.groupId ?: planName
    var expanded by rememberSaveable(groupId) { mutableStateOf(true) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // Progress = settled / total (by count, not amount)
    val settledCount = rows.count { it.isSettled }
    val totalCount = rows.size
    val progress = if (totalCount > 0) settledCount.toFloat() / totalCount.toFloat() else 0f

    FlowCard(accentColor = MaterialTheme.colorScheme.primary) {
        // Header
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = planName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = customerName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        text = accountName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
                Row(verticalAlignment = Alignment.Top) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatMoney(groupTotal),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$settledCount / $totalCount done",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete EMI plan",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress bar
            androidx.compose.material3.LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Mini summary row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EmiMiniStat("Pending", formatMoney(groupPending), warningColor(), Modifier.weight(1f))
                EmiMiniStat("Upcoming", formatMoney(groupUpcoming), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                EmiMiniStat("Settled", formatMoney(groupPaid), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            }

            // Expand/collapse hint
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.AccountBox else Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (expanded) "Tap to collapse" else "Tap to view schedule",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))

            // Table header
            EmiTableHeader()

            Divider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )

            // Instalment rows
            rows.forEachIndexed { idx, row ->
                EmiTableRow(row = row, today = today, vm = vm)
                if (idx != rows.lastIndex) {
                    Divider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete EMI plan?") },
            text = {
                Text(
                    "This will permanently delete all ${rows.size} instalments of \"$planName\" for $customerName. This cannot be undone."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        onDeleteGroup()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Delete all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EmiTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp)
        )
        Text(
            text = "Due Date",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.6f)
        )
        Text(
            text = "Amount",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.4f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
        Text(
            text = "Status",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun EmiTableRow(row: EmiGroupRow, today: LocalDate, vm: MainViewModel) {
    val statusColor = when {
        row.isSettled -> MaterialTheme.colorScheme.secondary
        row.isCurrent -> warningColor()
        row.isPast -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val statusLabel = when {
        row.isSettled -> "Paid"
        row.isCurrent -> "Due"
        row.isPast -> "Overdue"
        else -> "Upcoming"
    }
    val rowBg = when {
        row.isCurrent && !row.isSettled -> warningColor().copy(alpha = 0.07f)
        row.isPast && !row.isSettled -> MaterialTheme.colorScheme.error.copy(alpha = 0.05f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg, RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${row.emiIndex + 1}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (row.isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = statusColor,
            modifier = Modifier.width(24.dp)
        )
        // Date + optional Split badge
        Column(modifier = Modifier.weight(1.6f)) {
            Text(
                text = formatDisplayDate(row.transactionDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (row.isSplitInstallment) {
                Text(
                    text = "Split",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Text(
            text = formatMoney(row.amount),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.4f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 1
        )
        // Status badge — tappable to toggle settled for ALL parts of this installment
        Box(
            modifier = Modifier
                .width(72.dp)
                .padding(start = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(statusColor.copy(alpha = 0.13f))
                    .clickable {
                        val newSettled = !row.isSettled
                        row.transactionIds.forEach { id ->
                            vm.toggleTransactionSettled(id, newSettled)
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun EmiMiniStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
