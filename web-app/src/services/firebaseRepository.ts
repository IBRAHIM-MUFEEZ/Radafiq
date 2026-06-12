import {
  collection,
  doc,
  addDoc,
  setDoc,
  deleteDoc,
  getDoc,
  getDocs,
  onSnapshot,
  writeBatch,
  serverTimestamp,
  increment,
  deleteField,
  query,
  where,
  orderBy,
  Unsubscribe,
  DocumentSnapshot,
  QuerySnapshot,
} from 'firebase/firestore';
import { db } from '../firebase';
import {
  AccountKind,
  CardSummary,
  CustomerSummary,
  CustomerTransaction,
  SavingsEntry,
  SettlementHistoryEntry,
  FirestoreBackupPayload,
  BackupRecord,
  accountKindFromStorage,
} from '../types/models';

type SavingsType = 'deposit' | 'withdrawal';

// ── Path helpers ──────────────────────────────────────────────────────────────

function userRoot(uid: string) {
  return doc(db, 'users', uid);
}

function customersCol(uid: string) {
  return collection(db, 'users', uid, 'customers');
}

function transactionsCol(uid: string) {
  return collection(db, 'users', uid, 'transactions');
}

function accountsCol(uid: string) {
  return collection(db, 'users', uid, 'accounts');
}

function paymentsCol(uid: string) {
  return collection(db, 'users', uid, 'payments');
}

function savingsCol(uid: string) {
  return collection(db, 'users', uid, 'savings');
}

function settlementHistoryCol(uid: string, transactionId: string) {
  return collection(db, 'users', uid, 'transactions', transactionId, 'settlementHistory');
}

function profileDoc(uid: string) {
  return doc(db, 'users', uid, 'profile', 'main');
}

function securityDoc(uid: string) {
  return doc(db, 'users', uid, 'settings', 'security');
}

// ── Security settings ─────────────────────────────────────────────────────────

export async function saveSecurityToCloud(uid: string, data: {
  passcodeHash: string;
  passcodeSalt: string;
  lockEnabled: boolean;
  recoveryQuestion: string;
  recoveryAnswerHash: string;
}): Promise<void> {
  await setDoc(securityDoc(uid), {
    ...data,
    updatedAt: serverTimestamp(),
  });
}

export async function loadSecurityFromCloud(uid: string): Promise<{
  passcodeHash: string;
  passcodeSalt: string;
  lockEnabled: boolean;
  recoveryQuestion: string;
  recoveryAnswerHash: string;
} | null> {
  const snap = await getDoc(securityDoc(uid));
  if (!snap.exists()) return null;
  const d = snap.data();
  return {
    passcodeHash: (d.passcodeHash as string) ?? '',
    passcodeSalt: (d.passcodeSalt as string) ?? '',
    lockEnabled: (d.lockEnabled as boolean) ?? false,
    recoveryQuestion: (d.recoveryQuestion as string) ?? '',
    recoveryAnswerHash: (d.recoveryAnswerHash as string) ?? '',
  };
}

export async function clearSecurityFromCloud(uid: string): Promise<void> {
  await deleteDoc(securityDoc(uid));
}

// ── Data builders ─────────────────────────────────────────────────────────────

