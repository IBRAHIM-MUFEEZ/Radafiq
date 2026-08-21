/**
 * EMI Activity Card Due Split — Bug Condition Exploration Test
 *
 * Task 1: Validates Property 1 (Bug Condition)
 * Validates: Requirements 1.1, 1.3, 2.1, 2.3
 *
 * This test MUST FAIL on unfixed code — the failure IS the confirmation that
 * the bug exists. Do NOT attempt to fix the test or the production code when
 * this test fails.
 *
 * Bug condition: A visible unsettled EMI installment (isVisibleInTransactions === true)
 * is unconditionally routed to emiOutstandingByAccount instead of nonEmiDueByAccount.
 * The test asserts the EXPECTED (post-fix) behavior, so it fails on unfixed code.
 *
 * Expected counterexample on unfixed code:
 *   "visible EMI installment (dueDate -30d, ₹5000) ends up in
 *    emiOutstandingByAccount instead of nonEmiDueByAccount"
 */

import { describe, it, expect } from 'vitest';
import { computeEmiDueMaps } from '../pages/Dashboard';
import { CustomerTransaction, CustomerSummary, isVisibleInTransactions } from '../types/models';

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Build a YYYY-MM-DD string for today ± N calendar days. */
function dateOffset(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${dd}`;
}

/** Wrap a single transaction in a minimal CustomerSummary. */
function wrapInCustomer(t: CustomerTransaction): CustomerSummary {
  return {
    id: 'cust-1',
    name: 'Test Customer',
    totalAmount: t.amount,
    creditDueAmount: 0,
    manualPaidAmount: 0,
    settledTransactionAmount: 0,
    partialPaidAmount: t.partialPaidAmount,
    balance: t.amount - t.partialPaidAmount,
    transactions: [t],
    isDeleted: false,
    savingsBalance: 0,
    savingsEntries: [],
  };
}

/** Build a minimal visible unsettled EMI installment (the bug condition). */
function makeVisibleEmiTransaction(): CustomerTransaction {
  // dueDate 30 days ago → effectiveDate = dueDate - 20d = 50 days ago → isVisibleInTransactions = true
  return {
    id: 'txn-1',
    customerId: 'cust-1',
    name: 'Test Customer',
    accountId: 'acc-1',
    accountName: 'HDFC Bank Credit Card',
    accountKind: 'credit_card',
    amount: 5000,
    transactionDate: dateOffset(-30),
    isSettled: false,
    settledDate: '',
    partialPaidAmount: 0,
    dueDate: dateOffset(-30),         // 30 days in the past
    personName: '',
    splitGroupId: '',
    emiGroupId: 'group-1',            // non-empty → this is an EMI installment
    emiIndex: 1,
    emiTotal: 6,
  };
}

// ── Sanity guard ──────────────────────────────────────────────────────────────

it('sanity: isVisibleInTransactions returns true for dueDate -30d (confirms test setup)', () => {
  const t = makeVisibleEmiTransaction();
  // Must be true — if this fails, the dueDate offset is wrong
  expect(isVisibleInTransactions(t)).toBe(true);
});

// ── Bug condition exploration test ────────────────────────────────────────────

/**
 * Property 1 — Bug Condition: Visible EMI Installments Route to Current Due
 *
 * For any transaction t where isBugCondition(t) is true:
 *   t.emiGroupId ≠ ''  AND  !t.isSettled  AND  due > 0  AND  isVisibleInTransactions(t) === true
 *
 * The fixed computeEmiDueMaps SHALL place (t.amount − t.partialPaidAmount) in
 * nonEmiDueByAccount[t.accountId] and SHALL NOT place it in emiOutstandingByAccount.
 *
 * Validates: Requirements 2.1, 2.3
 *
 * EXPECTED OUTCOME ON UNFIXED CODE:
 *   - nonEmiDueByAccount.get('acc-1') is undefined (not 5000) → test FAILS ✗
 *   - emiOutstandingByAccount.get('acc-1') is 5000 (not undefined) → test FAILS ✗
 *
 * Counterexample: visible EMI installment (dueDate -30d, ₹5000) ends up in
 * emiOutstandingByAccount instead of nonEmiDueByAccount.
 */
describe('Bug Condition — Property 1: visible EMI installments route to Current Due', () => {
  it('routes a visible unsettled EMI installment to nonEmiDueByAccount (Current Due)', () => {
    const t = makeVisibleEmiTransaction();
    const customers = [wrapInCustomer(t)];
    const expectedDue = t.amount - t.partialPaidAmount; // 5000

    const { emiOutstandingByAccount, nonEmiDueByAccount } = computeEmiDueMaps(customers);

    // The full due amount must appear in Current Due
    expect(nonEmiDueByAccount.get('acc-1')).toBe(expectedDue);

    // None of that amount must leak into EMI Outstanding
    expect(emiOutstandingByAccount.get('acc-1')).toBeUndefined();
  });
});

// ── Preservation property tests (Task 2) ─────────────────────────────────────
//
// These six cases verify the BASELINE behavior on UNFIXED code.
// All six MUST PASS before the fix is applied, and MUST CONTINUE TO PASS
// after the fix (regression guard).
//
// Validates: Requirements 2.2, 2.4, 3.1, 3.2, 3.3, 3.4, 3.5

import { isScheduledForFutureMonth } from '../types/models';

describe('Preservation — baseline routing that must not regress after fix', () => {

  // ── Case A ────────────────────────────────────────────────────────────────
  it('Case A: future EMI installment (isVisibleInTransactions=false) stays in EMI Outstanding', () => {
    // dueDate 60 days ahead → effectiveDate = dueDate - 20d = 40 days ahead → not yet visible
    const t: CustomerTransaction = {
      id: 'txn-a',
      customerId: 'cust-2',
      name: 'Test Customer A',
      accountId: 'acc-2',
      accountName: 'HDFC Bank Credit Card',
      accountKind: 'credit_card',
      amount: 5000,
      transactionDate: dateOffset(60),
      isSettled: false,
      settledDate: '',
      partialPaidAmount: 0,
      dueDate: dateOffset(60),        // 60 days in the future
      personName: '',
      splitGroupId: '',
      emiGroupId: 'group-2',          // EMI installment
      emiIndex: 1,
      emiTotal: 6,
    };
    const { emiOutstandingByAccount, nonEmiDueByAccount } = computeEmiDueMaps([wrapInCustomer(t)]);

    expect(emiOutstandingByAccount.get('acc-2')).toBe(5000);
    expect(nonEmiDueByAccount.get('acc-2')).toBeUndefined();
  });

  // ── Case B ────────────────────────────────────────────────────────────────
  it('Case B: non-EMI unsettled transaction in current month lands in Current Due', () => {
    const t: CustomerTransaction = {
      id: 'txn-b',
      customerId: 'cust-3',
      name: 'Test Customer B',
      accountId: 'acc-3',
      accountName: 'SBI Card',
      accountKind: 'credit_card',
      amount: 2000,
      transactionDate: dateOffset(0),  // today — current month, not future
      isSettled: false,
      settledDate: '',
      partialPaidAmount: 0,
      dueDate: '',
      personName: '',
      splitGroupId: '',
      emiGroupId: '',                  // non-EMI
      emiIndex: 0,
      emiTotal: 0,
    };
    const { emiOutstandingByAccount, nonEmiDueByAccount } = computeEmiDueMaps([wrapInCustomer(t)]);

    expect(nonEmiDueByAccount.get('acc-3')).toBe(2000);
    expect(emiOutstandingByAccount.get('acc-3')).toBeUndefined();
  });

  // ── Case C ────────────────────────────────────────────────────────────────
  it('Case C: non-EMI transaction in a future calendar month still lands in Current Due (isScheduledForFutureMonth only gates EMI)', () => {
    // Build the first day of next month
    const now = new Date();
    const nextMonth = new Date(now.getFullYear(), now.getMonth() + 1, 1);
    const y = nextMonth.getFullYear();
    const m = String(nextMonth.getMonth() + 1).padStart(2, '0');
    const nextMonthFirst = `${y}-${m}-01`;

    const t: CustomerTransaction = {
      id: 'txn-c',
      customerId: 'cust-4',
      name: 'Test Customer C',
      accountId: 'acc-4',
      accountName: 'Axis Bank Credit Card',
      accountKind: 'credit_card',
      amount: 3000,
      transactionDate: nextMonthFirst, // first of next month — future calendar month
      isSettled: false,
      settledDate: '',
      partialPaidAmount: 0,
      dueDate: '',
      personName: '',
      splitGroupId: '',
      emiGroupId: '',                  // non-EMI
      emiIndex: 0,
      emiTotal: 0,
    };

    // isScheduledForFutureMonth short-circuits to false for non-EMI transactions,
    // so the !isScheduledForFutureMonth guard is always true for non-EMI — the
    // transaction lands in nonEmiDueByAccount regardless of its transactionDate.
    expect(isScheduledForFutureMonth(t)).toBe(false);

    const { emiOutstandingByAccount, nonEmiDueByAccount } = computeEmiDueMaps([wrapInCustomer(t)]);

    // Observed baseline: non-EMI future-dated txn goes to Current Due (not excluded)
    expect(nonEmiDueByAccount.get('acc-4')).toBe(3000);
    expect(emiOutstandingByAccount.get('acc-4')).toBeUndefined();
  });

  // ── Case D ────────────────────────────────────────────────────────────────
  it('Case D: person-account transaction is excluded from both maps', () => {
    const t: CustomerTransaction = {
      id: 'txn-d',
      customerId: 'cust-5',
      name: 'Test Customer D',
      accountId: 'acc-person',
      accountName: 'John Doe',
      accountKind: 'person',           // person account → always skipped
      amount: 1000,
      transactionDate: dateOffset(0),
      isSettled: false,
      settledDate: '',
      partialPaidAmount: 0,
      dueDate: '',
      personName: 'John Doe',
      splitGroupId: '',
      emiGroupId: '',
      emiIndex: 0,
      emiTotal: 0,
    };
    const { emiOutstandingByAccount, nonEmiDueByAccount } = computeEmiDueMaps([wrapInCustomer(t)]);

    expect(emiOutstandingByAccount.size).toBe(0);
    expect(nonEmiDueByAccount.size).toBe(0);
  });

  // ── Case E ────────────────────────────────────────────────────────────────
  it('Case E: fully settled transaction contributes zero to both maps', () => {
    const t: CustomerTransaction = {
      id: 'txn-e',
      customerId: 'cust-6',
      name: 'Test Customer E',
      accountId: 'acc-6',
      accountName: 'ICICI Bank Credit Card',
      accountKind: 'credit_card',
      amount: 3000,
      transactionDate: dateOffset(0),
      isSettled: true,                 // fully settled → due = 0
      settledDate: dateOffset(-1),
      partialPaidAmount: 0,
      dueDate: '',
      personName: '',
      splitGroupId: '',
      emiGroupId: '',
      emiIndex: 0,
      emiTotal: 0,
    };
    const { emiOutstandingByAccount, nonEmiDueByAccount } = computeEmiDueMaps([wrapInCustomer(t)]);

    expect(emiOutstandingByAccount.size).toBe(0);
    expect(nonEmiDueByAccount.size).toBe(0);
  });

  // ── Case F ────────────────────────────────────────────────────────────────
  it('Case F: partially paid future EMI uses (amount − partialPaidAmount) in EMI Outstanding', () => {
    // dueDate 60 days ahead → future installment → goes to emiOutstandingByAccount
    const t: CustomerTransaction = {
      id: 'txn-f',
      customerId: 'cust-7',
      name: 'Test Customer F',
      accountId: 'acc-5',
      accountName: 'Kotak Credit Card',
      accountKind: 'credit_card',
      amount: 6000,
      transactionDate: dateOffset(60),
      isSettled: false,
      settledDate: '',
      partialPaidAmount: 1500,         // 6000 − 1500 = 4500 due
      dueDate: dateOffset(60),        // 60 days in the future
      personName: '',
      splitGroupId: '',
      emiGroupId: 'group-3',          // EMI installment
      emiIndex: 2,
      emiTotal: 6,
    };
    const { emiOutstandingByAccount, nonEmiDueByAccount } = computeEmiDueMaps([wrapInCustomer(t)]);

    expect(emiOutstandingByAccount.get('acc-5')).toBe(4500);
    expect(nonEmiDueByAccount.get('acc-5')).toBeUndefined();
  });

});
