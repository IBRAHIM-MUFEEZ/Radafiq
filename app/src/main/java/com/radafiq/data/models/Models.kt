package com.radafiq.data.models

import java.time.LocalDate

enum class AccountKind(
    val storageValue: String,
    val label: String
) {
    BANK_ACCOUNT("bank_account", "Bank Account"),
    CREDIT_CARD("credit_card", "Credit Card"),
    PERSON("person", "Person");

    companion object {
        fun fromStorage(value: String?): AccountKind {
            return when (value?.lowercase()) {
                "bank_account", "bank", "account" -> BANK_ACCOUNT
                "credit_card", "credit", "card" -> CREDIT_CARD
                "person" -> PERSON
                else -> CREDIT_CARD
            }
        }
    }
}

data class AccountOption(
    val id: String,
    val name: String,
    val accountKind: AccountKind
)

object IndianAccountCatalog {
    val bankAccounts = listOf(
        AccountOption("bank_sbi", "State Bank of India (SBI)", AccountKind.BANK_ACCOUNT),
        AccountOption("bank_hdfc", "HDFC Bank", AccountKind.BANK_ACCOUNT),
        AccountOption("bank_icici", "ICICI Bank", AccountKind.BANK_ACCOUNT),
        AccountOption("bank_axis", "Axis Bank", AccountKind.BANK_ACCOUNT),
        AccountOption("bank_kotak", "Kotak Mahindra Bank", AccountKind.BANK_ACCOUNT),
        AccountOption("bank_pnb", "Punjab National Bank", AccountKind.BANK_ACCOUNT),
        AccountOption("bank_bob", "Bank of Baroda", AccountKind.BANK_ACCOUNT),
        AccountOption("bank_union", "Union Bank of India", AccountKind.BANK_ACCOUNT),
        AccountOption("bank_canara", "Canara Bank", AccountKind.BANK_ACCOUNT),
        AccountOption("bank_idfc_first", "IDFC FIRST Bank", AccountKind.BANK_ACCOUNT),
        AccountOption("bank_indusind", "IndusInd Bank", AccountKind.BANK_ACCOUNT),
        AccountOption("bank_au", "AU Small Finance Bank", AccountKind.BANK_ACCOUNT),
        AccountOption("bank_yes", "YES BANK", AccountKind.BANK_ACCOUNT),
        AccountOption("bank_idbi", "IDBI Bank", AccountKind.BANK_ACCOUNT),
        AccountOption("bank_federal", "Federal Bank", AccountKind.BANK_ACCOUNT),
        AccountOption("bank_rbl", "RBL Bank", AccountKind.BANK_ACCOUNT)
    )

    val creditCards = listOf(
        AccountOption("card_sbi", "SBI Card", AccountKind.CREDIT_CARD),
        AccountOption("card_hdfc", "HDFC Bank Credit Card", AccountKind.CREDIT_CARD),
        AccountOption("card_icici", "ICICI Bank Credit Card", AccountKind.CREDIT_CARD),
        AccountOption("card_axis", "Axis Bank Credit Card", AccountKind.CREDIT_CARD),
        AccountOption("card_kotak", "Kotak Mahindra Credit Card", AccountKind.CREDIT_CARD),
        AccountOption("card_indusind", "IndusInd Bank Credit Card", AccountKind.CREDIT_CARD),
        AccountOption("card_idfc_first", "IDFC FIRST Bank Credit Card", AccountKind.CREDIT_CARD),
        AccountOption("card_amex", "American Express India", AccountKind.CREDIT_CARD),
        AccountOption("card_standard_chartered", "Standard Chartered Credit Card", AccountKind.CREDIT_CARD),
        AccountOption("card_hsbc", "HSBC Credit Card", AccountKind.CREDIT_CARD),
        AccountOption("card_au", "AU Bank Credit Card", AccountKind.CREDIT_CARD),
        AccountOption("card_yes", "YES BANK Credit Card", AccountKind.CREDIT_CARD),
        AccountOption("card_rbl", "RBL Bank Credit Card", AccountKind.CREDIT_CARD),
        AccountOption("card_onecard", "OneCard", AccountKind.CREDIT_CARD),
        AccountOption("card_amazon_icici", "Amazon Pay ICICI Card", AccountKind.CREDIT_CARD),
        AccountOption("card_flipkart_axis", "Flipkart Axis Bank Credit Card", AccountKind.CREDIT_CARD),
        AccountOption("card_jupiter", "Jupiter Credit Card", AccountKind.CREDIT_CARD),
        AccountOption("card_irctc_hdfc", "IRCTC HDFC Credit Card", AccountKind.CREDIT_CARD)
    )

    val all = bankAccounts + creditCards

    fun optionsFor(
        accountKind: AccountKind,
        selectedAccountIds: Set<String>? = null
    ): List<AccountOption> {
        val baseOptions = when (accountKind) {
            AccountKind.BANK_ACCOUNT -> bankAccounts
            AccountKind.CREDIT_CARD  -> creditCards
            AccountKind.PERSON       -> emptyList()
        }
        return if (selectedAccountIds == null) {
            baseOptions
        } else {
            baseOptions.filter { it.id in selectedAccountIds }
        }
    }

