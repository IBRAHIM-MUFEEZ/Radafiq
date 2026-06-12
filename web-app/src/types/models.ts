// ── Account Types ─────────────────────────────────────────────────────────────

export type AccountKind = 'bank_account' | 'credit_card' | 'person';

export const ACCOUNT_KIND_LABELS: Record<AccountKind, string> = {
  bank_account: 'Bank Account',
  credit_card: 'Credit Card',
  person: 'Person',
};

export function accountKindFromStorage(value: string | undefined): AccountKind {
  switch (value?.toLowerCase()) {
    case 'bank_account':
    case 'bank':
    case 'account':
      return 'bank_account';
    case 'credit_card':
    case 'credit':
    case 'card':
      return 'credit_card';
    case 'person':
      return 'person';
    default:
      return 'credit_card';
  }
}

// ── Indian Account Catalog ────────────────────────────────────────────────────

export interface AccountOption {
  id: string;
  name: string;
  accountKind: AccountKind;
}

export const BANK_ACCOUNTS: AccountOption[] = [
  { id: 'bank_sbi', name: 'State Bank of India (SBI)', accountKind: 'bank_account' },
  { id: 'bank_hdfc', name: 'HDFC Bank', accountKind: 'bank_account' },
  { id: 'bank_icici', name: 'ICICI Bank', accountKind: 'bank_account' },
  { id: 'bank_axis', name: 'Axis Bank', accountKind: 'bank_account' },
  { id: 'bank_kotak', name: 'Kotak Mahindra Bank', accountKind: 'bank_account' },
  { id: 'bank_pnb', name: 'Punjab National Bank', accountKind: 'bank_account' },
  { id: 'bank_bob', name: 'Bank of Baroda', accountKind: 'bank_account' },
  { id: 'bank_union', name: 'Union Bank of India', accountKind: 'bank_account' },
  { id: 'bank_canara', name: 'Canara Bank', accountKind: 'bank_account' },
  { id: 'bank_idfc_first', name: 'IDFC FIRST Bank', accountKind: 'bank_account' },
  { id: 'bank_indusind', name: 'IndusInd Bank', accountKind: 'bank_account' },
  { id: 'bank_au', name: 'AU Small Finance Bank', accountKind: 'bank_account' },
  { id: 'bank_yes', name: 'YES BANK', accountKind: 'bank_account' },
  { id: 'bank_idbi', name: 'IDBI Bank', accountKind: 'bank_account' },
  { id: 'bank_federal', name: 'Federal Bank', accountKind: 'bank_account' },
  { id: 'bank_rbl', name: 'RBL Bank', accountKind: 'bank_account' },
];

export const CREDIT_CARDS: AccountOption[] = [
  { id: 'card_sbi', name: 'SBI Card', accountKind: 'credit_card' },
  { id: 'card_hdfc', name: 'HDFC Bank Credit Card', accountKind: 'credit_card' },
  { id: 'card_icici', name: 'ICICI Bank Credit Card', accountKind: 'credit_card' },
  { id: 'card_axis', name: 'Axis Bank Credit Card', accountKind: 'credit_card' },
  { id: 'card_kotak', name: 'Kotak Mahindra Credit Card', accountKind: 'credit_card' },
  { id: 'card_indusind', name: 'IndusInd Bank Credit Card', accountKind: 'credit_card' },
  { id: 'card_idfc_first', name: 'IDFC FIRST Bank Credit Card', accountKind: 'credit_card' },
  { id: 'card_amex', name: 'American Express India', accountKind: 'credit_card' },
  { id: 'card_standard_chartered', name: 'Standard Chartered Credit Card', accountKind: 'credit_card' },
  { id: 'card_hsbc', name: 'HSBC Credit Card', accountKind: 'credit_card' },
  { id: 'card_au', name: 'AU Bank Credit Card', accountKind: 'credit_card' },
  { id: 'card_yes', name: 'YES BANK Credit Card', accountKind: 'credit_card' },
  { id: 'card_rbl', name: 'RBL Bank Credit Card', accountKind: 'credit_card' },
  { id: 'card_onecard', name: 'OneCard', accountKind: 'credit_card' },
  { id: 'card_amazon_icici', name: 'Amazon Pay ICICI Card', accountKind: 'credit_card' },
  { id: 'card_flipkart_axis', name: 'Flipkart Axis Bank Credit Card', accountKind: 'credit_card' },
  { id: 'card_jupiter', name: 'Jupiter Credit Card', accountKind: 'credit_card' },
  { id: 'card_irctc_hdfc', name: 'IRCTC HDFC Credit Card', accountKind: 'credit_card' },
];