function buildAppData(
  customerDocs: DocumentSnapshot[],
  accountDocs: DocumentSnapshot[],
  transactionDocs: DocumentSnapshot[],
  paymentDocs: DocumentSnapshot[],
  savingsDocs: DocumentSnapshot[]
): { accounts: CardSummary[]; customers: CustomerSummary[]; deletedCustomers: CustomerSummary[] } {
  const today = new Date();

  // Build transactions map
  const allTransactions: CustomerTransaction[] = transactionDocs.map(d => {
    const data = d.data() ?? {};
    return {
      id: d.id,
      customerId: (data.customerId as string) ?? '',
      name: (data.transactionName as string) ?? '',
      accountId: (data.accountId as string) ?? '',
      accountName: (data.accountName as string) ?? '',
      accountKind: accountKindFromStorage(data.accountType as string),
      amount: (data.amount as number) ?? 0,
      transactionDate: (data.transactionDate as string) ?? '',
      isSettled: (data.isSettled as boolean) ?? false,
      settledDate: (data.settledDate as string) ?? '',
      partialPaidAmount: (data.partialPaidAmount as number) ?? 0,
      dueDate: (data.dueDate as string) ?? '',
      personName: (data.personName as string) ?? '',
      splitGroupId: (data.splitGroupId as string) ?? '',
      emiGroupId: (data.emiGroupId as string) ?? '',
      emiIndex: (data.emiIndex as number) ?? 0,
      emiTotal: (data.emiTotal as number) ?? 0,
    };
  });

  // Build savings map
  const allSavings: SavingsEntry[] = savingsDocs.map(d => {
    const data = d.data() ?? {};
    return {
      id: d.id,
      customerId: (data.customerId as string) ?? '',
      customerName: (data.customerName as string) ?? '',
      amount: (data.amount as number) ?? 0,
      type: ((data.type as string) === 'withdrawal' ? 'withdrawal' : 'deposit') as SavingsType,
      note: (data.note as string) ?? '',
      date: (data.date as string) ?? '',
      bankAccountId: (data.bankAccountId as string) ?? '',
      bankAccountName: (data.bankAccountName as string) ?? '',
    };
  });

  // Build payments map
  const paymentsByAccount: Record<string, number> = {};
  paymentDocs.forEach(d => {
    const data = d.data() ?? {};
    const accountId = (data.accountId as string) ?? '';
    const amount = (data.amount as number) ?? 0;
    paymentsByAccount[accountId] = (paymentsByAccount[accountId] ?? 0) + amount;
  });

  // Build customer summaries
  const customerMap = new Map<string, CustomerSummary>();
  customerDocs.forEach(d => {
    const data = d.data() ?? {};
    const isDeleted = (data.isDeleted as boolean) ?? false;
    customerMap.set(d.id, {
      id: d.id,
      name: (data.name as string) ?? '',
      totalAmount: 0,
      creditDueAmount: (data.creditDueAmount as number) ?? 0,
      manualPaidAmount: 0,
      settledTransactionAmount: 0,
      partialPaidAmount: 0,
      balance: 0,
      transactions: [],
      isDeleted,
      savingsBalance: 0,
      savingsEntries: [],
    });
  });

  // Attach transactions to customers
  allTransactions.forEach(t => {
    const customer = customerMap.get(t.customerId);
    if (customer) {
      customer.transactions.push(t);
    }
  });

  // Attach savings to customers
  allSavings.forEach(s => {
    const customer = customerMap.get(s.customerId);
    if (customer) {
      customer.savingsEntries.push(s);
    }
  });

  // Compute customer balances
  customerMap.forEach(customer => {
    const visibleTxns = customer.transactions.filter(t => {
      if (!t.emiGroupId) return true;
      const d = new Date(t.transactionDate);
      if (isNaN(d.getTime())) return true;
      const refYear = today.getFullYear();
      const refMonth = today.getMonth();
      // Future month by transaction date
      if (d.getFullYear() > refYear || (d.getFullYear() === refYear && d.getMonth() > refMonth)) {
        // But show if due date is within 20 days
        if (t.dueDate) {
          const due = new Date(t.dueDate);
          if (!isNaN(due.getTime())) {
            const msIn20Days = 20 * 24 * 60 * 60 * 1000;
            if (due.getTime() - today.getTime() <= msIn20Days) return true;
          }
        }
        return false;
      }
      return true;
    });

    let totalAmount = 0;
    let settledAmount = 0;
    let partialAmount = 0;

    visibleTxns.forEach(t => {
      totalAmount += t.amount;
      if (t.isSettled) {
        settledAmount += t.amount;
      } else {
        partialAmount += t.partialPaidAmount;
      }
    });

    customer.totalAmount = totalAmount;
    customer.settledTransactionAmount = settledAmount;
    customer.partialPaidAmount = partialAmount;
    customer.manualPaidAmount = customer.creditDueAmount; // raw stored value
    // creditDueAmount = total customer paid = manual + settled + partial (matches Android)
    const customerPaidAmount = customer.creditDueAmount + settledAmount + partialAmount;
    customer.creditDueAmount = customerPaidAmount;
    customer.balance = Math.max(0, totalAmount - customerPaidAmount);

    // Savings balance
    const deposited = customer.savingsEntries
      .filter(s => s.type === 'deposit')
      .reduce((sum, s) => sum + s.amount, 0);
    const withdrawn = customer.savingsEntries
      .filter(s => s.type === 'withdrawal')
      .reduce((sum, s) => sum + s.amount, 0);
    customer.savingsBalance = Math.max(0, deposited - withdrawn);
  });

  // Build account summaries
  const accountSummaryMap = new Map<string, CardSummary>();

  // Initialize from accounts collection
  accountDocs.forEach(d => {
    const data = d.data() ?? {};
    const kind = accountKindFromStorage((data.accountType ?? data.type) as string);
    accountSummaryMap.set(d.id, {
      id: d.id,
      name: (data.name as string) ?? '',
      accountKind: kind,
      bill: 0,
      pending: paymentsByAccount[d.id] ?? 0,
      payable: 0,
      dueAmount: (data.dueAmount as number) ?? 0,
      dueDate: (data.dueDate as string) ?? '',
      remindersEnabled: (data.remindersEnabled as boolean) ?? false,
      reminderEmail: (data.reminderEmail as string) ?? '',
      reminderWhatsApp: (data.reminderWhatsApp as string) ?? '',
    });
  });

  // Aggregate transaction amounts per account
  allTransactions.forEach(t => {
    // Person transactions don't affect account totals — only bank/credit card do
    if (t.accountKind === 'person') return;

    if (t.emiGroupId) {
      // EMI — only count visible (current month and past, or due within 20 days)
      const d = new Date(t.transactionDate);
      if (!isNaN(d.getTime())) {
        const refYear = today.getFullYear();
        const refMonth = today.getMonth();
        if (d.getFullYear() > refYear || (d.getFullYear() === refYear && d.getMonth() > refMonth)) {
          // Future month — skip unless due within 20 days
          let showEarly = false;
          if (t.dueDate) {
            const due = new Date(t.dueDate);
            if (!isNaN(due.getTime())) {
              const msIn20Days = 20 * 24 * 60 * 60 * 1000;
              if (due.getTime() - today.getTime() <= msIn20Days) showEarly = true;
            }
          }
          if (!showEarly) return;
        }
      }
    }

    let summary = accountSummaryMap.get(t.accountId);
    if (!summary) {
      summary = {
        id: t.accountId,
        name: t.accountName,
        accountKind: t.accountKind,
        bill: 0,
        pending: paymentsByAccount[t.accountId] ?? 0,
        payable: 0,
        dueAmount: 0,
        dueDate: '',
        remindersEnabled: false,
        reminderEmail: '',
        reminderWhatsApp: '',
      };
      accountSummaryMap.set(t.accountId, summary);
    }
    summary.bill += t.amount;

    // Track customer payments (settled + partial) to compute payable correctly
    // matching Android's logic: payable = totalUsed - customerPaid
    if (t.isSettled) {
      summary.payable -= t.amount; // will be corrected after loop
    } else if (t.partialPaidAmount > 0) {
      summary.payable -= t.partialPaidAmount; // will be corrected after loop
    }
  });

  // Compute payable: bill - customerPaid (settled + partial), floored at 0
  // This matches Android: payable = totalUsed - customerPaid
  accountSummaryMap.forEach(summary => {
    // payable was used as a running customerPaid accumulator above (negative)
    // bill + payable(negative) = bill - customerPaid
    const customerPaid = -summary.payable;
    summary.payable = Math.max(0, summary.bill - customerPaid);
  });

  const customers = Array.from(customerMap.values()).filter(c => !c.isDeleted);
  const deletedCustomers = Array.from(customerMap.values()).filter(c => c.isDeleted);
  const accounts = Array.from(accountSummaryMap.values());

  return { accounts, customers, deletedCustomers };
}

