package com.radafiq.ui


import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.radafiq.data.models.AccountKind
import com.radafiq.data.models.CardSummary
import com.radafiq.data.profile.UserProfile
import com.radafiq.data.security.AppSecurityState
import com.radafiq.data.settings.AppSettingsState
import com.radafiq.data.settings.AppThemeMode
import com.radafiq.viewmodel.MainViewModel
import java.time.LocalDate
import java.time.LocalTime

enum class DashboardTab {
    HOME, ACCOUNTS, CUSTOMERS, EMI_SCHEDULE, SETTINGS
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
    DashboardTabItem(DashboardTab.EMI_SCHEDULE, "EMI", Icons.Default.CalendarMonth),
    DashboardTabItem(DashboardTab.SETTINGS, "Settings", Icons.Default.Settings)
)

@Composable
fun DashboardScreen(
    navController: NavController,
    selectedAccountIds: Set<String>,
    profileName: String,
    photoUrl: String,
    vm: MainViewModel = viewModel(),
    settingsState: AppSettingsState,
    profile: UserProfile?,
    securityState: AppSecurityState,
    lockedAccountIds: Set<String>,
    backupStatusMessage: String,
    isBackupOperationInProgress: Boolean,
    lastDriveBackupTime: String?,
    lastDriveRestoreTime: String?,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onAccountSelectionChanged: (String, Boolean) -> Unit,
    onLockEnabledChanged: (Boolean) -> Unit,
    onBiometricEnabledChanged: (Boolean) -> Unit,
    onEditProfile: () -> Unit,
    onOpenSecuritySetup: () -> Unit,
    onBackupToDrive: () -> Unit,
    onRestoreFromDrive: () -> Unit,
    onDriveBackup: () -> Unit,
    onDriveRestore: () -> Unit,
    isDriveOperationInProgress: Boolean,
    driveBackupStatusMessage: String,
    onLogout: () -> Unit,
    onOpenCustomer: (String) -> Unit = {},
    onOpenAccount: (String) -> Unit = {}
) {
    var currentScreen by rememberSaveable { mutableStateOf(DashboardTab.HOME) }

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
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        initialOffsetX = { if (forward) it else -it }
                    ) + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(250, easing = FastOutSlowInEasing),
                        targetOffsetX = { if (forward) -it else it }
                    ) + fadeOut(tween(200)))
                },
                label = "dashboard_tab"
            ) { (tab, _) ->
            when (tab) {
                DashboardTab.HOME -> HomeScreen(
                    vm = vm,
                    profileName = profileName,
                    photoUrl = photoUrl,
                    modifier = Modifier.padding(padding)
                )

                DashboardTab.ACCOUNTS -> AccountsScreen(
                    vm = vm,
                    modifier = Modifier.padding(padding),
                    onOpenAccount = onOpenAccount
                )

                DashboardTab.CUSTOMERS -> CustomersScreen(
                    selectedAccountIds = selectedAccountIds,
                    vm = vm,
                    modifier = Modifier.padding(padding),
                    onOpenCustomer = onOpenCustomer
                )

                DashboardTab.EMI_SCHEDULE -> EmiScheduleScreen(
                    vm = vm,
                    modifier = Modifier.padding(padding)
                )

                DashboardTab.SETTINGS -> SettingsContent(
                    settingsState = settingsState,
                    profile = profile,
                    securityState = securityState,
                    lockedAccountIds = lockedAccountIds,
                    backupStatusMessage = backupStatusMessage,
                    isBackupOperationInProgress = isBackupOperationInProgress,
                    lastDriveBackupTime = lastDriveBackupTime,
                    lastDriveRestoreTime = lastDriveRestoreTime,
                    onThemeModeSelected = onThemeModeSelected,
                    onAccountSelectionChanged = onAccountSelectionChanged,
                    onLockEnabledChanged = onLockEnabledChanged,
                    onBiometricEnabledChanged = onBiometricEnabledChanged,
                    onEditProfile = onEditProfile,
                    onOpenSecuritySetup = onOpenSecuritySetup,
                    onBackupToDrive = onBackupToDrive,
                    onRestoreFromDrive = onRestoreFromDrive,
                    onDriveBackup = onDriveBackup,
                    onDriveRestore = onDriveRestore,
                    isDriveOperationInProgress = isDriveOperationInProgress,
                    driveBackupStatusMessage = driveBackupStatusMessage,
                    onLogout = onLogout,
                    modifier = Modifier.padding(padding)
                )
            }
            } // end AnimatedContent
        }
    }
}