export const ALL_ACCOUNTS: AccountOption[] = [...BANK_ACCOUNTS, ...CREDIT_CARDS];

export function getAccountOptions(kind: AccountKind, selectedIds?: Set<string>): AccountOption[] {
  const base = kind === 'bank_account' ? BANK_ACCOUNTS : kind === 'credit_card' ? CREDIT_CARDS : [];
  if (!selectedIds) return base;
  return base.filter(a => selectedIds.has(a.id));
}

export function getAccountById(id: string): AccountOption | undefined {
  return ALL_ACCOUNTS.find(a => a.id === id);
}

export function defaultSelectedAccountIds(): Set<string> {
  return new Set(ALL_ACCOUNTS.map(a => a.id));
}

// ── Core Data Models ──────────────────────────────────────────────────────────

export interface CustomerTransaction {
  id: string;
  customerId: string;
  name: string;
  accountId: string;
  accountName: string;
  accountKind: AccountKind;
  amount: number;
  transactionDate: string;
  isSettled: boolean;
  settledDate: string;
  partialPaidAmount: number;
  dueDate: string;
  personName: string;
  splitGroupId: string;
  emiGroupId: string;
  emiIndex: number;
  emiTotal: number;
  // Computed helpers
  isEmi?: boolean;
  isSplit?: boolean;
}

export function isEmi(t: CustomerTransaction): boolean {
  return t.emiGroupId !== '';
}

export function isSplit(t: CustomerTransaction): boolean {
  return t.splitGroupId !== '';
}

export function isScheduledForFutureMonth(t: CustomerTransaction, referenceDate: Date = new Date()): boolean {
  if (!isEmi(t)) return false;
  const installmentDate = new Date(t.transactionDate);
  if (isNaN(installmentDate.getTime())) return false;
  const refYear = referenceDate.getFullYear();
  const refMonth = referenceDate.getMonth();
  const instYear = installmentDate.getFullYear();
  const instMonth = installmentDate.getMonth();
  return instYear > refYear || (instYear === refYear && instMonth > refMonth);
}

/**
 * Returns true if this EMI installment should appear in the transaction list.
 *
 * Rules:
 * - Non-EMI transactions are always visible.
 * - EMI installments are visible when ANY of these is true:
 *   1. The transaction date is in the current month or past (original behaviour).
 *   2. The due date is within the next 20 days (show upcoming dues early).
 */
export function isVisibleInTransactions(t: CustomerTransaction, referenceDate: Date = new Date()): boolean {
  if (!isEmi(t)) return true;

  // Rule 1: transaction date is current month or past
  if (!isScheduledForFutureMonth(t, referenceDate)) return true;

  // Rule 2: due date is within 20 days from today
  if (t.dueDate) {
    const due = new Date(t.dueDate);
    if (!isNaN(due.getTime())) {
      const msIn20Days = 20 * 24 * 60 * 60 * 1000;
      if (due.getTime() - referenceDate.getTime() <= msIn20Days) return true;
    }
  }

  return false;
}

/** Returns true if the EMI installment's due date has passed and it is not settled. */
export function isEmiOverdue(t: CustomerTransaction, referenceDate: Date = new Date()): boolean {
  if (!isEmi(t) || t.isSettled) return false;
  if (!t.dueDate) return false;
  const due = new Date(t.dueDate);
  if (isNaN(due.getTime())) return false;
  return due < referenceDate;
}

