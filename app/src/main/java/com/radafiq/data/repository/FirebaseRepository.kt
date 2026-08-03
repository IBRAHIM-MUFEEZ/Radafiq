package com.radafiq.data.repository

import com.radafiq.data.models.AccountKind
import com.radafiq.data.models.AppData
import com.radafiq.data.models.BackupRecord
import com.radafiq.data.models.CardSummary
import com.radafiq.data.models.CustomerSettlementEntry
import com.radafiq.data.models.CustomerSummary
import com.radafiq.data.models.CustomerTransaction
import com.radafiq.data.models.FirestoreBackupPayload
import com.radafiq.data.models.IndianAccountCatalog
import com.radafiq.data.models.SavingsEntry
import com.radafiq.data.models.SavingsType
import com.radafiq.data.models.SettlementHistoryEntry
import com.radafiq.data.models.TransferHistoryEntry
import com.radafiq.data.auth.LocalIdentityRepository
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

class FirebaseRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun addCustomer(name: String): String {
        val data = hashMapOf(
            "name" to name,
            "creditDueAmount" to 0.0,
            "isDeleted" to false
        )
        val ref = customersCollection().add(data).await()
        return ref.id
    }

    suspend fun deleteCustomer(
        customerId: String,
        customerName: String
    ) {
        customersCollection()
            .document(customerId)
            .set(
                mapOf(
                    "name" to customerName,
                    "isDeleted" to true,
                    "deletedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun restoreCustomer(customerId: String) {
        customersCollection()
            .document(customerId)
            .set(
                mapOf(
                    "isDeleted" to false,
                    "deletedAt" to FieldValue.delete()
                ),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun permanentlyDeleteCustomer(
        customerId: String,
        customerName: String
    ) {
        val customerRef = customersCollection().document(customerId)
        val transactionSnapshot = transactionsCollection()
            .whereEqualTo("customerId", customerId)
            .get()
            .await()
        val legacyTransactionSnapshot = transactionsCollection()
            .whereEqualTo("customerName", customerName)
            .get()
            .await()

        val batch = db.batch()
        val deletedTransactionIds = mutableSetOf<String>()
        batch.delete(customerRef)
        transactionSnapshot.documents.forEach { transaction ->
            if (deletedTransactionIds.add(transaction.id)) {
                batch.delete(transaction.reference)
            }
        }
        legacyTransactionSnapshot.documents.forEach { transaction ->
            if (deletedTransactionIds.add(transaction.id)) {
                batch.delete(transaction.reference)
            }
        }
        batch.commit().await()
    }

    suspend fun updateCustomerDueAmount(
        customerId: String,
        customerName: String,
        creditDueAmount: Double
    ) {
        customersCollection()
            .document(customerId)
            .set(
                mapOf(
                    "name" to customerName,
                    "creditDueAmount" to creditDueAmount
                ),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun updateCreditCardDue(
        accountId: String,
        accountName: String,
        dueAmount: Double,
        dueDate: String,
        remindersEnabled: Boolean,
        reminderEmail: String,
        reminderWhatsApp: String
    ) {
        accountsCollection()
            .document(accountId)
            .set(
                mapOf(
                    "name" to accountName,
                    "accountType" to AccountKind.CREDIT_CARD.storageValue,
                    "type" to AccountKind.CREDIT_CARD.storageValue,
                    "dueAmount" to dueAmount,
                    "dueDate" to dueDate,
                    "remindersEnabled" to remindersEnabled,
                    "reminderEmail" to reminderEmail,
                    "reminderWhatsApp" to reminderWhatsApp
                ),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun toggleTransactionSettled(
        transactionId: String,
        isSettled: Boolean,
        settledDate: String
    ) {
        transactionsCollection()
            .document(transactionId)
            .set(
                mapOf(
                    "isSettled" to isSettled,
                    "settledDate" to if (isSettled) settledDate else FieldValue.delete()
                ),
                SetOptions.merge()
            )
            .await()
        // Record settlement history
        val currentDoc = transactionsCollection().document(transactionId).get().await()
        val previousPartialPaid = currentDoc.getDouble("partialPaidAmount") ?: 0.0
        val previousIsSettled = currentDoc.getBoolean("isSettled") != true
        recordSettlementHistory(
            transactionId = transactionId,
            customerId = currentDoc.getString("customerId") ?: "",
            type = if (isSettled) "settled" else "unsettled",
            amount = currentDoc.getDouble("amount") ?: 0.0,
            previousPartialPaid = previousPartialPaid,
            newPartialPaid = previousPartialPaid,
            previousIsSettled = previousIsSettled,
            newIsSettled = isSettled,
            date = if (isSettled) settledDate else java.time.LocalDate.now().toString()
        )
    }

    suspend fun addTransaction(
        customerId: String,
        transactionName: String,
        accountId: String,
        accountName: String,
        accountKind: AccountKind,
        customerName: String,
        amount: Double,
        transactionDate: String,
        personName: String = "",
        splitGroupId: String = "",
        dueDate: String = "",
        emiGroupId: String = "",
        emiIndex: Int = 0,
        emiTotal: Int = 0
    ) {
        val data = mutableMapOf<String, Any>(
            "customerId" to customerId,
            "transactionName" to transactionName,
            "accountId" to accountId,
            "accountName" to accountName,
            "accountType" to accountKind.storageValue,
            "customerName" to customerName,
            "amount" to amount,
            "transactionDate" to transactionDate,
            "givenDate" to transactionDate
        )
        if (personName.isNotBlank()) data["personName"] = personName
        if (splitGroupId.isNotBlank()) data["splitGroupId"] = splitGroupId
        if (dueDate.isNotBlank()) data["dueDate"] = dueDate
        if (emiGroupId.isNotBlank()) {
            data["emiGroupId"] = emiGroupId
            data["emiIndex"] = emiIndex
            data["emiTotal"] = emiTotal
        }
        transactionsCollection().add(data).await()
    }

    suspend fun addSplitTransactionsBatch(splits: List<Map<String, Any>>) {
        val batch = db.batch()
        splits.forEach { data ->
            batch.set(transactionsCollection().document(), data)
        }
        batch.commit().await()
    }

    /**
     * Convert a single EMI installment into split entries.
     * Deletes the original transaction and creates new split docs that preserve
     * emiGroupId/emiIndex/emiTotal so the installment still belongs to the EMI plan.
     */
    suspend fun convertEmiInstallmentToSplit(
        originalTransactionId: String,
        splits: List<Map<String, Any>>
    ) {
        val batch = db.batch()
        // Delete the original single-account EMI installment
        batch.delete(transactionsCollection().document(originalTransactionId))
        // Create replacement split docs (each carries emiGroupId etc.)
        splits.forEach { data ->
            batch.set(transactionsCollection().document(), data)
        }
        batch.commit().await()
    }

    suspend fun addEmiTransactionsBatch(instalments: List<Map<String, Any>>) {
        // Firestore batch limit is 500 writes; EMI plans are well within that
        val batch = db.batch()
        instalments.forEach { data ->
            val ref = transactionsCollection().document()
            batch.set(ref, data)
        }
        batch.commit().await()
    }

    /**
     * Only updates the dueDate field on an EMI installment document.
     * transactionDate is computed from dueDate at read time, so no write needed for it.
     */
    suspend fun updateEmiDueDate(transactionId: String, dueDate: String) {
        transactionsCollection()
            .document(transactionId)
            .update("dueDate", dueDate)
            .await()
    }

    suspend fun updateTransaction(
        transactionId: String,
        transactionName: String,
        accountId: String,
        accountName: String,
        accountKind: AccountKind,
        amount: Double,
        transactionDate: String,
        personName: String = "",
        dueDate: String = ""
    ) {
        val data = mutableMapOf<String, Any?>(
            "transactionName" to transactionName,
            "accountId" to accountId,
            "accountName" to accountName,
            "accountType" to accountKind.storageValue,
            "amount" to amount,
            "transactionDate" to transactionDate,
            "givenDate" to transactionDate
        )
        if (dueDate.isNotBlank()) data["dueDate"] = dueDate
        else data["dueDate"] = FieldValue.delete()
        if (personName.isNotBlank()) data["personName"] = personName
        else data["personName"] = FieldValue.delete()

        @Suppress("UNCHECKED_CAST")
        transactionsCollection()
            .document(transactionId)
            .set(data as Map<String, Any>, SetOptions.merge())
            .await()
    }

    suspend fun deleteTransaction(transactionId: String) {
        transactionsCollection().document(transactionId).delete().await()
    }

    /**
     * Transfer a transaction (and its split/EMI group siblings) from one customer to another.
     * Only updates customerId and customerName — preserves amount, date, account, settlement,
     * partial payments, and all other fields.
     *
     * @param oldCustomerId Current owner of the transaction (passed from ViewModel, no re-fetch).
     * @param oldCustomerName Current owner name.
     */
    suspend fun transferTransaction(
        transactionId: String,
        newCustomerId: String,
        newCustomerName: String,
        oldCustomerId: String,
        oldCustomerName: String,
        splitGroupId: String = "",
        emiGroupId: String = ""
    ) {
        val refsToUpdate = mutableSetOf(transactionId)

        // Query split-group and EMI-group siblings in parallel (independent reads)
        coroutineScope {
            val splitDeferred = if (splitGroupId.isNotBlank()) async {
                transactionsCollection()
                    .whereEqualTo("splitGroupId", splitGroupId)
                    .get()
                    .await()
            } else null

            val emiDeferred = if (emiGroupId.isNotBlank()) async {
                transactionsCollection()
                    .whereEqualTo("emiGroupId", emiGroupId)
                    .get()
                    .await()
            } else null

            splitDeferred?.await()?.documents?.forEach { refsToUpdate.add(it.id) }
            emiDeferred?.await()?.documents?.forEach { refsToUpdate.add(it.id) }
        }

        val batch = db.batch()
        refsToUpdate.forEach { id ->
            batch.update(
                transactionsCollection().document(id),
                mapOf("customerId" to newCustomerId, "customerName" to newCustomerName)
            )
        }

        // Record transfer history under ALL sibling transaction IDs in the same batch
        // so clicking "View Transfer History" on any sibling shows the same data.
        refsToUpdate.forEach { id ->
            val ref = transferHistoryCollection(id).document()
            batch.set(ref, mapOf(
                "fromCustomerId" to oldCustomerId,
                "fromCustomerName" to oldCustomerName,
                "toCustomerId" to newCustomerId,
                "toCustomerName" to newCustomerName,
                "transactionIds" to refsToUpdate.toList(),
                "timestamp" to FieldValue.serverTimestamp()
            ))
        }

        batch.commit().await()
    }

    suspend fun addPartialPayment(
        transactionId: String,
        amount: Double,
        date: String
    ) {
        transactionsCollection()
            .document(transactionId)
            .set(
                mapOf(
                    "partialPaidAmount" to FieldValue.increment(amount),
                    "lastPartialPaymentDate" to date
                ),
                SetOptions.merge()
            )
            .await()
        // Record partial payment history
        val currentDoc = transactionsCollection().document(transactionId).get().await()
        val previousPartialPaid = (currentDoc.getDouble("partialPaidAmount") ?: 0.0) - amount
        recordSettlementHistory(
            transactionId = transactionId,
            customerId = currentDoc.getString("customerId") ?: "",
            type = "partial",
            amount = amount,
            previousPartialPaid = previousPartialPaid,
            newPartialPaid = previousPartialPaid + amount,
            previousIsSettled = currentDoc.getBoolean("isSettled") == true,
            newIsSettled = false,
            date = date
        )
    }

    suspend fun addPayment(
        accountId: String,
        accountName: String,
        accountKind: AccountKind,
        amount: Double,
        date: String
    ) {
        val data = hashMapOf(
            "accountId" to accountId,
            "accountName" to accountName,
            "accountType" to accountKind.storageValue,
            "amount" to amount,
            "date" to date
        )

        paymentsCollection().add(data).await()
    }

    // ── Savings ───────────────────────────────────────────────────────────────

    suspend fun addSavingsEntry(
        customerId: String,
        customerName: String,
        amount: Double,
        type: SavingsType,
        note: String,
        date: String,
        bankAccountId: String = "",
        bankAccountName: String = ""
    ) {
        savingsCollection().add(
            hashMapOf(
                "customerId"      to customerId,
                "customerName"    to customerName,
                "amount"          to amount,
                "type"            to type.storageValue,
                "note"            to note,
                "date"            to date,
                "bankAccountId"   to bankAccountId,
                "bankAccountName" to bankAccountName
            )
        ).await()
    }

    suspend fun deleteSavingsEntry(entryId: String) {
        savingsCollection().document(entryId).delete().await()
    }

    fun listenAllData(onResult: (AppData) -> Unit): List<com.google.firebase.firestore.ListenerRegistration> {
        val root = userRoot()

        var latestCustomers: List<com.google.firebase.firestore.DocumentSnapshot> = emptyList()
        var latestAccounts: List<com.google.firebase.firestore.DocumentSnapshot> = emptyList()
        var latestTransactions: List<com.google.firebase.firestore.DocumentSnapshot> = emptyList()
        var latestPayments: List<com.google.firebase.firestore.DocumentSnapshot> = emptyList()
        var latestSavings: List<com.google.firebase.firestore.DocumentSnapshot> = emptyList()

        var customersReady = false
        var accountsReady = false
        var transactionsReady = false
        var paymentsReady = false
        var savingsReady = false

        var snapshotVersion = 0L

        fun notifyIfReady() {
            if (customersReady && accountsReady && transactionsReady && paymentsReady && savingsReady) {
                snapshotVersion++
                onResult(
                    buildAppData(
                        customers = latestCustomers,
                        accounts = latestAccounts,
                        transactions = latestTransactions,
                        payments = latestPayments,
                        savings = latestSavings,
                        snapshotVersion = snapshotVersion
                    )
                )
            }
        }

        val r1 = root.collection("customers").addSnapshotListener { snap, error ->
            // BUG-31: Check for Firestore errors before processing snapshot
            if (error != null) {
                android.util.Log.w("FirebaseRepository", "Customers listener error: ${error.localizedMessage}", error)
                return@addSnapshotListener
            }
            latestCustomers = snap?.documents.orEmpty()
            customersReady = true
            notifyIfReady()
        }

        val r2 = root.collection("accounts").addSnapshotListener { snap, error ->
            // BUG-31: Check for Firestore errors before processing snapshot
            if (error != null) {
                android.util.Log.w("FirebaseRepository", "Accounts listener error: ${error.localizedMessage}", error)
                return@addSnapshotListener
            }
            latestAccounts = snap?.documents.orEmpty()
            accountsReady = true
            notifyIfReady()
        }

        val r3 = root.collection("transactions").addSnapshotListener { snap, error ->
            // BUG-31: Check for Firestore errors before processing snapshot
            if (error != null) {
                android.util.Log.w("FirebaseRepository", "Transactions listener error: ${error.localizedMessage}", error)
                return@addSnapshotListener
            }
            latestTransactions = snap?.documents.orEmpty()
            transactionsReady = true
            notifyIfReady()
        }

        val r4 = root.collection("payments").addSnapshotListener { snap, error ->
            // BUG-31: Check for Firestore errors before processing snapshot
            if (error != null) {
                android.util.Log.w("FirebaseRepository", "Payments listener error: ${error.localizedMessage}", error)
                return@addSnapshotListener
            }
            latestPayments = snap?.documents.orEmpty()
            paymentsReady = true
            notifyIfReady()
        }

        val r5 = root.collection("savings").addSnapshotListener { snap, error ->
            // BUG-31: Check for Firestore errors before processing snapshot
            if (error != null) {
                android.util.Log.w("FirebaseRepository", "Savings listener error: ${error.localizedMessage}", error)
                return@addSnapshotListener
            }
            latestSavings = snap?.documents.orEmpty()
            savingsReady = true
            notifyIfReady()
        }

        return listOf(r1, r2, r3, r4, r5)
    }

    suspend fun exportBackup(
        profile: Map<String, Any?> = emptyMap(),
        settings: Map<String, Any?> = emptyMap()
    ): FirestoreBackupPayload {
        // Use Source.CACHE so exportBackup reads from the local Firestore cache rather than
        // making network requests. The snapshot listeners keep the cache up-to-date, so the
        // data is always fresh. This prevents exportBackup from hanging when offline or on a
        // weak connection, which was blocking the viewModelScope and delaying UI updates.
        val customers = customersCollection().get(Source.CACHE).await().documents.map { document ->
            BackupRecord(
                id = document.id,
                fields = mapOf(
                    "name" to document.getString("name").orEmpty(),
                    "creditDueAmount" to (document.getDouble("creditDueAmount") ?: 0.0),
                    "isDeleted" to (document.getBoolean("isDeleted") == true)
                )
            )
        }
        val accounts = accountsCollection().get(Source.CACHE).await().documents.map { document ->
            BackupRecord(
                id = document.id,
                fields = mapOf(
                    "name" to document.getString("name").orEmpty(),
                    "accountType" to (
                        document.getString("accountType")
                            ?: document.getString("type")
                            ?: AccountKind.CREDIT_CARD.storageValue
                        ),
                    "dueAmount" to (document.getDouble("dueAmount") ?: 0.0),
                    "dueDate" to document.getString("dueDate").orEmpty(),
                    "remindersEnabled" to (document.getBoolean("remindersEnabled") == true),
                    "reminderEmail" to document.getString("reminderEmail").orEmpty(),
                    "reminderWhatsApp" to document.getString("reminderWhatsApp").orEmpty()
                )
            )
        }
        val transactions = transactionsCollection().get(Source.CACHE).await().documents.map { document ->
            BackupRecord(
                id = document.id,
                fields = mapOf(
                    "customerId" to document.getString("customerId").orEmpty(),
                    "transactionName" to (
                        document.getString("transactionName")
                            ?: document.getString("name")
                            ?: ""
                        ),
                    "accountId" to document.getString("accountId").orEmpty(),
                    "accountName" to document.getString("accountName").orEmpty(),
                    "accountType" to document.getString("accountType").orEmpty(),
                    "customerName" to document.getString("customerName").orEmpty(),
                    "amount" to (document.getDouble("amount") ?: 0.0),
                    "transactionDate" to (
                        document.getString("transactionDate")
                            ?: document.getString("givenDate")
                            ?: ""
                        ),
                    "givenDate" to (
                        document.getString("givenDate")
                            ?: document.getString("transactionDate")
                            ?: ""
                        ),
                    "isSettled" to (document.getBoolean("isSettled") == true),
                    "settledDate" to document.getString("settledDate").orEmpty(),
                    "dueDate" to document.getString("dueDate").orEmpty(),
                    "partialPaidAmount" to (document.getDouble("partialPaidAmount") ?: 0.0),
                    "emiGroupId" to document.getString("emiGroupId").orEmpty(),
                    "emiIndex" to (document.getLong("emiIndex") ?: 0L),
                    "emiTotal" to (document.getLong("emiTotal") ?: 0L),
                    "personName" to document.getString("personName").orEmpty(),
                    "splitGroupId" to document.getString("splitGroupId").orEmpty()
                )
            )
        }
        val payments = paymentsCollection().get(Source.CACHE).await().documents.map { document ->
            BackupRecord(
                id = document.id,
                fields = mapOf(
                    "accountId" to document.getString("accountId").orEmpty(),
                    "accountName" to document.getString("accountName").orEmpty(),
                    "accountType" to document.getString("accountType").orEmpty(),
                    "amount" to (document.getDouble("amount") ?: 0.0),
                    "date" to document.getString("date").orEmpty()
                )
            )
        }

        val savings = savingsCollection().get(Source.CACHE).await().documents.map { document ->
            BackupRecord(
                id = document.id,
                fields = mapOf(
                    "customerId"   to document.getString("customerId").orEmpty(),
                    "customerName" to document.getString("customerName").orEmpty(),
                    "amount"       to (document.getDouble("amount") ?: 0.0),
                    "type"         to document.getString("type").orEmpty(),
                    "note"         to document.getString("note").orEmpty(),
                    "date"         to document.getString("date").orEmpty()
                )
            )
        }

        return FirestoreBackupPayload(
            exportedAt = Instant.now().toString(),
            profile = profile,
            settings = settings,
            customers = customers,
            accounts = accounts,
            transactions = transactions,
            payments = payments,
            savings = savings
        )
    }

    private suspend fun snapshotExistingData(): FirestoreBackupPayload {
        val toRecords: (QuerySnapshot) -> List<BackupRecord> = { snap ->
            snap.documents.map { doc ->
                BackupRecord(id = doc.id, fields = doc.data ?: emptyMap())
            }
        }
        return FirestoreBackupPayload(
            exportedAt = "",
            profile = emptyMap(),
            settings = emptyMap(),
            customers = toRecords(customersCollection().get().await()),
            accounts = toRecords(accountsCollection().get().await()),
            transactions = toRecords(transactionsCollection().get().await()),
            payments = toRecords(paymentsCollection().get().await()),
            savings = toRecords(savingsCollection().get().await())
        )
    }

    suspend fun restoreBackup(payload: FirestoreBackupPayload) {
        val saved = snapshotExistingData()
        try {
            restoreBackupAsync(payload).forEach { task -> task.await() }
        } catch (e: Exception) {
            android.util.Log.w("FirebaseRepository", "restoreBackup failed, rolling back", e)
            restoreBackupAsync(saved).forEach { task -> task.await() }
            throw e
        }
    }

    fun restoreBackupAsync(payload: FirestoreBackupPayload): List<Task<Void>> {
        return buildPendingWrites(payload).chunked(MAX_BATCH_WRITE_COUNT).map { chunk ->
            val batch = db.batch()
            chunk.forEach { pendingWrite ->
                batch.set(
                    pendingWrite.reference,
                    pendingWrite.fields,
                    SetOptions.merge()
                )
            }
            batch.commit()
        }
    }

    private data class PendingWrite(
        val reference: DocumentReference,
        val fields: Map<String, Any?>
    )

    private fun buildPendingWrites(payload: FirestoreBackupPayload): List<PendingWrite> {
        return buildList {
            payload.customers.forEach { record ->
                add(
                    PendingWrite(
                        reference = customersCollection().document(record.id),
                        fields = record.fields.filterValues { it != null }
                    )
                )
            }

            payload.accounts.forEach { record ->
                add(
                    PendingWrite(
                        reference = accountsCollection().document(record.id),
                        fields = record.fields.filterValues { it != null }
                    )
                )
            }

            payload.transactions.forEach { record ->
                add(
                    PendingWrite(
                        reference = transactionsCollection().document(record.id),
                        fields = record.fields.filterValues { it != null }
                    )
                )
            }

            payload.payments.forEach { record ->
                add(
                    PendingWrite(
                        reference = paymentsCollection().document(record.id),
                        fields = record.fields.filterValues { it != null }
                    )
                )
            }

            payload.savings.forEach { record ->
                add(
                    PendingWrite(
                        reference = savingsCollection().document(record.id),
                        fields = record.fields.filterValues { it != null }
                    )
                )
            }
        }
    }

    private companion object {
        const val MAX_BATCH_WRITE_COUNT = 400
    }

    private fun buildAppData(
        customers: List<DocumentSnapshot>,
        accounts: List<DocumentSnapshot>,
        transactions: List<DocumentSnapshot>,
        payments: List<DocumentSnapshot>,
        savings: List<DocumentSnapshot>,
        snapshotVersion: Long = 0L
    ): AppData {
        val accountTotals = linkedMapOf<String, RunningAccountTotal>()

        IndianAccountCatalog.all.forEach { option ->
            accountTotals[option.id] = RunningAccountTotal(
                id = option.id,
                name = option.name,
                accountKind = option.accountKind
            )
        }

        accounts.forEach { account ->
            val name = account.getString("name").orEmpty()
            if (name.isBlank()) return@forEach

            val accountKind = AccountKind.fromStorage(
                account.getString("accountType") ?: account.getString("type")
            )
            val accountTotal = accountTotals.getOrPut(account.id) {
                RunningAccountTotal(
                    id = account.id,
                    name = name,
                    accountKind = accountKind
                )
            }
            // Note: totalUsed is accumulated from transactions, not stored on the account doc
            accountTotal.dueAmount = account.getDouble("dueAmount") ?: 0.0
            accountTotal.dueDate = account.getString("dueDate").orEmpty()
            accountTotal.remindersEnabled = account.getBoolean("remindersEnabled") == true
            accountTotal.reminderEmail = account.getString("reminderEmail").orEmpty()
            accountTotal.reminderWhatsApp = account.getString("reminderWhatsApp").orEmpty()
        }

        val customerTotals = linkedMapOf<String, RunningCustomerTotal>()
        val deletedCustomerTotals = linkedMapOf<String, RunningCustomerTotal>()
        val customerIdsByName = mutableMapOf<String, String>()
        val deletedCustomerIds = mutableSetOf<String>()

        customers.forEach { customer ->
            val name = customer.getString("name").orEmpty()
            if (name.isBlank()) return@forEach

            val isDeleted = customer.getBoolean("isDeleted") == true
            val targetMap = if (isDeleted) deletedCustomerTotals else customerTotals

            targetMap[customer.id] = RunningCustomerTotal(
                id = customer.id,
                name = name,
                manualPaidAmount = customer.getDouble("creditDueAmount")
                    ?: customer.getDouble("dueAmount")
                    ?: 0.0,
                isDeleted = isDeleted
            )

            if (isDeleted) {
                deletedCustomerIds.add(customer.id)
            } else {
                customerIdsByName[name.trim().lowercase()] = customer.id
            }
        }

        transactions.forEach { transaction ->
            val accountId = transaction.getString("accountId").orEmpty()
            val amount = transaction.getDouble("amount") ?: 0.0
            val splitGroupId = transaction.getString("splitGroupId").orEmpty()
            // Allow split parts with blank accountId to still be attached to the customer
            if (amount <= 0.0) return@forEach
            if (accountId.isBlank() && splitGroupId.isBlank()) return@forEach

            val fallbackOption = IndianAccountCatalog.optionById(accountId)
            val accountKind = AccountKind.fromStorage(
                transaction.getString("accountType") ?: fallbackOption?.accountKind?.storageValue
            )
            val accountName = transaction.getString("accountName")
                ?: fallbackOption?.name
                ?: accountId

            val customerName = transaction.getString("customerName").orEmpty()
            if (customerName.isNotBlank()) {
                val storedCustomerId = transaction.getString("customerId").orEmpty()
                val customerId = storedCustomerId.ifBlank {
                    customerIdsByName[customerName.trim().lowercase()]
                        ?: legacyCustomerId(customerName)
                }

                val isDeleted = customerId in deletedCustomerIds
                val emiGroupId = transaction.getString("emiGroupId").orEmpty()
                val transactionDateStr = transaction.getString("transactionDate")
                    ?: transaction.getString("givenDate") ?: ""
                val isFutureEmi = isFutureScheduledEmi(
                    transactionDate = transactionDateStr,
                    emiGroupId = emiGroupId,
                    dueDate = transaction.getString("dueDate").orEmpty()
                )

                if (!isDeleted && !isFutureEmi) {
                    // Only bank/credit card transactions affect account totals
                    if (accountKind != AccountKind.PERSON) {
                        val accountTotal = accountTotals.getOrPut(accountId) {
                            RunningAccountTotal(
                                id = accountId,
                                name = accountName,
                                accountKind = accountKind
                            )
                        }
                        accountTotal.totalUsed += amount

                        // Customer partial payments and settlements reduce the outstanding balance
                        val partialPaid = transaction.getDouble("partialPaidAmount") ?: 0.0
                        val isSettledTx = transaction.getBoolean("isSettled") == true
                        when {
                            isSettledTx -> accountTotal.customerPaid += amount
                            partialPaid > 0.0 -> accountTotal.customerPaid += partialPaid
                        }
                    }
                }

                val targetCustomers = if (isDeleted) deletedCustomerTotals else customerTotals
                // Only attach to customers that actually exist — prevents phantom/duplicate entries
                val customerTotal = targetCustomers[customerId] ?: return@forEach
                if (!isFutureEmi) {
                    customerTotal.totalAmount += amount
                }
                val isSettled = transaction.getBoolean("isSettled") == true
                if (isSettled && !isFutureEmi) {
                    customerTotal.settledTransactionAmount += amount
                }

                customerTotal.transactions.add(
                    CustomerTransaction(
                        id = transaction.id,
                        customerId = customerId,
                        name = transaction.getString("transactionName")
                            ?: transaction.getString("name")
                            ?: accountName,
                        accountId = accountId,
                        accountName = accountName,
                        accountKind = accountKind,
                        amount = amount,
                        _transactionDate = transaction.getString("transactionDate")
                            ?: transaction.getString("givenDate")
                            ?: "",
                        isSettled = isSettled,
                        settledDate = transaction.getString("settledDate").orEmpty(),
                        partialPaidAmount = transaction.getDouble("partialPaidAmount") ?: 0.0,
                        dueDate = transaction.getString("dueDate").orEmpty(),
                        personName = transaction.getString("personName").orEmpty(),
                        splitGroupId = transaction.getString("splitGroupId").orEmpty(),
                        emiGroupId = transaction.getString("emiGroupId").orEmpty(),
                        emiIndex = (transaction.getLong("emiIndex") ?: 0L).toInt(),
                        emiTotal = (transaction.getLong("emiTotal") ?: 0L).toInt()
                    )
                )
            }
        }

        payments.forEach { payment ->
            val accountId = payment.getString("accountId").orEmpty()
            val amount = payment.getDouble("amount") ?: 0.0
            if (accountId.isBlank() || amount <= 0.0) return@forEach

            val fallbackOption = IndianAccountCatalog.optionById(accountId)
            val accountKind = AccountKind.fromStorage(
                payment.getString("accountType") ?: fallbackOption?.accountKind?.storageValue
            )
            val accountName = payment.getString("accountName")
                ?: fallbackOption?.name
                ?: accountId

            val accountTotal = accountTotals.getOrPut(accountId) {
                RunningAccountTotal(
                    id = accountId,
                    name = accountName,
                    accountKind = accountKind
                )
            }
            accountTotal.totalPaid += amount
        }

        val accountSummaries = accountTotals.values.map { total ->
            // payable = what customers still owe (used - customer paid)
            // pending = owner's personal payments toward the account bill
            val customerOutstanding = (total.totalUsed - total.customerPaid).coerceAtLeast(0.0)
            CardSummary(
                id = total.id,
                name = total.name,
                accountKind = total.accountKind,
                bill = total.totalUsed,
                pending = total.totalPaid,
                payable = customerOutstanding,
                dueAmount = total.dueAmount,
                dueDate = total.dueDate,
                remindersEnabled = total.remindersEnabled,
                reminderEmail = total.reminderEmail,
                reminderWhatsApp = total.reminderWhatsApp
            )
        }

        // Attach savings entries to customers
        savings.forEach { doc ->
            val customerId = doc.getString("customerId").orEmpty()
            val amount = doc.getDouble("amount") ?: 0.0
            if (customerId.isBlank() || amount <= 0.0) return@forEach
            val entry = SavingsEntry(
                id = doc.id,
                customerId = customerId,
                customerName = doc.getString("customerName").orEmpty(),
                amount = amount,
                type = SavingsType.fromStorage(doc.getString("type")),
                note = doc.getString("note").orEmpty(),
                date = doc.getString("date").orEmpty(),
                bankAccountId = doc.getString("bankAccountId").orEmpty(),
                bankAccountName = doc.getString("bankAccountName").orEmpty()
            )
            customerTotals[customerId]?.savingsEntries?.add(entry)
            deletedCustomerTotals[customerId]?.savingsEntries?.add(entry)
        }

        val customerSummaries = customerTotals.values.map { total -> total.toSummary(snapshotVersion) }
        val deletedCustomerSummaries = deletedCustomerTotals.values.map { total -> total.toSummary(snapshotVersion) }

        return AppData(
            accounts = accountSummaries,
            customers = customerSummaries,
            deletedCustomers = deletedCustomerSummaries
        )
    }

    private fun RunningCustomerTotal.toSummary(snapshotVersion: Long = 0L): CustomerSummary {
        // totalAmount already excludes future EMIs (set in buildAppData)
        // Use isVisibleInTransactions() which includes the 20-day early-show rule
        val visibleTxns = transactions.filter { t -> t.isVisibleInTransactions() }
        // For settled transactions: count the full amount (partial is subsumed by settlement)
        // For unsettled transactions: count only partial paid amount
        val totalPartialPaid = visibleTxns
            .filter { !it.isSettled }
            .sumOf { it.partialPaidAmount }
        val customerPaidAmount = manualPaidAmount + settledTransactionAmount + totalPartialPaid
        val sortedSavings = savingsEntries.sortedByDescending { it.date }
        val savingsBalance = sortedSavings.sumOf {
            if (it.type == SavingsType.DEPOSIT) it.amount else -it.amount
        }
        return CustomerSummary(
            id = id,
            name = name,
            totalAmount = totalAmount,
            creditDueAmount = customerPaidAmount,
            manualPaidAmount = manualPaidAmount,
            settledTransactionAmount = settledTransactionAmount,
            partialPaidAmount = totalPartialPaid,
            balance = totalAmount - customerPaidAmount,
            transactions = transactions.sortedWith(
                compareByDescending<CustomerTransaction> { it.transactionDate }
                    .thenByDescending { it.id }
            ),
            isDeleted = isDeleted,
            savingsBalance = savingsBalance,
            savingsEntries = sortedSavings,
            snapshotVersion = snapshotVersion
        )
    }

    private fun userRoot() = db.collection("users")
        .document(LocalIdentityRepository.userId())

    private fun customersCollection() = userRoot()
        .collection("customers")

    private fun accountsCollection() = userRoot()
        .collection("accounts")

    private fun transactionsCollection() = userRoot()
        .collection("transactions")

    private fun paymentsCollection() = userRoot()
        .collection("payments")

    private fun savingsCollection() = userRoot()
        .collection("savings")

    private fun settlementHistoryCollection(transactionId: String) =
        transactionsCollection().document(transactionId).collection("settlementHistory")

    private fun transferHistoryCollection(transactionId: String) =
        transactionsCollection().document(transactionId).collection("transferHistory")

    suspend fun getTransferHistory(transactionId: String): List<TransferHistoryEntry> {
        val snap = transferHistoryCollection(transactionId)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .get()
            .await()
        return snap.documents.map { doc ->
            val ts = doc.getTimestamp("timestamp")
            val millis = ts?.toDate()?.time ?: 0L
            TransferHistoryEntry(
                id = doc.id,
                fromCustomerId = doc.getString("fromCustomerId") ?: "",
                fromCustomerName = doc.getString("fromCustomerName") ?: "",
                toCustomerId = doc.getString("toCustomerId") ?: "",
                toCustomerName = doc.getString("toCustomerName") ?: "",
                transactionIds = (doc.get("transactionIds") as? List<String>) ?: emptyList(),
                timestamp = millis
            )
        }
    }

    suspend fun recordSettlementHistory(
        transactionId: String,
        customerId: String,
        type: String,
        amount: Double,
        previousPartialPaid: Double,
        newPartialPaid: Double,
        previousIsSettled: Boolean,
        newIsSettled: Boolean,
        date: String
    ) {
        val data = hashMapOf(
            "transactionId" to transactionId,
            "customerId" to customerId,
            "type" to type,
            "amount" to amount,
            "previousPartialPaid" to previousPartialPaid,
            "newPartialPaid" to newPartialPaid,
            "previousIsSettled" to previousIsSettled,
            "newIsSettled" to newIsSettled,
            "date" to date,
            "timestamp" to FieldValue.serverTimestamp()
        )
        settlementHistoryCollection(transactionId).add(data).await()
    }

    suspend fun getSettlementHistory(transactionId: String): List<SettlementHistoryEntry> {
        val snap = settlementHistoryCollection(transactionId)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .get()
            .await()
        return snap.documents.map { doc ->
            SettlementHistoryEntry(
                id = doc.id,
                transactionId = doc.getString("transactionId") ?: "",
                customerId = doc.getString("customerId") ?: "",
                type = doc.getString("type") ?: "",
                amount = doc.getDouble("amount") ?: 0.0,
                previousPartialPaid = doc.getDouble("previousPartialPaid") ?: 0.0,
                newPartialPaid = doc.getDouble("newPartialPaid") ?: 0.0,
                previousIsSettled = doc.getBoolean("previousIsSettled") == true,
                newIsSettled = doc.getBoolean("newIsSettled") == true,
                date = doc.getString("date") ?: "",
                timestamp = (doc.getTimestamp("timestamp")?.toDate()?.time ?: 0L)
            )
        }
    }

    /**
     * Loads settlement history for every transaction in [transactions] in parallel,
     * attaches the transaction name to each entry, and returns the combined list
     * sorted newest-first (by timestamp).  Mirrors the web app's getAllSettlementHistory.
     */
    suspend fun getAllSettlementHistory(
        transactions: List<Pair<String, String>>   // id to name
    ): List<CustomerSettlementEntry> = coroutineScope {
        val deferred = transactions.map { (id, name) ->
            async {
                try {
                    getSettlementHistory(id).map { entry ->
                        CustomerSettlementEntry(base = entry, transactionName = name)
                    }
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
        deferred
            .flatMap { it.await() }
            .sortedByDescending { it.timestamp }
    }

    private data class RunningAccountTotal(
        val id: String,
        val name: String,
        val accountKind: AccountKind,
        var totalUsed: Double = 0.0,
        var totalPaid: Double = 0.0,       // owner's personal payments (payments collection)
        var customerPaid: Double = 0.0,    // customer partial + settled payments
        var dueAmount: Double = 0.0,
        var dueDate: String = "",
        var remindersEnabled: Boolean = false,
        var reminderEmail: String = "",
        var reminderWhatsApp: String = ""
    )

    private data class RunningCustomerTotal(
        val id: String,
        val name: String,
        var manualPaidAmount: Double = 0.0,
        var settledTransactionAmount: Double = 0.0,
        var totalAmount: Double = 0.0,
        val transactions: MutableList<CustomerTransaction> = mutableListOf(),
        val savingsEntries: MutableList<SavingsEntry> = mutableListOf(),
        val isDeleted: Boolean = false
    )

    private fun legacyCustomerId(customerName: String): String {
        return "legacy_${customerName.trim().lowercase().hashCode()}"
    }

    private fun isFutureScheduledEmi(
        transactionDate: String,
        emiGroupId: String,
        dueDate: String = "",
        referenceDate: LocalDate = LocalDate.now()
    ): Boolean {
        if (emiGroupId.isBlank()) return false
        // Compute the effective transaction date the same way CustomerTransaction does:
        // for EMIs with a dueDate, transactionDate = dueDate - 20 days.
        val effectiveDate = if (dueDate.isNotBlank()) {
            val due = runCatching { LocalDate.parse(dueDate) }.getOrNull()
            due?.minusDays(20)?.toString() ?: transactionDate
        } else transactionDate
        // It's a future EMI (hide it) only if its transaction date is strictly after today.
        val installmentDate = runCatching { LocalDate.parse(effectiveDate) }.getOrNull()
            ?: return false
        return installmentDate.isAfter(referenceDate)
    }
}