// ── Listener ──────────────────────────────────────────────────────────────────

export function listenAllData(
  uid: string,
  onResult: (data: { accounts: CardSummary[]; customers: CustomerSummary[]; deletedCustomers: CustomerSummary[] }) => void
): Unsubscribe[] {
  let latestCustomers: DocumentSnapshot[] = [];
  let latestAccounts: DocumentSnapshot[] = [];
  let latestTransactions: DocumentSnapshot[] = [];
  let latestPayments: DocumentSnapshot[] = [];
  let latestSavings: DocumentSnapshot[] = [];

  let customersReady = false;
  let accountsReady = false;
  let transactionsReady = false;
  let paymentsReady = false;
  let savingsReady = false;

  function notifyIfReady() {
    if (customersReady && accountsReady && transactionsReady && paymentsReady && savingsReady) {
      onResult(buildAppData(latestCustomers, latestAccounts, latestTransactions, latestPayments, latestSavings));
    }
  }

  const u1 = onSnapshot(customersCol(uid), snap => {
    latestCustomers = snap.docs;
    customersReady = true;
    notifyIfReady();
  });

  const u2 = onSnapshot(accountsCol(uid), snap => {
    latestAccounts = snap.docs;
    accountsReady = true;
    notifyIfReady();
  });

  const u3 = onSnapshot(transactionsCol(uid), snap => {
    latestTransactions = snap.docs;
    transactionsReady = true;
    notifyIfReady();
  });

  const u4 = onSnapshot(paymentsCol(uid), snap => {
    latestPayments = snap.docs;
    paymentsReady = true;
    notifyIfReady();
  });

  const u5 = onSnapshot(savingsCol(uid), snap => {
    latestSavings = snap.docs;
    savingsReady = true;
    notifyIfReady();
  });

  return [u1, u2, u3, u4, u5];
}