/** Returns the number of days until (positive) or since (negative) the due date. */
export function daysUntilDue(t: CustomerTransaction, referenceDate: Date = new Date()): number | null {
  if (!t.dueDate) return null;
  const due = new Date(t.dueDate);
  if (isNaN(due.getTime())) return null;
  const diffMs = due.getTime() - referenceDate.getTime();
  return Math.ceil(diffMs / (24 * 60 * 60 * 1000));
}

export interface SavingsEntry {
  id: string;
  customerId: string;
  customerName: string;
  amount: number;
  type: 'deposit' | 'withdrawal';
  note: string;
  date: string;
  bankAccountId: string;
  bankAccountName: string;
}

export interface CustomerSummary {
  id: string;
  name: string;
  totalAmount: number;
  creditDueAmount: number;
  manualPaidAmount: number;
  settledTransactionAmount: number;
  partialPaidAmount: number;
  balance: number;
  transactions: CustomerTransaction[];
  isDeleted: boolean;
  savingsBalance: number;
  savingsEntries: SavingsEntry[];
}

export interface CardSummary {
  id: string;
  name: string;
  accountKind: AccountKind;
  bill: number;
  pending: number;
  payable: number;
  dueAmount: number;
  dueDate: string;
  remindersEnabled: boolean;
  reminderEmail: string;
  reminderWhatsApp: string;
}

export function hasLedgerActivity(card: CardSummary): boolean {
  return (
    card.bill > 0 ||
    card.pending > 0 ||
    card.dueAmount > 0 ||
    card.dueDate !== '' ||
    card.remindersEnabled ||
    card.reminderEmail !== '' ||
    card.reminderWhatsApp !== ''
  );
}

export interface SplitEntry {
  accountKind: AccountKind;
  accountId: string;
  accountName: string;
  personName: string;
  amount: string;
}

// ── User Profile ──────────────────────────────────────────────────────────────

export interface UserProfile {
  uid: string;
  displayName: string;
  businessName: string;
  email: string;
  photoUrl: string;
  isProfileComplete: boolean;
}

// ── Backup Models ─────────────────────────────────────────────────────────────

export interface BackupRecord {
  id: string;
  fields: Record<string, unknown>;
}

export interface FirestoreBackupPayload {
  version: number;
  exportedAt: string;
  profile: Record<string, unknown>;
  settings: Record<string, unknown>;
  customers: BackupRecord[];
  accounts: BackupRecord[];
  transactions: BackupRecord[];
  payments: BackupRecord[];
  savings: BackupRecord[];
}

// ── App Settings ──────────────────────────────────────────────────────────────

export type AppThemeMode = 'LIGHT' | 'DARK';

export interface AppSettings {
  themeMode: AppThemeMode;
  selectedAccountIds: Set<string>;
  knownAccountIds: Set<string>;
  lastDriveBackupTime: string | null;
  lastDriveRestoreTime: string | null;
}

// ── Settlement History ─────────────────────────────────────────────────────────

export interface SettlementHistoryEntry {
  id: string;
  transactionId: string;
  customerId: string;
  type: 'settled' | 'partial' | 'unsettled';
  amount: number;
  previousPartialPaid: number;
  newPartialPaid: number;
  previousIsSettled: boolean;
  newIsSettled: boolean;
  date: string;
  timestamp: number;
}

export const SETTLEMENT_TYPE_LABELS: Record<string, string> = {
  settled: 'Fully Settled',
  partial: 'Partial Payment',
  unsettled: 'Marked Unpaid',
};

// ── App Security ──────────────────────────────────────────────────────────────

export interface AppSecurityState {
  lockEnabled: boolean;
  hasPasscode: boolean;
  recoveryQuestion: string;
  hasRecoveryQuestion: boolean;
  isUnlocked: boolean;
}
