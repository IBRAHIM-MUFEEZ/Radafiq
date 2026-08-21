# EMI Activity Card Due Split — Bugfix Design

## Overview

The Dashboard "Account Activity" section computes two financial summary values per credit-card account — **Current Due** and **EMI Outstanding** — inside a single `useMemo` in `Dashboard.tsx`. The current implementation routes every unsettled EMI installment (regardless of whether it has already become payable) into the EMI Outstanding bucket. The fix adds a single conditional inside the EMI branch: if `isVisibleInTransactions(t)` is `true`, the installment's due amount goes to `nonEmiMap` (Current Due); if it is `false`, it stays in `emiMap` (EMI Outstanding). No other code path is changed.

## Glossary

- **Bug_Condition (C)**: An unsettled EMI installment whose effective date has been reached (`isVisibleInTransactions(t) === true`) — these are misrouted to `emiMap` instead of `nonEmiMap`.
- **Property (P)**: The desired post-fix behavior — visible unsettled EMI amounts appear in Current Due; future (not-yet-visible) EMI amounts appear in EMI Outstanding.
- **Preservation**: All routing logic for non-EMI transactions, fully settled transactions, person-account transactions, and future EMI installments must remain byte-for-byte equivalent to the original.
- **`isVisibleInTransactions(t)`**: Helper in `src/types/models.ts` that returns `true` when an EMI installment's effective date (`dueDate − 20 days`) has reached or passed today. Non-EMI transactions always return `true`.
- **`isScheduledForFutureMonth(t)`**: Helper used by the non-EMI branch to exclude transactions dated in a future calendar month.
- **`emiMap`**: Internal `Map<string, number>` that accumulates EMI Outstanding amounts keyed by `accountId`.
- **`nonEmiMap`**: Internal `Map<string, number>` that accumulates Current Due amounts keyed by `accountId`.

## Bug Details

### Bug Condition

The bug manifests inside the `useMemo` that produces `emiOutstandingByAccount` and `nonEmiDueByAccount` in `Dashboard.tsx`. When a transaction has a non-empty `emiGroupId`, the current code unconditionally adds its due amount to `emiMap` without checking whether the installment is already visible (i.e., payable today). Visible installments that belong in Current Due are therefore never placed in `nonEmiMap`.

**Formal Specification:**

```
FUNCTION isBugCondition(t: CustomerTransaction)
  INPUT:  t of type CustomerTransaction
  OUTPUT: boolean

  RETURN t.emiGroupId ≠ ''
    AND NOT t.isSettled
    AND (t.amount - t.partialPaidAmount) > 0
    AND isVisibleInTransactions(t) = true
END FUNCTION
```

### Examples

- **Visible EMI installment (bug)**: An EMI installment with `dueDate = "2025-06-15"` is reached; `isVisibleInTransactions` returns `true`. The installment's ₹5,000 due amount is added to `emiMap` → Current Due shows ₹0, EMI Outstanding shows ₹5,000. Expected: Current Due ₹5,000, EMI Outstanding ₹0 for that installment.
- **Future EMI installment (no bug)**: An EMI installment with `dueDate = "2025-10-15"` is not yet visible; `isVisibleInTransactions` returns `false`. The ₹5,000 due amount goes into `emiMap`. Correct as-is — this is the pipeline amount.
- **Mixed account**: An account has two installments — one visible (₹3,000), one future (₹3,000). Bug: Current Due = ₹0, EMI Outstanding = ₹6,000. Expected after fix: Current Due = ₹3,000, EMI Outstanding = ₹3,000.
- **Non-EMI transaction (no bug)**: A regular ₹2,000 transaction in the current month goes to `nonEmiMap` unchanged. Fix must not alter this path.

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Non-EMI (regular) unsettled transactions that are not scheduled for a future month continue to accumulate in `nonEmiMap`.
- Non-EMI transactions scheduled for a future month continue to be excluded from both maps.
- Transactions on `person`-kind accounts continue to be skipped entirely.
- Fully settled transactions (`isSettled === true`) continue to contribute zero to both maps.
- Partially paid transactions continue to use `amount − partialPaidAmount` as the due amount.
- Future (not-yet-visible) EMI installments continue to accumulate in `emiMap`.
- The EMI Outstanding pill continues to be hidden when `emiOutstanding === 0`.
- Bank account payable amounts in `ActivityCard` continue to be sourced from `card.payable`, which is unaffected by this change.

**Scope:**
Only the EMI branch (`if (t.emiGroupId)`) inside the `useMemo` is modified. Every other branch, map, and downstream consumer is untouched.

## Hypothesized Root Cause

1. **Missing visibility check in the EMI branch**: The EMI branch uses only `t.emiGroupId` as its routing criterion. Unlike the non-EMI branch (which calls `isScheduledForFutureMonth`), the EMI branch has no analogous temporal check. Adding `isVisibleInTransactions(t)` as the routing discriminator is the targeted fix.

