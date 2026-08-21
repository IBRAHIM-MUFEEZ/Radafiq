package com.radafiq.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radafiq.data.auth.CredentialManagerHelper
import com.radafiq.data.auth.LocalIdentityRepository
import com.radafiq.data.backup.BackupJsonSerializer
import com.radafiq.data.backup.DriveBackupRepository
import com.radafiq.data.models.AccountKind
import com.radafiq.data.models.AppData
import com.radafiq.data.models.CardSummary
import com.radafiq.data.models.CustomerSettlementEntry
import com.radafiq.data.models.CustomerSummary
import com.radafiq.data.models.CustomerTransaction
import com.radafiq.data.models.FirestoreBackupPayload
import com.radafiq.data.models.SettlementHistoryEntry
import com.radafiq.data.models.TransferHistoryEntry
import com.radafiq.data.models.SplitEntry
import com.radafiq.data.profile.UserProfileRepository
import com.radafiq.data.repository.FirebaseRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import com.radafiq.data.security.AppSecurityRepository
import com.radafiq.data.settings.AppSettingsRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/** Persists in-progress transaction form state across lock/unlock cycles. */
data class DraftTransactionState(
    val customerId: String = "",
    val transactionName: String = "",
    val selectedKindName: String = "",       // AccountKind.name
    val selectedAccountId: String = "",
    val personName: String = "",
    val amountExpression: String = "",
    val transactionDate: String = "",
    val splitEnabled: Boolean = false,
    val splitEntries: List<SplitEntry> = listOf(SplitEntry(), SplitEntry()),
    val emiEnabled: Boolean = false,
    val emiMonths: String = "",
    val emiFirstMonthOverride: String = "",
    val emiManualDates: Boolean = false,
    val emiDateOverrides: Map<Int, String> = emptyMap()
) {
    val isEmpty: Boolean get() = customerId.isBlank() && transactionName.isBlank() && amountExpression.isBlank() && transactionDate.isBlank()
}

// ── Pre-computed aggregate data classes for 120Hz fluid UI ────────────────────
data class CardTotals(
    val used: Double = 0.0,
    val paid: Double = 0.0,
    val balance: Double = 0.0
)

data class PersonSummary(
    val personId: String,
    val personName: String,
    val totalUsed: Double,
    val totalDue: Double
)

data class CustomerAggregates(
    val emiOutstandingByAccount: Map<String, Double> = emptyMap(),
    val nonEmiDueByAccount: Map<String, Double> = emptyMap(),
    val personSummaries: List<PersonSummary> = emptyList()
)

data class DashboardAggregates(
    val snapshotVersion: Long = 0L,
    val usedAccountIds: Set<String> = emptySet(),
    val visibleCards: List<CardSummary> = emptyList(),
    val cardTotals: CardTotals = CardTotals(),
    val customerAgg: CustomerAggregates = CustomerAggregates(),
    val availableKinds: List<AccountKind> = emptyList(),
    val savingsCustomers: List<CustomerSummary> = emptyList()
)

/**
 * Computes all dashboard aggregates from raw cards + customers data.
 * Designed to run on [Dispatchers.Default] — never call from the main thread.
 */
internal fun computeAggregates(
    snapshotVersion: Long,
    cards: List<CardSummary>,
    customers: List<CustomerSummary>
): DashboardAggregates {
    val usedAccountIds = mutableSetOf<String>()
    customers.forEach { c -> c.transactions.forEach { usedAccountIds.add(it.accountId) } }

    val visibleCards = if (usedAccountIds.isEmpty()) emptyList()
        else cards.filter { it.id in usedAccountIds && it.payable > 0.0 }

    var totalUsed = 0.0; var totalPaid = 0.0; var totalBalance = 0.0
    for (c in customers) { totalUsed += c.totalAmount; totalPaid += c.creditDueAmount; totalBalance += c.balance }
    val cardTotals = CardTotals(totalUsed, totalPaid, totalBalance)

    // ── Customer aggregator (EMI / non-EMI / person) ──
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
                if (due > 0.0) {
                    // Visible installments are payable now → Current Due (nonEmiMap)
                    // Future installments are still in the pipeline → EMI Outstanding (emiMap)
                    if (t.isVisibleInTransactions()) {
                        nonEmiMap[t.accountId] = (nonEmiMap[t.accountId] ?: 0.0) + due
                    } else {
                        emiMap[t.accountId] = (emiMap[t.accountId] ?: 0.0) + due
                    }
                }
            }
        }
    }
    val customerAgg = CustomerAggregates(
        emiOutstandingByAccount = emiMap,
        nonEmiDueByAccount = nonEmiMap,
        personSummaries = personMap.entries
            .map { (id, v) -> PersonSummary(id, v.first, v.second, v.third) }
            .filter { it.totalDue > 0.0 }
            .sortedByDescending { it.totalDue }
    )

    val availableKinds = buildList {
        visibleCards.mapTo(this) { it.accountKind }
        if (customerAgg.personSummaries.isNotEmpty() && !contains(AccountKind.PERSON)) add(AccountKind.PERSON)
    }.distinct()

    val savingsCustomers = customers.filter { it.savingsBalance > 0.0 }
        .sortedByDescending { it.savingsBalance }

    return DashboardAggregates(
        snapshotVersion = snapshotVersion,
        usedAccountIds = usedAccountIds,
        visibleCards = visibleCards,
        cardTotals = cardTotals,
        customerAgg = customerAgg,
        availableKinds = availableKinds,
        savingsCustomers = savingsCustomers
    )
}

/**
 * Events emitted by [MainViewModel.transferTransaction] to signal the UI
 * that a transfer has completed (success) or failed (rollback applied).
 */
sealed class TransferEvent {
    data class Success(
        val transactions: List<CustomerTransaction>,
        val fromCustomerId: String,
        val fromCustomerName: String,
        val toCustomerId: String,
        val toCustomerName: String
    ) : TransferEvent()
    data class Failure(
        val transactionId: String,
        val error: String
    ) : TransferEvent()
}

/** Unique grouping key for deduplication — splitGroupId, emiGroupId, or plain transaction id. */
fun CustomerTransaction.groupKey(): String = when {
    splitGroupId.isNotBlank() -> "s:$splitGroupId"
    emiGroupId.isNotBlank() -> "e:$emiGroupId"
    else -> "t:$id"
}