@Composable
private fun DashboardBottomBar(
    currentScreen: DashboardTab,
    onTabSelected: (DashboardTab) -> Unit
) {
    val useDarkTheme = LocalRadafiqDarkTheme.current
    val bgColor = if (useDarkTheme) Color(0xFF1A1A35) else MaterialTheme.colorScheme.surface.copy(alpha = 0.74f)
    val borderColor = if (useDarkTheme) Color(0xFF3A3A6A).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(bgColor)
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                )
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DashboardTabs.forEach { item ->
                val selected = currentScreen == item.tab
                val containerColor = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    Color.Transparent
                }
                val contentColor = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(containerColor)
                        .clickable { onTabSelected(item.tab) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = contentColor
                    )
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
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
    vm: MainViewModel,
    profileName: String,
    photoUrl: String,
    modifier: Modifier = Modifier
) {
    val cards by vm.cards.collectAsState()
    val customers by vm.customers.collectAsState()
    val driveOperationMessage by vm.driveOperationMessage.collectAsState()

    val snapshotKey = customers.sumOf { it.snapshotVersion }
    val usedAccountIds = remember(snapshotKey) {
        val set = mutableSetOf<String>()
        customers.forEach { c -> c.transactions.forEach { set.add(it.accountId) } }
        set
    }
    val visibleCards = remember(cards, usedAccountIds) {
        if (usedAccountIds.isEmpty()) emptyList()
        else cards.filter { it.id in usedAccountIds }
    }

    data class CardTotals(val used: Double, val paid: Double, val balance: Double)
    val cardTotals = remember(visibleCards) {
        var u = 0.0; var p = 0.0; var b = 0.0
        for (c in visibleCards) { u += c.bill; p += (c.bill - c.payable).coerceAtLeast(0.0); b += c.payable }
        CardTotals(u, p, b)
    }

    data class CustomerAgg(
        val emiOutstandingByAccount: Map<String, Double>,
        val nonEmiDueByAccount: Map<String, Double>,
        val personSummaries: List<PersonSummary>
    )
    val customerAgg = remember(snapshotKey) {
        val emiMap = mutableMapOf<String, Double>()
        val nonEmiMap = mutableMapOf<String, Double>()
        val personMap = linkedMapOf<String, Triple<String, Double, Double>>()
        customers.forEach { customer ->
            customer.transactions.forEach { t ->
                if (t.accountKind == AccountKind.PERSON) {
                    if (t.isVisibleInTransactions()) {
                        val key = t.accountId
                        val name = t.personName.ifBlank { t.accountName }
                        val used = t.amount
                        val due = if (t.isSettled) 0.0 else (t.amount - t.partialPaidAmount).coerceAtLeast(0.0)
                        val existing = personMap[key]
                        personMap[key] = if (existing == null) Triple(name, used, due)
                            else Triple(existing.first, existing.second + used, existing.third + due)
                    }
                } else if (!t.isEmi) {
                    if (t.isVisibleInTransactions()) {
                        val due = if (t.isSettled) 0.0 else (t.amount - t.partialPaidAmount).coerceAtLeast(0.0)
                        if (due > 0.0) nonEmiMap[t.accountId] = (nonEmiMap[t.accountId] ?: 0.0) + due
                    }
                } else {
                    val due = if (t.isSettled) 0.0 else (t.amount - t.partialPaidAmount).coerceAtLeast(0.0)
                    if (due > 0.0) emiMap[t.accountId] = (emiMap[t.accountId] ?: 0.0) + due
                }
            }
        }
        CustomerAgg(
            emiOutstandingByAccount = emiMap,
            nonEmiDueByAccount = nonEmiMap,
            personSummaries = personMap.entries
                .map { (id, v) -> PersonSummary(id, v.first, v.second, v.third) }
                .sortedByDescending { it.totalDue }
        )
    }

    val availableKinds = remember(snapshotKey) {
        val kinds = visibleCards.map { it.accountKind }.toMutableList()
        if (customerAgg.personSummaries.isNotEmpty() && AccountKind.PERSON !in kinds) kinds.add(AccountKind.PERSON)
        kinds.distinct()
    }
    var selectedActivityKindName by rememberSaveable { mutableStateOf("ALL") }
    var isFilterOpen by rememberSaveable { mutableStateOf(false) }

    val activityCards = when (selectedActivityKindName) {
        "ALL" -> visibleCards
        AccountKind.PERSON.name -> emptyList()
        else -> visibleCards.filter { it.accountKind.name == selectedActivityKindName }
    }
    val activityPersons = when (selectedActivityKindName) {
        "ALL", AccountKind.PERSON.name -> customerAgg.personSummaries
        else -> emptyList()
    }

    // Compute savings list before LazyColumn (remember must be called at composable scope)
    val savingsCustomers = remember(snapshotKey) {
        customers.filter { it.savingsBalance > 0.0 }
            .sortedByDescending { it.savingsBalance }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .radafiqScrollBackground(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            HomeGreetingHeader(
                profileName = profileName,
                photoUrl = photoUrl
            )
        }

        val driveMsg = driveOperationMessage
        if (!driveMsg.isNullOrBlank()) {
            item {
                DriveOperationCard(message = driveMsg)
            }
        }

        item {
            HeroPanel(
                title = "Outstanding Balance",
                amountValue = cardTotals.balance,
                subtitle = "${visibleCards.size} active account(s) currently contributing to your ledger flow."
            )
        }

        item {
            ResponsiveTwoPane(
                first = { itemModifier ->
                    MetricPill(
                        label = "Total Used",
                        amountValue = cardTotals.used,
                        color = warningColor(),
                        modifier = itemModifier
                    )
                },
                second = { itemModifier ->
                    MetricPill(
                        label = "Total Paid",
                        amountValue = cardTotals.paid,
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
            items(activityCards.sortedByDescending { it.payable }.take(6), key = { it.id }) { card ->
                HomeActivityCard(
                    card = card,
                    currentDue = customerAgg.nonEmiDueByAccount[card.id] ?: 0.0,
                    emiOutstanding = customerAgg.emiOutstandingByAccount[card.id] ?: 0.0
                )
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
            items(activityPersons.take(6), key = { "person_${it.personId}" }) { person ->
                HomePersonCard(person = person)
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

        // ── Savings summary section ───────────────────────────────────────────
        if (savingsCustomers.isNotEmpty()) {
            item {
                Text(
                    text = "Savings",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "Customer savings held in bank accounts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            items(savingsCustomers, key = { "savings_home_${it.id}" }) { c ->
                HomeSavingsCard(customer = c)
            }
        }
    }
}

@Composable
private fun HomeSavingsCard(customer: com.radafiq.data.models.CustomerSummary) {
    val accent = MaterialTheme.colorScheme.secondary
    FlowCard(accentColor = accent) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Show the latest bank account name if available
                val latestBankName = customer.savingsEntries
                    .filter { it.bankAccountName.isNotBlank() }
                    .maxByOrNull { it.date }?.bankAccountName
                if (latestBankName != null) {
                    Text(
                        text = "🏦 $latestBankName",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                AnimatedMoney(
                    value = customer.savingsBalance,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Text(
                    text = "Savings Balance",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
    photoUrl: String
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

        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
        ) {
            if (photoUrl.isNotBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "Profile photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
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
                        contentDescription = if (isOutgoing) "Outgoing transaction" else "Incoming transaction",
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
                                AnimatedMoney(
                                    value = currentDue,
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
                                    AnimatedMoney(
                                        value = emiOutstanding,
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
                AnimatedMoney(
                    value = card.payable,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    modifier = Modifier
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
                AnimatedMoney(
                    value = person.totalDue,
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
    modifier: Modifier = Modifier,
    vm: MainViewModel = viewModel()
) {
    val customers by vm.customers.collectAsState()
    val today = remember { LocalDate.now() }

    // Collect every EMI instalment across all customers, only from groups
    // Collect every EMI instalment across all customers, grouped by plan.
    // Shows all EMI groups that have at least one instalment recorded.
    val allRows: List<EmiGroupRow> = remember(customers.sumOf { it.snapshotVersion }, today) {
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
                    // Use dueDate for past/current classification when available;
                    // fall back to transactionDate for legacy records without a dueDate.
                    val dueDateStr = first.dueDate.ifBlank { first.transactionDate }
                    val dueLocalDate = runCatching { LocalDate.parse(dueDateStr) }.getOrNull()
                    val isPast = dueLocalDate == null || !dueLocalDate.isAfter(today)
                    val isCurrent = dueLocalDate != null &&
                        dueLocalDate.year == today.year && dueLocalDate.monthValue == today.monthValue
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
    val groupedByPlan: List<Pair<String, List<EmiGroupRow>>> = remember(customers.sumOf { it.snapshotVersion }, today) {
        allRows
            .groupBy { it.groupId }
            .entries
            .sortedBy { (_, rows) -> rows.first().transactionDate }
            .map { (groupId, rows) -> groupId to rows }
    }

    val emiSnapshotKey = customers.sumOf { it.snapshotVersion }
    val totalUpcoming = remember(emiSnapshotKey) {
        allRows.filter { !it.isPast && !it.isSettled }.sumOf { it.amount }
    }
    val totalPaid = remember(emiSnapshotKey) {
        allRows.filter { it.isSettled }.sumOf { it.amount }
    }
    val totalPending = remember(emiSnapshotKey) {
        allRows.filter { it.isPast && !it.isSettled }.sumOf { it.amount }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .radafiqScrollBackground()
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
                        amountValue = totalUpcoming,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        label = "Pending",
                        amountValue = totalPending,
                        color = warningColor(),
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        label = "Settled",
                        amountValue = totalPaid,
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
                        AnimatedMoney(
                            value = groupTotal,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
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
                EmiMiniStat("Pending", groupPending, warningColor(), Modifier.weight(1f))
                EmiMiniStat("Upcoming", groupUpcoming, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                EmiMiniStat("Settled", groupPaid, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
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
                    contentDescription = if (expanded) "Collapse schedule" else "Expand schedule",
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
            text = "Txn Date",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.3f)
        )
        Text(
            text = "Due Date",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.3f)
        )
        Text(
            text = "Amount",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.2f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
        Text(
            text = "Status",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
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

    var showDueDatePicker by remember { mutableStateOf(false) }

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
        // Txn Date column — computed as dueDate - 20 days, read-only
        Column(modifier = Modifier.weight(1.3f)) {
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
        // Due Date column — tappable to edit; saving updates Firestore and
        // automatically recalculates transactionDate (dueDate - 20 days) at read time
        val dueDateDisplay = if (row.dueDate.isNotBlank()) formatDisplayDate(row.dueDate)
                             else formatDisplayDate(row.transactionDate)
        Text(
            text = dueDateDisplay,
            style = MaterialTheme.typography.bodySmall,
            color = statusColor,
            fontWeight = if (row.isCurrent || (row.isPast && !row.isSettled)) FontWeight.SemiBold
                         else FontWeight.Normal,
            modifier = Modifier
                .weight(1.3f)
                .clip(RoundedCornerShape(4.dp))
                .clickable { showDueDatePicker = true }
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = formatMoney(row.amount),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.2f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 1
        )
        // Status badge — tappable to toggle settled for ALL parts of this installment
        Box(
            modifier = Modifier
                .width(64.dp)
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

    if (showDueDatePicker) {
        val initialDate = row.dueDate.ifBlank { row.transactionDate }
        EmiDueDatePickerDialog(
            initialDateIso = initialDate,
            onDateSelected = { iso ->
                vm.updateEmiDueDate(row.transactionIds, iso)
                showDueDatePicker = false
            },
            onDismiss = { showDueDatePicker = false }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EmiDueDatePickerDialog(
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

@Composable
private fun EmiMiniStat(label: String, amountValue: Double, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedMoney(
            value = amountValue,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