2. **`isVisibleInTransactions` not called for EMIs at the `useMemo` level**: The function is already imported and used elsewhere in `Dashboard.tsx` (the `personSummaries` memo), so no new import or utility is needed.

3. **No secondary data source involved**: `emiMap` and `nonEmiMap` are fully recomputed from `customers` on every render. The bug is entirely local to this one memo — there is no stale cache or Firestore read to blame.

## Correctness Properties

Property 1: Bug Condition — Visible EMI Installments Route to Current Due

_For any_ transaction `t` where `isBugCondition(t)` returns `true` (i.e., `t.emiGroupId ≠ ''`, unsettled, `due > 0`, and `isVisibleInTransactions(t) === true`), the fixed `useMemo` SHALL add `(t.amount − t.partialPaidAmount)` to `nonEmiMap[t.accountId]` and SHALL NOT add that amount to `emiMap[t.accountId]`.

**Validates: Requirements 2.1, 2.3**

Property 2: Preservation — Future EMI Installments Stay in EMI Outstanding

_For any_ transaction `t` where `t.emiGroupId ≠ ''`, unsettled, `due > 0`, and `isVisibleInTransactions(t) === false`, the fixed `useMemo` SHALL add `(t.amount − t.partialPaidAmount)` to `emiMap[t.accountId]` — identical to the original behavior.

**Validates: Requirements 2.2, 2.4, 3.1**

Property 3: Preservation — Non-EMI and Person Routing Unchanged

_For any_ transaction `t` where `isBugCondition(t)` returns `false` AND `t.emiGroupId === ''` (non-EMI) or `t.accountKind === 'person'`, the fixed `useMemo` SHALL produce exactly the same map entries as the original function.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**

## Fix Implementation

### Changes Required

**File:** `web-app/src/pages/Dashboard.tsx`

**Function:** `useMemo` callback that returns `{ emiOutstandingByAccount, nonEmiDueByAccount }` (lines ~198–220)

**Specific Changes:**

1. **Split the EMI branch on `isVisibleInTransactions`**: Replace the single `emiMap.set(...)` call with an `if/else` that routes visible installments to `nonEmiMap` and future installments to `emiMap`.

2. **Hoist `today` outside the inner loop**: Currently `new Date()` is called once per non-EMI transaction inside the loop. Move it to the top of the memo callback so it is computed once and shared by both the EMI and non-EMI branches.

**Before (broken):**
```typescript
const { emiOutstandingByAccount, nonEmiDueByAccount } = useMemo(() => {
  const emiMap    = new Map<string, number>();
  const nonEmiMap = new Map<string, number>();
  customers.forEach(customer => {
    customer.transactions.forEach(t => {
      if (t.accountKind === 'person') return;
      const due = t.isSettled ? 0 : Math.max(0, t.amount - t.partialPaidAmount);
      if (due <= 0) return;
      if (t.emiGroupId) {
        emiMap.set(t.accountId, (emiMap.get(t.accountId) ?? 0) + due);
      } else {
        const today = new Date();
        if (!isScheduledForFutureMonth(t, today)) {
          nonEmiMap.set(t.accountId, (nonEmiMap.get(t.accountId) ?? 0) + due);
        }
      }
    });
  });
  return { emiOutstandingByAccount: emiMap, nonEmiDueByAccount: nonEmiMap };
}, [customers]);
```

**After (fixed):**
```typescript
const { emiOutstandingByAccount, nonEmiDueByAccount } = useMemo(() => {
  const emiMap    = new Map<string, number>();
  const nonEmiMap = new Map<string, number>();
  const today     = new Date();                        // hoisted — computed once
  customers.forEach(customer => {
    customer.transactions.forEach(t => {
      if (t.accountKind === 'person') return;
      const due = t.isSettled ? 0 : Math.max(0, t.amount - t.partialPaidAmount);
      if (due <= 0) return;
      if (t.emiGroupId) {
        // Visible installments are payable now → Current Due
        // Future installments are still in the pipeline → EMI Outstanding
        if (isVisibleInTransactions(t, today)) {
          nonEmiMap.set(t.accountId, (nonEmiMap.get(t.accountId) ?? 0) + due);
        } else {
          emiMap.set(t.accountId, (emiMap.get(t.accountId) ?? 0) + due);
        }
      } else {
        if (!isScheduledForFutureMonth(t, today)) {
          nonEmiMap.set(t.accountId, (nonEmiMap.get(t.accountId) ?? 0) + due);
        }
      }
    });
  });
  return { emiOutstandingByAccount: emiMap, nonEmiDueByAccount: nonEmiMap };
}, [customers]);
```

**No import changes needed** — `isVisibleInTransactions` is already imported from `../types/models` on line 8 of `Dashboard.tsx`.

## Testing Strategy

### Validation Approach

Two-phase approach: first surface counterexamples on the unfixed code to confirm the root cause, then verify the fix satisfies both correctness properties and all preservation requirements.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bug on the UNFIXED code and confirm that the missing `isVisibleInTransactions` check is the sole root cause.