class MainViewModel(
    private var repository: FirebaseRepository = FirebaseRepository()
) : ViewModel() {

    private val _cards = MutableStateFlow<List<CardSummary>>(emptyList())
    val cards: StateFlow<List<CardSummary>> = _cards.asStateFlow()

    private val _customers = MutableStateFlow<List<CustomerSummary>>(emptyList())
    val customers: StateFlow<List<CustomerSummary>> = _customers.asStateFlow()

    private val _deletedCustomers = MutableStateFlow<List<CustomerSummary>>(emptyList())
    val deletedCustomers: StateFlow<List<CustomerSummary>> = _deletedCustomers.asStateFlow()

    private val _driveOperationMessage = MutableStateFlow<String?>(null)
    val driveOperationMessage: StateFlow<String?> = _driveOperationMessage.asStateFlow()

    private val _settlementHistory = MutableStateFlow<List<SettlementHistoryEntry>>(emptyList())
    val settlementHistory: StateFlow<List<SettlementHistoryEntry>> = _settlementHistory.asStateFlow()

    private val _settlementHistoryLoading = MutableStateFlow(false)
    val settlementHistoryLoading: StateFlow<Boolean> = _settlementHistoryLoading.asStateFlow()

    // ── Transfer History state ──────────────────────────────────────────────────
    private val _transferHistory = MutableStateFlow<List<TransferHistoryEntry>>(emptyList())
    val transferHistory: StateFlow<List<TransferHistoryEntry>> = _transferHistory.asStateFlow()

    private val _transferHistoryLoading = MutableStateFlow(false)
    val transferHistoryLoading: StateFlow<Boolean> = _transferHistoryLoading.asStateFlow()

    // ── Transfer event channel (signals UI when Firestore write completes) ──────────
    private val _transferEvents = Channel<TransferEvent>(Channel.BUFFERED)
    val transferEvents = _transferEvents.receiveAsFlow()

    fun loadTransferHistory(transactionId: String) {
        viewModelScope.launch {
            // Clear stale data immediately so we don't flash previous transaction's history
            _transferHistory.value = emptyList()
            _transferHistoryLoading.value = true
            try {
                _transferHistory.value = repository.getTransferHistory(transactionId)
            } catch (e: Exception) {
                android.util.Log.e("TransferHistory", "Failed to load history: ${e.localizedMessage}", e)
                _transferHistory.value = emptyList()
            } finally {
                _transferHistoryLoading.value = false
            }
        }
    }

    // ── Pre-computed dashboard aggregates (computed off main thread) ─────────────────
    private val _aggregates = MutableStateFlow(DashboardAggregates())
    val aggregates: StateFlow<DashboardAggregates> = _aggregates.asStateFlow()

    // ── Frequently-used account IDs across ALL customers (for transaction editor) ──
    private val _frequentAccountIds = MutableStateFlow<Set<String>>(emptySet())
    val frequentAccountIds: StateFlow<Set<String>> = _frequentAccountIds.asStateFlow()

    private var _snapshotVersion = 0L // simple counter, incremented on each data change

    private var _computeAggregatesJob: Job? = null

    /** Starts the background aggregation flow. Call after any data reset. */
    private fun startAggregationFlow() {
        _computeAggregatesJob?.cancel()
        _computeAggregatesJob = viewModelScope.launch(Dispatchers.Default) {
            combine(_cards, _customers) { cards, customers ->
                _snapshotVersion++
                val aggregates = computeAggregates(_snapshotVersion, cards, customers)
                // Also compute frequent account IDs while we're here
                val freq = mutableSetOf<String>()
                for (c in customers) {
                    for (t in c.transactions) {
                        freq.add(t.accountId)
                    }
                }
                _frequentAccountIds.value = freq
                aggregates
            }.collect { result ->
                _aggregates.value = result
            }
        }
    }

    fun loadSettlementHistory(transactionId: String) {
        viewModelScope.launch {
            _settlementHistoryLoading.value = true
            try {
                _settlementHistory.value = repository.getSettlementHistory(transactionId)
            } catch (e: Exception) {
                android.util.Log.e("SettlementHistory", "Failed to load history: ${e.localizedMessage}", e)
                _settlementHistory.value = emptyList()
            } finally {
                _settlementHistoryLoading.value = false
            }
        }
    }

    // ── Customer-wide consolidated settlement history ─────────────────────────
    private val _allSettlementHistory = MutableStateFlow<List<CustomerSettlementEntry>>(emptyList())
    val allSettlementHistory: StateFlow<List<CustomerSettlementEntry>> = _allSettlementHistory.asStateFlow()

    private val _allSettlementHistoryLoading = MutableStateFlow(false)
    val allSettlementHistoryLoading: StateFlow<Boolean> = _allSettlementHistoryLoading.asStateFlow()

    /**
     * Loads settlement history for every transaction in [customer], combining
     * them into a single list sorted newest-first.  Mirrors the web app's
     * "Settlement History" button that shows all payment events under the
     * customer summary card.
     */
    fun loadAllSettlementHistory(customer: CustomerSummary) {
        viewModelScope.launch {
            _allSettlementHistoryLoading.value = true
            _allSettlementHistory.value = emptyList()
            try {
                val txnPairs = customer.transactions
                    .map { it.id to it.name }
                _allSettlementHistory.value = repository.getAllSettlementHistory(txnPairs)
            } catch (e: Exception) {
                android.util.Log.e("AllSettlementHistory", "Failed: ${e.localizedMessage}", e)
                _allSettlementHistory.value = emptyList()
            } finally {
                _allSettlementHistoryLoading.value = false
            }
        }
    }

    // In-progress transaction form draft — survives lock/unlock
    private val _draftTransaction = MutableStateFlow(DraftTransactionState())
    val draftTransaction: StateFlow<DraftTransactionState> = _draftTransaction.asStateFlow()

    fun saveDraftTransaction(draft: DraftTransactionState) {
        _draftTransaction.value = draft
    }

    fun clearDraftTransaction() {
        _draftTransaction.value = DraftTransactionState()
    }

    // ── Optimistic local update ───────────────────────────────────────────────
    // Immediately mutates _customers in-memory so the UI reflects writes instantly.
    // The real Firestore snapshot will overwrite this with server-confirmed data shortly after.
    // NOTE: optimistic ADD is intentionally excluded — it would create temp IDs that break
    // the delete flow. Firestore snapshot arrives fast enough that the add latency is acceptable.

    private fun optimisticallyAddTransaction(customerId: String, txn: CustomerTransaction) {
        _customers.value = _customers.value.map { customer ->
            if (customer.id != customerId) return@map customer
            val newTxns = customer.transactions + txn
            rebuildSummary(customer, newTxns)
        }
    }

    private fun optimisticallyUpdateTransaction(transactionId: String, update: (CustomerTransaction) -> CustomerTransaction) {
        _customers.value = _customers.value.map { customer ->
            val idx = customer.transactions.indexOfFirst { it.id == transactionId }
            if (idx < 0) return@map customer
            val newTxns = customer.transactions.toMutableList().also { it[idx] = update(it[idx]) }
            rebuildSummary(customer, newTxns)
        }
    }

    private fun optimisticallyDeleteTransaction(transactionId: String) {
        _customers.value = _customers.value.map { customer ->
            val newTxns = customer.transactions.filter { it.id != transactionId }
            if (newTxns.size == customer.transactions.size) return@map customer
            rebuildSummary(customer, newTxns)
        }
    }

    /** Rebuilds a CustomerSummary with a new transaction list, recomputing all totals. */
    private fun rebuildSummary(customer: CustomerSummary, newTxns: List<CustomerTransaction>): CustomerSummary {
        val visible = newTxns.filter { it.isVisibleInTransactions() }
        val totalAmount = visible.sumOf { it.amount }
        val settledAmount = visible.filter { it.isSettled }.sumOf { it.amount }
        val partialAmount = visible.filter { !it.isSettled }.sumOf { it.partialPaidAmount }
        val paidAmount = customer.manualPaidAmount + settledAmount + partialAmount
        val sorted = newTxns.sortedWith(
            compareByDescending<CustomerTransaction> { it.transactionDate }.thenByDescending { it.id }
        )
        return customer.copy(
            totalAmount = totalAmount,
            creditDueAmount = paidAmount,
            settledTransactionAmount = settledAmount,
            partialPaidAmount = partialAmount,
            balance = (totalAmount - paidAmount).coerceAtLeast(0.0),
            transactions = sorted,
            snapshotVersion = customer.snapshotVersion + 1
        )
    }

    // Sync status for the customers tab sync button
    enum class SyncState { IDLE, SYNCING, SUCCESS, ERROR }
    data class SyncStatus(val state: SyncState = SyncState.IDLE, val message: String = "")
    private val _syncStatus = MutableStateFlow(SyncStatus())
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()
    private var syncStatusResetJob: Job? = null

    private var autoBackupJob: Job? = null
    private var appContext: Context? = null
    private var profileRepository: UserProfileRepository? = null
    private var settingsRepository: AppSettingsRepository? = null
    private var securityRepository: AppSecurityRepository? = null
    private val driveBackupRepository = DriveBackupRepository()
    // BUG-32, BUG-26: Use AtomicInteger for thread-safe concurrent backup operations
    private val activeDriveOperationCount = AtomicInteger(0)
    // BUG-45: Use AtomicBoolean for thread-safe concurrent state updates
    @Volatile
    private var hasObservedInitialSnapshot = false
    // Snapshots to skip after restore (our own writes echo back as Firestore snapshots)
    // BUG-32: Use synchronized blocks to protect snapshotsToSkip from race conditions
    private var snapshotsToSkip = 0
    private var firestoreListeners: List<com.google.firebase.firestore.ListenerRegistration> = emptyList()

    // Call once from MainActivity to enable auto-backup
    fun initAutoBackup(
        context: Context,
        profileRepo: UserProfileRepository,
        settingsRepo: AppSettingsRepository,
        securityRepo: AppSecurityRepository
    ) {
        if (appContext == null) appContext = context.applicationContext
        profileRepository = profileRepo
        settingsRepository = settingsRepo
        securityRepository = securityRepo
        // Pre-warm encrypted security prefs on IO dispatcher
        viewModelScope.launch(Dispatchers.IO) {
            securityRepo.warmUp()
        }
    }

    init {
        // Grab application context immediately from Firebase so appContext is never null
        appContext = runCatching {
            com.google.firebase.FirebaseApp.getInstance().applicationContext
        }.getOrNull()
        // Pre-warm encrypted prefs on IO — starts in parallel with startListening
        appContext?.let { ctx ->
            viewModelScope.launch(Dispatchers.IO) {
                LocalIdentityRepository.warmUp(ctx)
            }
        }
        startListening()
        startAggregationFlow()
    }

    private fun startListening() {
        firestoreListeners = repository.listenAllData(::handleObservedAppData)
    }

    /**
     * Called after logout + identity reset, or after Google Sign-In switches the identity.
     * Replaces the repository so ALL reads AND writes use the new Firestore path.
     */
    fun reinitialize() {
        autoBackupJob?.cancel()
        _computeAggregatesJob?.cancel()
        // BUG-26: Remove old listeners before clearing to prevent memory leak
        firestoreListeners.forEach { it.remove() }
        firestoreListeners = emptyList()
        activeDriveOperationCount.set(0)
        hasObservedInitialSnapshot = false
        synchronized(this) {
            snapshotsToSkip = 0
        }
        _cards.value = emptyList()
        _customers.value = emptyList()
        _deletedCustomers.value = emptyList()
        _driveOperationMessage.value = null
        _draftTransaction.value = DraftTransactionState()
        _aggregates.value = DashboardAggregates()
        _snapshotVersion = 0L
        repository = FirebaseRepository()
        startListening()
        startAggregationFlow()
    }

    /** Call before a restore so the echoed Firestore snapshots don't trigger auto-backup. */
    fun suppressNextBackups(count: Int = 3) {
        // BUG-32: Thread-safe increment — called on main thread before restore writes
        // Use synchronized to ensure the counter is updated before any snapshot arrives
        synchronized(this) {
            snapshotsToSkip = (snapshotsToSkip + count).coerceAtLeast(0)
        }
    }

    private fun handleObservedAppData(appData: AppData) {
        // BUG-45: Thread-safe check and set for initial snapshot
        // Skip the very first snapshot (initial load — not a user change)
        if (!hasObservedInitialSnapshot) {
            synchronized(this) {
                if (!hasObservedInitialSnapshot) {
                    hasObservedInitialSnapshot = true
                    _cards.value = appData.accounts
                    _customers.value = appData.customers
                    _deletedCustomers.value = appData.deletedCustomers
                    return
                }
            }
        }

        // BUG-32: Thread-safe decrement — use synchronized to protect snapshotsToSkip
        // Skip snapshots echoed back from our own restore writes — do NOT update data
        val shouldSkip = synchronized(this) {
            if (snapshotsToSkip > 0) {
                snapshotsToSkip--
                true
            } else {
                false
            }
        }
        if (shouldSkip) return

        _cards.value = appData.accounts
        _customers.value = appData.customers
        _deletedCustomers.value = appData.deletedCustomers

        // Every subsequent snapshot is a real data change — schedule backup
        scheduleAutoBackup()
    }

    private fun scheduleAutoBackup() {
        val context = appContext ?: return
        autoBackupJob?.cancel()
        autoBackupJob = viewModelScope.launch {
            delay(3_000L) // debounce — wait 3s after last change
            performAutoBackup(context)
        }
    }

    private suspend fun performAutoBackup(context: Context) {
        // BUG-32, BUG-26: Thread-safe check for concurrent operations
        if (activeDriveOperationCount.get() > 0) return
        val token = CredentialManagerHelper.fetchDriveToken(context) ?: return
        // Show syncing state in the customers tab
        _syncStatus.value = SyncStatus(SyncState.SYNCING, "Syncing...")
        syncStatusResetJob?.cancel()
        try {
            // 30-second hard timeout — prevents the backup job from hanging indefinitely
            // when offline or on a weak connection, which was blocking snapshot delivery.
            kotlinx.coroutines.withTimeout(30_000L) {
                val payload = repository.exportBackup(
                    profile = profileRepository?.exportProfileMap() ?: emptyMap(),
                    settings = mapOf(
                        "app" to (settingsRepository?.exportSettings() ?: emptyMap()),
                        "security" to (securityRepository?.exportSettings() ?: emptyMap())
                    )
                )
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val json = BackupJsonSerializer.toJson(payload)
                    driveBackupRepository.uploadBackup(token, json).getOrThrow()
                }
            }
            settingsRepository?.setLastDriveBackupTime(currentTimestampLabel())
            setSyncResult(SyncState.SUCCESS, "Synced successfully.")
            android.util.Log.d("AutoBackup", "Auto-backup succeeded")
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            setSyncResult(SyncState.ERROR, "Sync timed out — will retry on next change.")
            android.util.Log.w("AutoBackup", "Auto-backup timed out")
        } catch (e: java.net.UnknownHostException) {
            setSyncResult(SyncState.ERROR, "No internet connection.")
        } catch (e: java.net.SocketTimeoutException) {
            setSyncResult(SyncState.ERROR, "Connection timed out.")
        } catch (e: com.google.android.gms.auth.GoogleAuthException) {
            setSyncResult(SyncState.ERROR, "Google auth expired. Please sign in again.")
            android.util.Log.w("AutoBackup", "Token fetch failed: ${e.localizedMessage}", e)
        } catch (e: Exception) {
            val msg = e.localizedMessage?.take(60) ?: "Sync failed."
            setSyncResult(SyncState.ERROR, msg)
            android.util.Log.w("AutoBackup", "Auto-backup failed: ${e.localizedMessage}", e)
        }
    }

    fun beginDriveOperation(message: String) {
        // BUG-32, BUG-26: Thread-safe increment
        activeDriveOperationCount.incrementAndGet()
        _driveOperationMessage.value = message
    }

    fun updateDriveOperationMessage(message: String) {
        // BUG-32, BUG-26: Thread-safe check
        if (activeDriveOperationCount.get() > 0) {
            _driveOperationMessage.value = message
        }
    }

    fun finishDriveOperation() {
        // BUG-32, BUG-26: Thread-safe decrement with atomic operation
        val newCount = activeDriveOperationCount.decrementAndGet().coerceAtLeast(0)
        if (newCount == 0) {
            _driveOperationMessage.value = null
        }
    }

    fun recordDriveBackupCompleted() {
        settingsRepository?.setLastDriveBackupTime(currentTimestampLabel())
    }

    fun recordDriveRestoreCompleted() {
        settingsRepository?.setLastDriveRestoreTime(currentTimestampLabel())
    }

    /** Manual sync triggered from the customers tab sync button. */
    fun triggerSync() {
        val context = appContext ?: run {
            _syncStatus.value = SyncStatus(SyncState.ERROR, "App not ready. Try again.")
            return
        }
        // BUG-26: Prevent concurrent sync operations
        if (_syncStatus.value.state == SyncState.SYNCING) return
        // BUG-26: Also block if a drive operation is already in progress
        if (activeDriveOperationCount.get() > 0) return
        viewModelScope.launch {
            _syncStatus.value = SyncStatus(SyncState.SYNCING, "Syncing...")
            syncStatusResetJob?.cancel()
            val token = CredentialManagerHelper.fetchDriveToken(context)
            if (token == null) {
                setSyncResult(SyncState.ERROR, "Not signed in to Google.")
                return@launch
            }
            try {
                // Export from Firestore (uses await() internally — fine on any dispatcher)
                val payload = repository.exportBackup(
                    profile = profileRepository?.exportProfileMap() ?: emptyMap(),
                    settings = mapOf(
                        "app" to (settingsRepository?.exportSettings() ?: emptyMap()),
                        "security" to (securityRepository?.exportSettings() ?: emptyMap())
                    )
                )
                // Upload to Drive on IO (HttpURLConnection — must be off main thread)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val json = BackupJsonSerializer.toJson(payload)
                    driveBackupRepository.uploadBackup(token, json).getOrThrow()
                }
                settingsRepository?.setLastDriveBackupTime(currentTimestampLabel())
                setSyncResult(SyncState.SUCCESS, "Synced successfully.")
            } catch (e: java.net.UnknownHostException) {
                setSyncResult(SyncState.ERROR, "No internet connection.")
            } catch (e: java.net.SocketTimeoutException) {
                setSyncResult(SyncState.ERROR, "Connection timed out.")
            } catch (e: com.google.android.gms.auth.GoogleAuthException) {
                setSyncResult(SyncState.ERROR, "Google auth expired. Please sign in again.")
                android.util.Log.w("ManualSync", "Token fetch failed: ${e.localizedMessage}", e)
            } catch (e: Exception) {
                val msg = e.localizedMessage?.take(60) ?: "Sync failed."
                setSyncResult(SyncState.ERROR, msg)
            }
        }
    }

    private fun setSyncResult(state: SyncState, message: String) {
        _syncStatus.value = SyncStatus(state, message)
        syncStatusResetJob?.cancel()
        syncStatusResetJob = viewModelScope.launch {
            delay(4_000L) // show message for 4 seconds then clear
            _syncStatus.value = SyncStatus(SyncState.IDLE, "")
        }
    }

    /** Full restore from a JSON string — awaits all writes. Suppresses echo backups. */
    suspend fun restoreFromJson(
        json: String,
        profileRepo: UserProfileRepository,
        settingsRepo: AppSettingsRepository,
        securityRepo: AppSecurityRepository
    ) {
        val payload = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.radafiq.data.backup.BackupJsonSerializer.fromJson(json)
        }
        suppressNextBackups(3)
        repository.restoreBackup(payload)
        val profileMap = payload.profile.filterValues { it != null }.mapValues { it.value as Any }
        profileRepo.restoreProfileMapAsync(profileMap)
        settingsRepo.restoreSettings(
            (payload.settings["app"] as? Map<*, *>)?.entries
                ?.associate { it.key.toString() to it.value }.orEmpty()
        )
        securityRepo.restoreSettings(
            (payload.settings["security"] as? Map<*, *>)?.entries
                ?.associate { it.key.toString() to it.value }.orEmpty()
        )
        recordDriveRestoreCompleted()
    }
    suspend fun exportBackup(
        profile: Map<String, Any?>,
        settings: Map<String, Any?>
    ) = repository.exportBackup(profile = profile, settings = settings)

    /** Restore backup using the ViewModel's own repository — awaits all Firestore writes. */
    suspend fun restoreBackup(payload: FirestoreBackupPayload) =
        repository.restoreBackup(payload)

    private fun currentTimestampLabel(): String {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))
    }

    fun addCustomer(name: String, onCreated: (customerId: String) -> Unit = {}) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return
        viewModelScope.launch {
            val id = repository.addCustomer(trimmedName)
            onCreated(id)
        }
    }

    fun deleteCustomer(customerId: String, customerName: String) {
        viewModelScope.launch {
            repository.deleteCustomer(customerId = customerId, customerName = customerName)
        }
    }

    fun restoreCustomer(customerId: String) {
        viewModelScope.launch { repository.restoreCustomer(customerId) }
    }

    fun permanentlyDeleteCustomer(customerId: String, customerName: String) {
        viewModelScope.launch {
            repository.permanentlyDeleteCustomer(customerId = customerId, customerName = customerName)
        }
    }

    fun updateCustomerDueAmount(customerId: String, customerName: String, amount: String) {
        val parsedAmount = amount.toDoubleOrNull() ?: return
        viewModelScope.launch {
            repository.updateCustomerDueAmount(
                customerId = customerId,
                customerName = customerName,
                creditDueAmount = parsedAmount
            )
        }
    }

    fun updateCreditCardDue(
        accountId: String,
        accountName: String,
        amount: String,
        dueDate: String,
        remindersEnabled: Boolean,
        reminderEmail: String,
        reminderWhatsApp: String
    ) {
        val parsedAmount = amount.toDoubleOrNull() ?: return
        // FIX-5: Validate due date format before writing to Firestore
        val trimmedDueDate = dueDate.trim()
        if (trimmedDueDate.isNotBlank()) {
            val parsed = runCatching { java.time.LocalDate.parse(trimmedDueDate) }.getOrNull()
            if (parsed == null) {
                android.util.Log.w("MainViewModel", "updateCreditCardDue: invalid date '$trimmedDueDate' — aborting")
                return
            }
        }
        // FIX-5: Validate email format
        val trimmedEmail = reminderEmail.trim()
        val safeEmail = if (trimmedEmail.isBlank() ||
            android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()
        ) trimmedEmail else ""

        // FIX-5: Validate WhatsApp number — digits only, 7–15 chars
        val trimmedWhatsApp = reminderWhatsApp.trim().filter { it.isDigit() || it == '+' }
        val safeWhatsApp = if (trimmedWhatsApp.length in 7..16) trimmedWhatsApp else ""

        viewModelScope.launch {
            repository.updateCreditCardDue(
                accountId = accountId,
                accountName = accountName,
                dueAmount = parsedAmount,
                dueDate = trimmedDueDate,
                remindersEnabled = remindersEnabled,
                reminderEmail = safeEmail,
                reminderWhatsApp = safeWhatsApp
            )
        }
    }

    fun addTransaction(
        customerId: String,
        transactionName: String,
        customerName: String,
        accountId: String,
        accountName: String,
        accountKind: AccountKind,
        amount: String,
        transactionDate: String,
        personName: String = ""
    ) {
        val parsedAmount = amount.toDoubleOrNull() ?: return
        // BUG-36: Validate required fields before writing to Firestore
        if (parsedAmount <= 0.0) return
        if (customerId.isBlank()) return
        if (transactionName.isBlank()) return
        // For non-person accounts, accountId must be non-blank
        if (accountKind != AccountKind.PERSON && accountId.isBlank()) return
        val safeDate = transactionDate.ifBlank { LocalDate.now().toString() }
        viewModelScope.launch {
            repository.addTransaction(
                customerId = customerId,
                transactionName = transactionName.trim(),
                accountId = accountId,
                accountName = accountName,
                accountKind = accountKind,
                customerName = customerName.trim(),
                amount = parsedAmount,
                transactionDate = safeDate,
                personName = personName.trim()
            )
            // Firestore snapshot listener delivers the confirmed doc — no temp ID needed
        }
    }

    fun addEmiTransactions(
        customerId: String,
        transactionName: String,
        customerName: String,
        accountId: String,
        accountName: String,
        totalAmount: Double,
        transactionDate: String,
        months: Int,
        firstMonthOverride: Double?,
        dateOverrides: Map<Int, String> = mapOf()
    ) {
        // FIX-8: Validate all required fields before creating EMI transactions
        if (months <= 0 || totalAmount <= 0.0) return
        if (customerId.isBlank()) return
        if (transactionName.isBlank()) return
        if (accountId.isBlank()) return
        val baseDate = runCatching { LocalDate.parse(transactionDate) }.getOrDefault(LocalDate.now())

        // Intentional design: baseEmi = totalAmount / months is the standard instalment amount.
        // The first-month override is an independent amount (e.g. a down payment or processing fee)
        // and does NOT affect the remaining instalments — each of months 2..N stays at baseEmi.
        val baseEmi = totalAmount / months
        val firstEmi = firstMonthOverride?.takeIf { it > 0.0 } ?: baseEmi

        val groupId = java.util.UUID.randomUUID().toString()
        viewModelScope.launch {
            try {
                val instalments = (0 until months).map { i ->
                    val emiAmount = if (i == 0) firstEmi else baseEmi
                    // dueDate = same day next month for each instalment (baseDate + i+1 months)
                    // transactionDate = dueDate - 20 days (so it appears in the transaction tab
                    // 20 days before payment is due, giving the user time to prepare)
                    val dueDate = dateOverrides[i]?.let {
                        runCatching { LocalDate.parse(it) }.getOrNull()
                    } ?: baseDate.plusMonths((i + 1).toLong())
                    val emiDate = dueDate.minusDays(20)
                    mutableMapOf<String, Any>(
                        "customerId" to customerId,
                        "transactionName" to "${transactionName.trim()} — EMI ${i + 1}/$months",
                        "accountId" to accountId,
                        "accountName" to accountName,
                        "accountType" to AccountKind.CREDIT_CARD.storageValue,
                        "customerName" to customerName.trim(),
                        "amount" to emiAmount,
                        "transactionDate" to emiDate.toString(),
                        "givenDate" to emiDate.toString(),
                        "dueDate" to dueDate.toString(),
                        "emiGroupId" to groupId,
                        "emiIndex" to i.toLong(),
                        "emiTotal" to months.toLong(),
                        "isSettled" to false
                    )
                }
                repository.addEmiTransactionsBatch(instalments = instalments)
            } catch (e: Exception) {
                android.util.Log.e("EMI", "Failed to save EMI transactions: ${e.localizedMessage}", e)
            }
        }
    }

    fun updateTransaction(
        transactionId: String,
        transactionName: String,
        accountId: String,
        accountName: String,
        accountKind: AccountKind,
        amount: String,
        transactionDate: String,
        personName: String = "",
        dueDate: String = ""
    ) {
        val parsedAmount = amount.toDoubleOrNull() ?: return
        // BUG-36: Validate required fields before writing to Firestore
        if (parsedAmount <= 0.0) return
        if (transactionId.isBlank()) return
        if (transactionName.isBlank()) return
        val safeDate = transactionDate.ifBlank { LocalDate.now().toString() }
        // Optimistic update — reflect in UI immediately
        optimisticallyUpdateTransaction(transactionId) { old ->
            old.copy(
                name = transactionName.trim(),
                accountId = accountId,
                accountName = accountName,
                accountKind = accountKind,
                amount = parsedAmount,
                personName = personName.trim(),
                dueDate = dueDate
            ).withStoredDate(safeDate)
        }
        viewModelScope.launch {
            repository.updateTransaction(
                transactionId = transactionId,
                transactionName = transactionName.trim(),
                accountId = accountId,
                accountName = accountName,
                accountKind = accountKind,
                amount = parsedAmount,
                transactionDate = safeDate,
                personName = personName.trim(),
                dueDate = dueDate
            )
        }
    }

    fun deleteTransaction(transactionId: String) {
        android.util.Log.d("DeleteTxn", "deleteTransaction called: id='$transactionId'")
        if (transactionId.isBlank() || transactionId.startsWith("optimistic_")) {
            android.util.Log.w("DeleteTxn", "Skipped: blank or temp id")
            return
        }
        optimisticallyDeleteTransaction(transactionId)
        viewModelScope.launch {
            try {
                repository.deleteTransaction(transactionId)
                android.util.Log.d("DeleteTxn", "Firestore delete SUCCESS: $transactionId")
            } catch (e: Exception) {
                android.util.Log.e("DeleteTxn", "Firestore delete FAILED: $transactionId — ${e.localizedMessage}", e)
            }
        }
    }

    /**
     * Transfer a transaction (and its split/EMI group siblings) from one customer to another.
     * Optimistically updates both the source and target customer's transaction lists.
     * Emits [TransferEvent.Success] on Firestore write success, or [TransferEvent.Failure]
     * on failure (with automatic rollback of the optimistic update).
     */
    fun transferTransaction(
        transaction: CustomerTransaction,
        newCustomerId: String,
        newCustomerName: String
    ) {
        val transactionId = transaction.id
        if (transactionId.isBlank()) return
        // Guard against self-transfer (defense-in-depth — UI should prevent this too)
        if (transaction.customerId == newCustomerId) return

        // Pre-identify siblings from the SOURCE customer (siblings still live on source)
        val sourceCustomer = _customers.value.find { it.id == transaction.customerId }
        val sourceSiblings = sourceCustomer?.transactions?.filter {
            (transaction.splitGroupId.isNotBlank() && it.splitGroupId == transaction.splitGroupId) ||
            (transaction.emiGroupId.isNotBlank() && it.emiGroupId == transaction.emiGroupId)
        }?.filter { it.id != transactionId } ?: emptyList()
        val oldCustomerName = sourceCustomer?.name ?: ""

        // Save previous state for rollback on Firestore write failure
        val previousCustomers = _customers.value

        // Optimistic update
        _customers.value = _customers.value.map { customer ->
            if (customer.id == transaction.customerId) {
                // Remove transaction AND all siblings from source
                val idsToRemove = (listOf(transactionId) + sourceSiblings.map { it.id }).toSet()
                val cleaned = customer.transactions.filter { it.id !in idsToRemove }
                rebuildSummary(customer, cleaned)
            } else if (customer.id == newCustomerId) {
                // Add transaction AND all siblings to target
                val siblingsUpdated = sourceSiblings.map { it.copy(customerId = newCustomerId) }
                val updatedTxn = transaction.copy(customerId = newCustomerId)
                val newTxns = customer.transactions + siblingsUpdated + updatedTxn
                rebuildSummary(customer, newTxns)
            } else {
                customer
            }
        }

        viewModelScope.launch {
            try {
                repository.transferTransaction(
                    transactionId = transactionId,
                    newCustomerId = newCustomerId,
                    newCustomerName = newCustomerName,
                    oldCustomerId = transaction.customerId,
                    oldCustomerName = oldCustomerName,
                    splitGroupId = transaction.splitGroupId,
                    emiGroupId = transaction.emiGroupId
                )
                // Signal UI that transfer committed — only now should it set undo state
                _transferEvents.send(TransferEvent.Success(
                    transactions = listOf(transaction) + sourceSiblings,
                    fromCustomerId = transaction.customerId,
                    fromCustomerName = oldCustomerName,
                    toCustomerId = newCustomerId,
                    toCustomerName = newCustomerName
                ))
            } catch (e: Exception) {
                android.util.Log.e("Transfer", "transferTransaction FAILED: ${e.localizedMessage}", e)
                // Rollback optimistic update
                _customers.value = previousCustomers
                _transferEvents.send(TransferEvent.Failure(
                    transactionId = transactionId,
                    error = e.localizedMessage ?: "Unknown error"
                ))
            }
        }
    }

    /**
     * Updates the dueDate for all parts of an EMI installment (handles split installments
     * where multiple transaction IDs share the same emiIndex).
     * transactionDate is derived from dueDate at read time — no extra write needed.
     */
    fun updateEmiDueDate(transactionIds: List<String>, dueDate: String) {
        if (transactionIds.isEmpty() || dueDate.isBlank()) return
        viewModelScope.launch {
            transactionIds.forEach { id ->
                try {
                    repository.updateEmiDueDate(id, dueDate)
                } catch (e: Exception) {
                    android.util.Log.e("EMI", "Failed to update dueDate for $id: ${e.localizedMessage}", e)
                }
            }
        }
    }

    fun addPartialPayment(transactionId: String, amount: String) {
        val parsedAmount = amount.toDoubleOrNull() ?: return
        if (parsedAmount <= 0.0) return
        val transaction = _customers.value
            .flatMap { it.transactions }
            .find { it.id == transactionId }
        if (transaction != null) {
            val maxAllowed = (transaction.amount - transaction.partialPaidAmount).coerceAtLeast(0.0)
            if (maxAllowed <= 0.0) return
            val safeAmount = parsedAmount.coerceAtMost(maxAllowed)
            // Optimistic update
            optimisticallyUpdateTransaction(transactionId) { old ->
                old.copy(partialPaidAmount = old.partialPaidAmount + safeAmount)
            }
            viewModelScope.launch {
                repository.addPartialPayment(
                    transactionId = transactionId,
                    amount = safeAmount,
                    date = LocalDate.now().toString()
                )
            }
        } else {
            viewModelScope.launch {
                repository.addPartialPayment(
                    transactionId = transactionId,
                    amount = parsedAmount,
                    date = LocalDate.now().toString()
                )
            }
        }
    }

    fun toggleTransactionSettled(transactionId: String, isSettled: Boolean) {
        val settledDate = if (isSettled) LocalDate.now().toString() else ""
        // Optimistic update
        optimisticallyUpdateTransaction(transactionId) { old ->
            old.copy(isSettled = isSettled, settledDate = settledDate)
        }
        viewModelScope.launch {
            repository.toggleTransactionSettled(
                transactionId = transactionId,
                isSettled = isSettled,
                settledDate = settledDate
            )
        }
    }

    /**
     * Suspend version of toggleTransactionSettled — awaits the Firestore write.
     * Use this when sequential ordering of writes matters (e.g. payment distribution).
     */
    suspend fun toggleTransactionSettledAwait(transactionId: String, isSettled: Boolean) {
        repository.toggleTransactionSettled(
            transactionId = transactionId,
            isSettled = isSettled,
            settledDate = if (isSettled) LocalDate.now().toString() else ""
        )
    }

    /**
     * Suspend version of addPartialPayment — awaits the Firestore write.
     * Use this when sequential ordering of writes matters (e.g. payment distribution).
     */
    suspend fun addPartialPaymentAwait(transactionId: String, amount: Double) {
        if (amount <= 0.0) return
        repository.addPartialPayment(
            transactionId = transactionId,
            amount = amount,
            date = LocalDate.now().toString()
        )
    }

    fun addSplitTransactions(
        customerId: String,
        customerName: String,
        transactionName: String,
        transactionDate: String,
        splits: List<SplitEntry>
    ) {
        if (splits.isEmpty()) return
        val groupId = java.util.UUID.randomUUID().toString()
        viewModelScope.launch {
            val docs = splits.mapNotNull { split ->
                val amount = split.amount.toDoubleOrNull() ?: return@mapNotNull null
                if (amount <= 0.0) return@mapNotNull null
                val isPerson = split.accountKind == com.radafiq.data.models.AccountKind.PERSON
                // Use accountName as fallback id for non-person entries where id may be blank
                val accountId = when {
                    isPerson -> "person_${split.personName.trim().lowercase().replace(" ", "_")}"
                    split.accountId.isNotBlank() -> split.accountId
                    split.accountName.isNotBlank() -> split.accountName.trim().lowercase().replace(" ", "_")
                    else -> return@mapNotNull null  // no usable account — skip
                }
                val accountName = if (isPerson) split.personName.trim() else split.accountName.trim()
                if (accountName.isBlank()) return@mapNotNull null
                mutableMapOf<String, Any>(
                    "customerId"      to customerId,
                    "customerName"    to customerName,
                    "transactionName" to transactionName,
                    "accountId"       to accountId,
                    "accountName"     to accountName,
                    "accountType"     to split.accountKind.storageValue,
                    "amount"          to amount,
                    "transactionDate" to transactionDate.ifBlank { LocalDate.now().toString() },
                    "givenDate"       to transactionDate.ifBlank { LocalDate.now().toString() },
                    "splitGroupId"    to groupId
                ).also { data ->
                    if (isPerson && split.personName.isNotBlank()) data["personName"] = split.personName.trim()
                }
            }
            if (docs.isNotEmpty()) repository.addSplitTransactionsBatch(docs)
        }
    }

    fun convertEmiInstallmentToSplit(
        originalTransactionId: String,
        customerId: String,
        customerName: String,
        transactionName: String,
        transactionDate: String,
        emiGroupId: String,
        emiIndex: Int,
        emiTotal: Int,
        splits: List<SplitEntry>
    ) {
        if (splits.isEmpty()) return
        val splitGroupId = java.util.UUID.randomUUID().toString()
        viewModelScope.launch {
            val docs = splits.mapNotNull { split ->
                val amount = split.amount.toDoubleOrNull() ?: return@mapNotNull null
                if (amount <= 0.0) return@mapNotNull null
                val isPerson = split.accountKind == com.radafiq.data.models.AccountKind.PERSON
                val accountId = when {
                    isPerson -> "person_${split.personName.trim().lowercase().replace(" ", "_")}"
                    split.accountId.isNotBlank() -> split.accountId
                    split.accountName.isNotBlank() -> split.accountName.trim().lowercase().replace(" ", "_")
                    else -> return@mapNotNull null
                }
                val accountName = if (isPerson) split.personName.trim() else split.accountName.trim()
                if (accountName.isBlank()) return@mapNotNull null
                mutableMapOf<String, Any>(
                    "customerId"      to customerId,
                    "customerName"    to customerName,
                    "transactionName" to transactionName,
                    "accountId"       to accountId,
                    "accountName"     to accountName,
                    "accountType"     to split.accountKind.storageValue,
                    "amount"          to amount,
                    "transactionDate" to transactionDate.ifBlank { LocalDate.now().toString() },
                    "givenDate"       to transactionDate.ifBlank { LocalDate.now().toString() },
                    "splitGroupId"    to splitGroupId,
                    "emiGroupId"      to emiGroupId,
                    "emiIndex"        to emiIndex,
                    "emiTotal"        to emiTotal
                ).also { data ->
                    if (isPerson && split.personName.isNotBlank()) data["personName"] = split.personName.trim()
                }
            }
            if (docs.isNotEmpty()) {
                repository.convertEmiInstallmentToSplit(originalTransactionId, docs)
            }
        }
    }

    fun addPayment(accountId: String, accountName: String, accountKind: AccountKind, amount: String, date: String = LocalDate.now().toString()) {
        val parsedAmount = amount.toDoubleOrNull() ?: return
        if (parsedAmount <= 0.0) return
        val safeDate = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now()).toString()
        viewModelScope.launch {
            repository.addPayment(
                accountId = accountId,
                accountName = accountName,
                accountKind = accountKind,
                amount = parsedAmount,
                date = safeDate
            )
        }
    }

    // ── Savings ───────────────────────────────────────────────────────────────

    private fun optimisticallyAddSavingsEntry(entry: com.radafiq.data.models.SavingsEntry) {
        _customers.value = _customers.value.map { customer ->
            if (customer.id != entry.customerId) return@map customer
            val newEntries = customer.savingsEntries + entry
            val sortedEntries = newEntries.sortedByDescending { it.date }
            val newBalance = sortedEntries.sumOf {
                if (it.type == com.radafiq.data.models.SavingsType.DEPOSIT) it.amount else -it.amount
            }
            customer.copy(
                savingsEntries = sortedEntries,
                savingsBalance = newBalance,
                snapshotVersion = customer.snapshotVersion + 1
            )
        }
    }

    private fun optimisticallyDeleteSavingsEntry(entryId: String) {
        _customers.value = _customers.value.map { customer ->
            val newEntries = customer.savingsEntries.filter { it.id != entryId }
            if (newEntries.size == customer.savingsEntries.size) return@map customer
            val newBalance = newEntries.sumOf {
                if (it.type == com.radafiq.data.models.SavingsType.DEPOSIT) it.amount else -it.amount
            }
            customer.copy(
                savingsEntries = newEntries.sortedByDescending { it.date },
                savingsBalance = newBalance,
                snapshotVersion = customer.snapshotVersion + 1
            )
        }
    }

    fun addSavingsDeposit(customerId: String, customerName: String, amount: String, note: String, bankAccountId: String = "", bankAccountName: String = "", date: String = LocalDate.now().toString()) {
        val parsed = amount.toDoubleOrNull() ?: return
        if (parsed <= 0.0) return
        val tempEntry = com.radafiq.data.models.SavingsEntry(
            id = "optimistic_savings_${System.currentTimeMillis()}",
            customerId = customerId,
            customerName = customerName,
            amount = parsed,
            type = com.radafiq.data.models.SavingsType.DEPOSIT,
            note = note.trim(),
            date = date,
            bankAccountId = bankAccountId,
            bankAccountName = bankAccountName
        )
        optimisticallyAddSavingsEntry(tempEntry)
        viewModelScope.launch {
            repository.addSavingsEntry(
                customerId = customerId,
                customerName = customerName,
                amount = parsed,
                type = com.radafiq.data.models.SavingsType.DEPOSIT,
                note = note.trim(),
                date = date,
                bankAccountId = bankAccountId,
                bankAccountName = bankAccountName
            )
        }
    }

    fun addSavingsWithdrawal(customerId: String, customerName: String, amount: String, note: String, bankAccountId: String = "", bankAccountName: String = "", date: String = LocalDate.now().toString()) {
        val parsed = amount.toDoubleOrNull() ?: return
        if (parsed <= 0.0) return
        val tempEntry = com.radafiq.data.models.SavingsEntry(
            id = "optimistic_savings_${System.currentTimeMillis()}",
            customerId = customerId,
            customerName = customerName,
            amount = parsed,
            type = com.radafiq.data.models.SavingsType.WITHDRAWAL,
            note = note.trim(),
            date = date,
            bankAccountId = bankAccountId,
            bankAccountName = bankAccountName
        )
        optimisticallyAddSavingsEntry(tempEntry)
        viewModelScope.launch {
            repository.addSavingsEntry(
                customerId = customerId,
                customerName = customerName,
                amount = parsed,
                type = com.radafiq.data.models.SavingsType.WITHDRAWAL,
                note = note.trim(),
                date = date,
                bankAccountId = bankAccountId,
                bankAccountName = bankAccountName
            )
        }
    }

    fun deleteSavingsEntry(entryId: String) {
        if (!entryId.startsWith("optimistic_savings_")) {
            optimisticallyDeleteSavingsEntry(entryId)
        }
        viewModelScope.launch { repository.deleteSavingsEntry(entryId) }
    }

    override fun onCleared() {
        super.onCleared()
        autoBackupJob?.cancel()
        syncStatusResetJob?.cancel()
        _computeAggregatesJob?.cancel()
        firestoreListeners.forEach { it.remove() }
    }
}