    fun optionById(id: String): AccountOption? {
        return all.firstOrNull { it.id == id }
    }

    fun availableKinds(selectedAccountIds: Set<String>): List<AccountKind> {
        return AccountKind.values().filter { optionsFor(it, selectedAccountIds).isNotEmpty() }
    }

    fun defaultSelectedAccountIds(): Set<String> {
        return all.map { it.id }.toSet()
    }

    fun sanitizeSelectedAccountIds(selectedAccountIds: Set<String>): Set<String> {
        val knownIds = all.map { it.id }.toSet()
        val filteredIds = selectedAccountIds.filterTo(mutableSetOf()) { it in knownIds }
        return if (filteredIds.isEmpty()) {
            defaultSelectedAccountIds()
        } else {
            filteredIds
        }
    }

    /** Like [sanitizeSelectedAccountIds] but also merges in any newly added account IDs
     *  (e.g. new credit cards) that weren't in the saved set. Call this when loading
     *  persisted settings so new catalog entries appear automatically.
     *  @param savedKnownIds the set of account IDs that existed at the time of the
     *         last save. Only accounts NOT in this set are auto-merged.
     *         Empty for backward compat — fallback uses [selectedAccountIds]. */
    fun mergeNewAccountIds(
        selectedAccountIds: Set<String>,
        savedKnownIds: Set<String> = emptySet()
    ): MergedResult {
        val knownIds = all.map { it.id }.toSet()
        val filtered = selectedAccountIds.filterTo(mutableSetOf()) { it in knownIds }
        if (filtered.isEmpty()) return MergedResult(defaultSelectedAccountIds(), knownIds)
        // Backward compat: if no savedKnownIds, use filtered (saved selections) as baseline
        val baseline = if (savedKnownIds.isEmpty()) filtered.toSet() else savedKnownIds
        val genuinelyNew = knownIds - baseline
        filtered.addAll(genuinelyNew)
        return MergedResult(filtered, knownIds)
    }
}

data class MergedResult(
    val selectedAccountIds: Set<String>,
    val knownAccountIds: Set<String>
)

data class CardSummary(
    val id: String,
    val name: String,
    val accountKind: AccountKind,
    val bill: Double,
    val pending: Double,
    val payable: Double,
    val dueAmount: Double = 0.0,
    val dueDate: String = "",
    val remindersEnabled: Boolean = false,
    val reminderEmail: String = "",
    val reminderWhatsApp: String = ""
)

fun CardSummary.hasLedgerActivity(): Boolean {
    return bill > 0.0 ||
        pending > 0.0 ||
        dueAmount > 0.0 ||
        dueDate.isNotBlank() ||
        remindersEnabled ||
        reminderEmail.isNotBlank() ||
        reminderWhatsApp.isNotBlank()
}

data class CustomerSummary(
    val id: String,
    val name: String,
    val totalAmount: Double,
    val creditDueAmount: Double,
    val manualPaidAmount: Double,
    val settledTransactionAmount: Double,
    val partialPaidAmount: Double,
    val balance: Double,
    val transactions: List<CustomerTransaction>,
    val isDeleted: Boolean = false,
    val savingsBalance: Double = 0.0,
    val savingsEntries: List<SavingsEntry> = emptyList(),
    /** Monotonically increments on every Firestore snapshot so remember(customer)
     *  always invalidates even when transactions list is structurally equal. */
    val snapshotVersion: Long = 0L
)

data class SavingsEntry(
    val id: String,
    val customerId: String,
    val customerName: String,
    val amount: Double,
    val type: SavingsType,
    val note: String = "",
    val date: String,
    val bankAccountId: String = "",
    val bankAccountName: String = ""
)

enum class SavingsType(val label: String, val storageValue: String) {
    DEPOSIT("Deposit", "deposit"),
    WITHDRAWAL("Withdrawal", "withdrawal");

    companion object {
        fun fromStorage(value: String?): SavingsType =
            if (value == "withdrawal") WITHDRAWAL else DEPOSIT
    }
}