**Test Plan**: Construct a minimal `customers` array containing a visible unsettled EMI installment and pass it to the `useMemo` logic. Assert that `nonEmiDueByAccount` is empty (counterexample confirming the bug) and `emiOutstandingByAccount` contains the installment's due amount (confirming the misrouting).

**Test Cases:**

1. **Single visible EMI installment** — `dueDate` set 30 days ago, `isSettled = false`, `partialPaidAmount = 0`. Assert `nonEmiDueByAccount` is empty on unfixed code (will fail on fixed code, confirming the fix works).
2. **Mixed visible + future installments** — two installments on the same account, one visible, one future. Assert on unfixed code that `emiOutstandingByAccount.get(accountId)` equals the sum of both (counterexample); assert on fixed code that each map contains only its respective amount.
3. **Fully future EMI installment** — `dueDate` set 60 days in the future. Assert `emiOutstandingByAccount` contains the amount on both unfixed and fixed code (no regression).
4. **Settled visible EMI installment** — `isSettled = true`. Assert neither map contains any amount (edge case, both versions should behave identically).

**Expected Counterexamples:**
- On unfixed code: `nonEmiDueByAccount` is empty even when a visible unsettled EMI installment is present.
- On unfixed code: `emiOutstandingByAccount` is inflated by visible installment amounts that belong in Current Due.

### Fix Checking

**Goal**: Verify that for all inputs where `isBugCondition(t)` is true, the fixed function places the due amount in `nonEmiMap`.

**Pseudocode:**
```
FOR ALL t WHERE isBugCondition(t) DO
  result_fixed ← computeMaps_fixed([t])
  ASSERT result_fixed.nonEmiDueByAccount.get(t.accountId) = (t.amount - t.partialPaidAmount)
  ASSERT result_fixed.emiOutstandingByAccount.get(t.accountId) = undefined OR 0
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where `isBugCondition(t)` is false, the fixed function produces the same map entries as the original.

**Pseudocode:**
```
FOR ALL t WHERE NOT isBugCondition(t) DO
  result_original ← computeMaps_original([t])
  result_fixed    ← computeMaps_fixed([t])
  ASSERT result_original.emiOutstandingByAccount = result_fixed.emiOutstandingByAccount
  ASSERT result_original.nonEmiDueByAccount      = result_fixed.nonEmiDueByAccount
END FOR
```

**Testing Approach**: Property-based testing is well-suited here because:
- The input space (combinations of `isSettled`, `partialPaidAmount`, `emiGroupId`, `dueDate`, `accountKind`) is large.
- The preservation invariant must hold across all non-buggy inputs, not just hand-picked examples.
- PBT generators can produce transactions that stress edge cases (zero amounts, missing dates, partial payments near the full amount).

**Test Cases:**
1. **Non-EMI transaction preservation** — generate random non-EMI transactions (visible, current month) and verify both versions produce identical `nonEmiDueByAccount` entries.
2. **Future-month non-EMI exclusion preservation** — generate non-EMI transactions with `transactionDate` in a future month and verify both versions exclude them from both maps.
3. **Person-account exclusion preservation** — generate transactions with `accountKind = 'person'` and verify neither version adds them to either map.
4. **Future EMI installment preservation** — generate EMI transactions with `dueDate` far in the future and verify both versions place them in `emiOutstandingByAccount` unchanged.
5. **Settled transaction preservation** — generate settled transactions of any type and verify both versions contribute zero to both maps.

### Unit Tests

- Compute maps with a single visible unsettled EMI installment; assert it lands in `nonEmiDueByAccount`.
- Compute maps with a single future EMI installment; assert it lands in `emiOutstandingByAccount`.
- Compute maps with multiple installments of the same account (mixed visible/future); assert per-account totals are split correctly.
- Compute maps with partial-paid visible EMI; assert `amount − partialPaidAmount` is used.
- Compute maps with `accountKind = 'person'`; assert both maps are empty.

### Property-Based Tests

- Generate random `CustomerTransaction` arrays; assert that for every item where `isBugCondition(t)` is true, the fixed memo places `due` in `nonEmiDueByAccount`.
- Generate random `CustomerTransaction` arrays; assert that for every item where `t.emiGroupId !== '' && !isVisibleInTransactions(t)`, the fixed memo places `due` in `emiOutstandingByAccount`.
- Generate random non-EMI, non-person transactions; assert the fixed and original memos produce identical maps.

### Integration Tests

- Full Dashboard render with a seeded `customers` array containing a mix of visible EMI, future EMI, and regular transactions; assert the `currentDue` and `emiOutstanding` props passed to each `ActivityCard` match expected values.
- Dashboard render after simulating time advancing past an installment's effective date; assert the pill values update correctly (visible → Current Due, remaining future → EMI Outstanding).
- Dashboard render for a bank account; assert `card.payable` is displayed unchanged and neither `currentDue` nor `emiOutstanding` is affected by the EMI routing change.