// ── Customer CRUD ─────────────────────────────────────────────────────────────

export async function addCustomer(uid: string, name: string): Promise<string> {
  const ref = await addDoc(customersCol(uid), {
    name,
    creditDueAmount: 0,
    isDeleted: false,
  });
  return ref.id;
}

export async function deleteCustomer(uid: string, customerId: string, customerName: string): Promise<void> {
  await setDoc(
    doc(customersCol(uid), customerId),
    { name: customerName, isDeleted: true, deletedAt: serverTimestamp() },
    { merge: true }
  );
}

export async function restoreCustomer(uid: string, customerId: string): Promise<void> {
  await setDoc(
    doc(customersCol(uid), customerId),
    { isDeleted: false, deletedAt: deleteField() },
    { merge: true }
  );
}

export async function permanentlyDeleteCustomer(uid: string, customerId: string, customerName: string): Promise<void> {
  const batch = writeBatch(db);
  batch.delete(doc(customersCol(uid), customerId));

  const txnSnap = await getDocs(query(transactionsCol(uid), where('customerId', '==', customerId)));
  const legacySnap = await getDocs(query(transactionsCol(uid), where('customerName', '==', customerName)));

  const deleted = new Set<string>();
  // BUG-19 fix: Set.add() always returns the Set (truthy) — check has() before add()
  txnSnap.docs.forEach(d => { if (!deleted.has(d.id)) { deleted.add(d.id); batch.delete(d.ref); } });
  legacySnap.docs.forEach(d => { if (!deleted.has(d.id)) { deleted.add(d.id); batch.delete(d.ref); } });

  await batch.commit();
}

export async function updateCustomerDueAmount(uid: string, customerId: string, customerName: string, amount: number): Promise<void> {
  await setDoc(
    doc(customersCol(uid), customerId),
    { name: customerName, creditDueAmount: amount },
    { merge: true }
  );
}

// ── Transaction CRUD ──────────────────────────────────────────────────────────

export async function addTransaction(
  uid: string,
  params: {
    customerId: string;
    transactionName: string;
    accountId: string;
    accountName: string;
    accountKind: AccountKind;
    customerName: string;
    amount: number;
    transactionDate: string;
    personName?: string;
    splitGroupId?: string;
    dueDate?: string;
    emiGroupId?: string;
    emiIndex?: number;
    emiTotal?: number;
  }
): Promise<void> {
  const data: Record<string, unknown> = {
    customerId: params.customerId,
    transactionName: params.transactionName,
    accountId: params.accountId,
    accountName: params.accountName,
    accountType: params.accountKind,
    customerName: params.customerName,
    amount: params.amount,
    transactionDate: params.transactionDate,
    givenDate: params.transactionDate,
  };
  if (params.personName) data.personName = params.personName;
  if (params.splitGroupId) data.splitGroupId = params.splitGroupId;
  if (params.dueDate) data.dueDate = params.dueDate;
  if (params.emiGroupId) {
    data.emiGroupId = params.emiGroupId;
    data.emiIndex = params.emiIndex ?? 0;
    data.emiTotal = params.emiTotal ?? 0;
  }
  await addDoc(transactionsCol(uid), data);
}

