package com.radafiq.data.models

import java.time.LocalDate
import java.time.YearMonth

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
    val savingsEntries: List<SavingsEntry> = emptyList()
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
    val transactionDate: String,
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

    private fun installmentDate(): LocalDate? {
        return runCatching { LocalDate.parse(transactionDate) }.getOrNull()
    }

    fun isScheduledForFutureMonth(referenceDate: LocalDate = LocalDate.now()): Boolean {
        if (!isEmi) return false
        val installmentDate = installmentDate() ?: return false
        return YearMonth.from(installmentDate).isAfter(YearMonth.from(referenceDate))
    }

    fun isVisibleInTransactions(referenceDate: LocalDate = LocalDate.now()): Boolean {
        if (!isEmi) return true
        // Rule 1: transaction date is current month or past
        if (!isScheduledForFutureMonth(referenceDate)) return true
        // Rule 2: due date is within 20 days — show early so user can prepare
        if (dueDate.isNotBlank()) {
            val due = runCatching { LocalDate.parse(dueDate) }.getOrNull()
            if (due != null && !due.isAfter(referenceDate.plusDays(20))) return true
        }
        return false
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