data class CustomerTransaction(
    val id: String,
    val customerId: String,
    val name: String,
    val accountId: String,
    val accountName: String,
    val accountKind: AccountKind,
    val amount: Double,
    private val _transactionDate: String,
    val isSettled: Boolean = false,
    val settledDate: String = "",
    val partialPaidAmount: Double = 0.0,
    val dueDate: String = "",
    val personName: String = "",     // filled when accountKind == PERSON
    val splitGroupId: String = "",   // links split transactions together
    // EMI fields
    val emiGroupId: String = "",
    val emiIndex: Int = 0,
    val emiTotal: Int = 0
) {
    val isEmi: Boolean get() = emiGroupId.isNotBlank()
    val isSplit: Boolean get() = splitGroupId.isNotBlank()

    /** Creates a copy with a new stored transaction date (used for optimistic updates). */
    fun withStoredDate(date: String): CustomerTransaction =
        copy(_transactionDate = date)

    companion object {
        /** Public factory used for optimistic local creation outside this class,
         *  since _transactionDate is a private constructor parameter. */
        fun create(
            id: String,
            customerId: String,
            name: String,
            accountId: String,
            accountName: String,
            accountKind: AccountKind,
            amount: Double,
            transactionDate: String,
            isSettled: Boolean = false,
            settledDate: String = "",
            partialPaidAmount: Double = 0.0,
            dueDate: String = "",
            personName: String = "",
            splitGroupId: String = "",
            emiGroupId: String = "",
            emiIndex: Int = 0,
            emiTotal: Int = 0
        ) = CustomerTransaction(
            id = id,
            customerId = customerId,
            name = name,
            accountId = accountId,
            accountName = accountName,
            accountKind = accountKind,
            amount = amount,
            _transactionDate = transactionDate,
            isSettled = isSettled,
            settledDate = settledDate,
            partialPaidAmount = partialPaidAmount,
            dueDate = dueDate,
            personName = personName,
            splitGroupId = splitGroupId,
            emiGroupId = emiGroupId,
            emiIndex = emiIndex,
            emiTotal = emiTotal
        )
    }

    /**
     * For EMI transactions: always computed as dueDate - 20 days so the installment
     * appears in the transaction tab 20 days before payment is due.
     * For all other transactions: the stored date as-is.
     */
    val transactionDate: String get() {
        if (isEmi && dueDate.isNotBlank()) {
            val due = runCatching { LocalDate.parse(dueDate) }.getOrNull()
            if (due != null) return due.minusDays(20).toString()
        }
        return _transactionDate
    }

    fun isVisibleInTransactions(referenceDate: LocalDate = LocalDate.now()): Boolean {
        if (!isEmi) return true
        // An EMI installment is visible only when its transaction date (dueDate - 20 days)
        // has reached or passed today. No early-show logic.
        val txnDate = runCatching { LocalDate.parse(transactionDate) }.getOrNull()
            ?: return true  // if date can't be parsed, show it rather than hide it
        return !txnDate.isAfter(referenceDate)
    }

    /** True if this transaction's due date is today or in the past and it is not settled. */
    val isDueToday: Boolean get() {
        if (isSettled) return false
        if (dueDate.isBlank()) return false
        val due = runCatching { LocalDate.parse(dueDate) }.getOrNull() ?: return false
        return !due.isAfter(LocalDate.now())
    }
}

data class AppData(
    val accounts: List<CardSummary>,
    val customers: List<CustomerSummary>,
    val deletedCustomers: List<CustomerSummary>
)

// Represents one row in a split transaction entry dialog
data class SplitEntry(
    val accountKind: AccountKind = AccountKind.CREDIT_CARD,
    val accountId: String = "",
    val accountName: String = "",
    val personName: String = "",
    val amount: String = ""
)

data class Transaction(
    val customerId: String = "",
    val transactionName: String = "",
    val accountId: String = "",
    val accountName: String = "",
    val accountType: String = "",
    val customerName: String = "",
    val amount: Double = 0.0,
    val transactionDate: String = "",
    val givenDate: String = ""
)

data class Payment(
    val accountId: String = "",
    val accountName: String = "",
    val accountType: String = "",
    val amount: Double = 0.0,
    val date: String = ""
)

data class SettlementHistoryEntry(
    val id: String,
    val transactionId: String,
    val customerId: String,
    val type: String,       // "settled", "partial", "unsettled"
    val amount: Double,
    val previousPartialPaid: Double,
    val newPartialPaid: Double,
    val previousIsSettled: Boolean,
    val newIsSettled: Boolean,
    val date: String,
    val timestamp: Long = 0L
) {
    val label: String get() = when (type) {
        "settled" -> "Fully Settled"
        "partial" -> "Partial Payment"
        "unsettled" -> "Marked Unpaid"
        else -> type
    }
}

/**
 * Settlement history entry enriched with the transaction name — used for the
 * customer-level "Settlement History" view that shows all payment events across
 * every transaction in one consolidated list.
 */
data class CustomerSettlementEntry(
    val base: SettlementHistoryEntry,
    val transactionName: String
) {
    val id: String get() = base.id
    val type: String get() = base.type
    val amount: Double get() = base.amount
    val date: String get() = base.date
    val timestamp: Long get() = base.timestamp
    val label: String get() = base.label
}

/**
 * Record of a transaction being transferred from one customer to another.
 * Stored in users/{uid}/transactions/{transactionId}/transferHistory/.
 */
data class TransferHistoryEntry(
    val id: String,
    val fromCustomerId: String,
    val fromCustomerName: String,
    val toCustomerId: String,
    val toCustomerName: String,
    val transactionIds: List<String>,
    val timestamp: Long = 0L
)