export async function addSplitTransactionsBatch(uid: string, splits: Record<string, unknown>[]): Promise<void> {
  const batch = writeBatch(db);
  splits.forEach(data => batch.set(doc(transactionsCol(uid)), data));
  await batch.commit();
}

/**
 * Convert a single EMI installment into split entries.
 * Deletes the original transaction and creates new split docs that preserve
 * the emiGroupId/emiIndex/emiTotal so the installment still belongs to the EMI plan.
 */
export async function convertEmiInstallmentToSplit(
  uid: string,
  originalTransactionId: string,
  splits: Record<string, unknown>[]
): Promise<void> {
  const batch = writeBatch(db);
  // Delete the original single-account EMI installment
  batch.delete(doc(transactionsCol(uid), originalTransactionId));
  // Create the replacement split docs (each carries emiGroupId etc.)
  splits.forEach(data => batch.set(doc(transactionsCol(uid)), data));
  await batch.commit();
}

export async function addEmiTransactionsBatch(uid: string, instalments: Record<string, unknown>[]): Promise<void> {
  const batch = writeBatch(db);
  instalments.forEach(data => batch.set(doc(transactionsCol(uid)), data));
  await batch.commit();
}

export async function updateTransaction(
  uid: string,
  transactionId: string,
  params: {
    transactionName: string;
    accountId: string;
    accountName: string;
    accountKind: AccountKind;
    amount: number;
    transactionDate: string;
    personName?: string;
  }
): Promise<void> {
  const data: Record<string, unknown> = {
    transactionName: params.transactionName,
    accountId: params.accountId,
    accountName: params.accountName,
    accountType: params.accountKind,
    amount: params.amount,
    transactionDate: params.transactionDate,
    givenDate: params.transactionDate,
    dueDate: deleteField(),
    personName: params.personName || deleteField(),
  };
  await setDoc(doc(transactionsCol(uid), transactionId), data, { merge: true });
}

export async function deleteTransaction(uid: string, transactionId: string): Promise<void> {
  await deleteDoc(doc(transactionsCol(uid), transactionId));
}

export async function addPartialPayment(uid: string, transactionId: string, amount: number, date: string): Promise<void> {
  // Fetch current state before updating
  const currentSnap = await getDoc(doc(transactionsCol(uid), transactionId));
  const currentData = currentSnap.data() ?? {};
  const previousPartialPaid = (currentData.partialPaidAmount as number) ?? 0;

  await setDoc(
    doc(transactionsCol(uid), transactionId),
    { partialPaidAmount: increment(amount), lastPartialPaymentDate: date },
    { merge: true }
  );

  // Record partial payment history
  await recordSettlementHistory(uid, {
    transactionId,
    customerId: (currentData.customerId as string) ?? '',
    type: 'partial',
    amount,
    previousPartialPaid,
    newPartialPaid: previousPartialPaid + amount,
    previousIsSettled: (currentData.isSettled as boolean) ?? false,
    newIsSettled: false,
    date,
  });
}

export async function toggleTransactionSettled(uid: string, transactionId: string, isSettled: boolean, settledDate: string): Promise<void> {
  // Fetch current transaction state before updating
  const currentSnap = await getDoc(doc(transactionsCol(uid), transactionId));
  const currentData = currentSnap.data() ?? {};
  const previousPartialPaid = (currentData.partialPaidAmount as number) ?? 0;

  await setDoc(
    doc(transactionsCol(uid), transactionId),
    {
      isSettled,
      settledDate: isSettled ? settledDate : deleteField(),
    },
    { merge: true }
  );

  // When marking as unpaid, remove any previous 'settled' entries
  // so a mistaken "Mark as Paid" is erased rather than stacked.
  if (!isSettled) {
    const historySnap = await getDocs(
      query(settlementHistoryCol(uid, transactionId), where('type', '==', 'settled'))
    );
    const deletes = historySnap.docs.map(d => deleteDoc(doc(settlementHistoryCol(uid, transactionId), d.id)));
    await Promise.all(deletes);
  }

  // Record settlement history
  await recordSettlementHistory(uid, {
    transactionId,
    customerId: (currentData.customerId as string) ?? '',
    type: isSettled ? 'settled' : 'unsettled',
    amount: (currentData.amount as number) ?? 0,
    previousPartialPaid,
    newPartialPaid: previousPartialPaid,
    previousIsSettled: !isSettled,
    newIsSettled: isSettled,
    date: isSettled ? settledDate : new Date().toISOString().split('T')[0],
  });
}

// ── Settlement History ─────────────────────────────────────────────────────────

export async function recordSettlementHistory(
  uid: string,
  params: {
    transactionId: string;
    customerId: string;
    type: 'settled' | 'partial' | 'unsettled';
    amount: number;
    previousPartialPaid: number;
    newPartialPaid: number;
    previousIsSettled: boolean;
    newIsSettled: boolean;
    date: string;
  }
): Promise<void> {
  await addDoc(settlementHistoryCol(uid, params.transactionId), {
    transactionId: params.transactionId,
    customerId: params.customerId,
    type: params.type,
    amount: params.amount,
    previousPartialPaid: params.previousPartialPaid,
    newPartialPaid: params.newPartialPaid,
    previousIsSettled: params.previousIsSettled,
    newIsSettled: params.newIsSettled,
    date: params.date,
    timestamp: serverTimestamp(),
  });
}

export async function getSettlementHistory(uid: string, transactionId: string): Promise<SettlementHistoryEntry[]> {
  const snapshot = await getDocs(
    query(settlementHistoryCol(uid, transactionId), orderBy('timestamp', 'asc'))
  );
  return snapshot.docs.map(d => {
    const data = d.data();
    const ts = data.timestamp as { toMillis?: () => number; seconds?: number; nanoseconds?: number } | null;
    return {
      id: d.id,
      transactionId: (data.transactionId as string) ?? '',
      customerId: (data.customerId as string) ?? '',
      type: (data.type as 'settled' | 'partial' | 'unsettled') ?? 'settled',
      amount: (data.amount as number) ?? 0,
      previousPartialPaid: (data.previousPartialPaid as number) ?? 0,
      newPartialPaid: (data.newPartialPaid as number) ?? 0,
      previousIsSettled: (data.previousIsSettled as boolean) ?? false,
      newIsSettled: (data.newIsSettled as boolean) ?? false,
      date: (data.date as string) ?? '',
      timestamp: ts?.toMillis?.() ?? (ts?.seconds ?? 0) * 1000,
    };
  });
}

export async function getAllSettlementHistory(
  uid: string,
  transactions: { id: string; name: string }[]
): Promise<(SettlementHistoryEntry & { transactionName: string })[]> {
  const results = await Promise.all(
    transactions.map(t => getSettlementHistory(uid, t.id))
  );
  const entries: (SettlementHistoryEntry & { transactionName: string })[] = [];
  for (let i = 0; i < transactions.length; i++) {
    for (const h of results[i]) {
      entries.push({ ...h, transactionName: transactions[i].name });
    }
  }
  return entries.sort((a, b) => b.timestamp - a.timestamp);
}

// ── Account CRUD ──────────────────────────────────────────────────────────────

export async function updateCreditCardDue(
  uid: string,
  accountId: string,
  accountName: string,
  dueAmount: number,
  dueDate: string,
  remindersEnabled: boolean,
  reminderEmail: string,
  reminderWhatsApp: string
): Promise<void> {
  await setDoc(
    doc(accountsCol(uid), accountId),
    {
      name: accountName,
      accountType: 'credit_card',
      type: 'credit_card',
      dueAmount,
      dueDate,
      remindersEnabled,
      reminderEmail,
      reminderWhatsApp,
    },
    { merge: true }
  );
}

// ── Payment CRUD ──────────────────────────────────────────────────────────────

export async function addPayment(
  uid: string,
  accountId: string,
  accountName: string,
  accountKind: AccountKind,
  amount: number,
  date: string
): Promise<void> {
  await addDoc(paymentsCol(uid), {
    accountId,
    accountName,
    accountType: accountKind,
    amount,
    date,
  });
}

// ── Savings CRUD ──────────────────────────────────────────────────────────────

export async function addSavingsEntry(
  uid: string,
  customerId: string,
  customerName: string,
  amount: number,
  type: SavingsType,
  note: string,
  date: string,
  bankAccountId: string = '',
  bankAccountName: string = ''
): Promise<void> {
  await addDoc(savingsCol(uid), { customerId, customerName, amount, type, note, date, bankAccountId, bankAccountName });
}

export async function deleteSavingsEntry(uid: string, entryId: string): Promise<void> {
  await deleteDoc(doc(savingsCol(uid), entryId));
}

// ── Profile ───────────────────────────────────────────────────────────────────

export async function saveProfile(
  uid: string,
  displayName: string,
  businessName: string,
  email: string,
  photoUrl: string = ''
): Promise<void> {
  await setDoc(
    profileDoc(uid),
    {
      uid,
      displayName: displayName.trim(),
      businessName: businessName.trim(),
      email: email.trim(),
      photoUrl: photoUrl.trim(),
      isProfileComplete: true,
    },
    { merge: true }
  );
}

export function listenProfile(
  uid: string,
  onResult: (profile: {
    uid: string;
    displayName: string;
    businessName: string;
    email: string;
    photoUrl: string;
    isProfileComplete: boolean;
  } | null) => void
): Unsubscribe {
  return onSnapshot(profileDoc(uid), snap => {
    if (!snap.exists()) {
      onResult(null);
      return;
    }
    const data = snap.data();
    onResult({
      uid,
      displayName: (data.displayName as string) ?? '',
      businessName: (data.businessName as string) ?? '',
      email: (data.email as string) ?? '',
      photoUrl: (data.photoUrl as string) ?? '',
      isProfileComplete: (data.isProfileComplete as boolean) ?? false,
    });
  });
}

// ── Backup / Restore ──────────────────────────────────────────────────────────

export async function exportBackup(
  uid: string,
  profile: Record<string, unknown>,
  settings: Record<string, unknown>
): Promise<FirestoreBackupPayload> {
  const [customerSnap, accountSnap, txnSnap, paymentSnap, savingsSnap] = await Promise.all([
    getDocs(customersCol(uid)),
    getDocs(accountsCol(uid)),
    getDocs(transactionsCol(uid)),
    getDocs(paymentsCol(uid)),
    getDocs(savingsCol(uid)),
  ]);

  const toRecords = (snap: QuerySnapshot): BackupRecord[] =>
    snap.docs.map(d => ({ id: d.id, fields: d.data() as Record<string, unknown> }));

  return {
    version: 1,
    exportedAt: new Date().toISOString(),
    profile,
    settings,
    customers: toRecords(customerSnap),
    accounts: toRecords(accountSnap),
    transactions: toRecords(txnSnap),
    payments: toRecords(paymentSnap),
    savings: toRecords(savingsSnap),
  };
}

export async function restoreBackup(uid: string, payload: FirestoreBackupPayload): Promise<void> {
  // Delete existing data first
  const [customerSnap, accountSnap, txnSnap, paymentSnap, savingsSnap] = await Promise.all([
    getDocs(customersCol(uid)),
    getDocs(accountsCol(uid)),
    getDocs(transactionsCol(uid)),
    getDocs(paymentsCol(uid)),
    getDocs(savingsCol(uid)),
  ]);

  const BATCH_SIZE = 400;

  async function deleteAll(docs: DocumentSnapshot[]) {
    for (let i = 0; i < docs.length; i += BATCH_SIZE) {
      const batch = writeBatch(db);
      docs.slice(i, i + BATCH_SIZE).forEach(d => batch.delete(d.ref));
      await batch.commit();
    }
  }

  await Promise.all([
    deleteAll(customerSnap.docs),
    deleteAll(accountSnap.docs),
    deleteAll(txnSnap.docs),
    deleteAll(paymentSnap.docs),
    deleteAll(savingsSnap.docs),
  ]);

  // Write new data
  async function writeRecords(col: ReturnType<typeof customersCol>, records: BackupRecord[]) {
    for (let i = 0; i < records.length; i += BATCH_SIZE) {
      const batch = writeBatch(db);
      records.slice(i, i + BATCH_SIZE).forEach(r => {
        batch.set(doc(col, r.id), r.fields);
      });
      await batch.commit();
    }
  }

  await Promise.all([
    writeRecords(customersCol(uid), payload.customers),
    writeRecords(accountsCol(uid), payload.accounts),
    writeRecords(transactionsCol(uid), payload.transactions),
    writeRecords(paymentsCol(uid), payload.payments),
    writeRecords(savingsCol(uid), payload.savings),
  ]);
}
