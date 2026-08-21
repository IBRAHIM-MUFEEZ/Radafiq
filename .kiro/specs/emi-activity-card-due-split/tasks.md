# Implementation Plan

- [x] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - Visible EMI Installments Misrouted to EMI Outstanding
  - **CRITICAL**: This test MUST FAIL on unfixed code — failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior — it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate the visible-EMI misrouting bug
  - **Scoped PBT Approach**: Scope the property to the concrete failing case — a single unsettled EMI installment whose `dueDate` is ≥ 21 days in the past so `isVisibleInTransactions` returns `true`
  - Construct a minimal `customers` array with one transaction where:
    - `emiGroupId` is non-empty (e.g. `"group-1"`)
    - `isSettled = false`
    - `amount = 5000`, `partialPaidAmount = 0` (due = ₹5 000)
    - `dueDate` set 30 days ago so `isVisibleInTransactions(t)` returns `true`
    - `accountKind = 'credit_card'`, `accountId = 'acc-1'`
  - Extract the `useMemo` callback from `web-app/src/pages/Dashboard.tsx` (lines ~198–220) into a pure helper function so it can be called in tests without a React render
  - Run the helper with the minimal `customers` array
  - Assert `nonEmiDueByAccount.get('acc-1') === 5000` (expected routing after fix)
  - Assert `emiOutstandingByAccount.get('acc-1') === undefined` (no leakage into EMI Outstanding)
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS because `nonEmiDueByAccount` is empty and `emiOutstandingByAccount` contains ₹5 000 (counterexample proving the misrouting)
  - Document the counterexample: `"visible EMI installment (dueDate -30d, ₹5000) ends up in emiOutstandingByAccount instead of nonEmiDueByAccount"`
  - Mark task complete when test is written, run, and the failure is documented
  - _Requirements: 1.1, 1.3, 2.1, 2.3_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Non-Buggy Routing Unchanged
  - **IMPORTANT**: Follow observation-first methodology — run the UNFIXED code against non-buggy inputs, observe the actual output, then encode it as a property
  - Observe on UNFIXED code for each case below and record actual map values before writing the assertions

  **Case A — Future EMI installment (isBugCondition = false because isVisibleInTransactions = false)**
  - `dueDate` set 60 days in the future; all other fields same as task 1 except `isSettled = false`
  - Observe: `emiOutstandingByAccount.get('acc-1') === 5000`, `nonEmiDueByAccount.get('acc-1') === undefined`
  - Write property: for any unsettled EMI installment where `isVisibleInTransactions(t) === false`, the amount goes to `emiOutstandingByAccount` unchanged

  **Case B — Non-EMI unsettled transaction in current month**
  - `emiGroupId = ''`, `isSettled = false`, `amount = 2000`, `partialPaidAmount = 0`, `transactionDate` in current month
  - Observe: `nonEmiDueByAccount.get('acc-2') === 2000`
  - Write property: non-EMI unsettled current-month transactions continue to land in `nonEmiDueByAccount`

  **Case C — Non-EMI transaction scheduled for a future month**
  - `emiGroupId = ''`, `isSettled = false`, `transactionDate` in a future calendar month
  - Observe: both maps empty for `acc-3`
  - Write property: future-month non-EMI transactions are excluded from both maps

  **Case D — Person-account transaction**
  - `accountKind = 'person'`, `isSettled = false`, `amount = 1000`
  - Observe: both maps empty
  - Write property: person-account transactions are always excluded

  **Case E — Fully settled transaction (any type)**
  - `isSettled = true`, `amount = 3000`
  - Observe: both maps empty (due = 0 guard hits)
  - Write property: settled transactions contribute zero to both maps

  **Case F — Partially paid EMI (future, isBugCondition = false)**
  - `emiGroupId = 'group-2'`, `isSettled = false`, `amount = 6000`, `partialPaidAmount = 1500`, future `dueDate`
  - Observe: `emiOutstandingByAccount.get('acc-4') === 4500` (amount − partialPaidAmount)
  - Write property: partial payment deduction is applied before routing for future EMIs

  - Verify all property tests PASS on UNFIXED code before proceeding to implementation
  - **EXPECTED OUTCOME**: All tests PASS (confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 2.2, 2.4, 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 3. Fix — split EMI branch on `isVisibleInTransactions` and hoist `today`

  - [x] 3.1 Implement the fix in `web-app/src/pages/Dashboard.tsx`
    - Hoist `const today = new Date()` to the top of the `useMemo` callback (before the `customers.forEach` loop), replacing the per-iteration `const today = new Date()` in the non-EMI branch
    - Inside the `if (t.emiGroupId)` branch, replace the single unconditional `emiMap.set(...)` call with:
      ```typescript
      if (isVisibleInTransactions(t, today)) {
        nonEmiMap.set(t.accountId, (nonEmiMap.get(t.accountId) ?? 0) + due);
      } else {
        emiMap.set(t.accountId, (emiMap.get(t.accountId) ?? 0) + due);
      }
      ```
    - Pass `today` to the existing `isScheduledForFutureMonth(t, today)` call in the non-EMI branch (it was already receiving a locally-scoped `today`; now it uses the hoisted one)
    - No import changes required — `isVisibleInTransactions` is already imported from `'../types/models'`
    - No other branches, maps, or downstream consumers are modified
    - _Bug_Condition: `t.emiGroupId ≠ '' AND NOT t.isSettled AND (t.amount - t.partialPaidAmount) > 0 AND isVisibleInTransactions(t) === true`_
    - _Expected_Behavior: visible unsettled EMI → `nonEmiMap`; future unsettled EMI → `emiMap`_
    - _Preservation: all non-EMI, person-account, settled, and future-EMI routing paths are byte-for-byte equivalent to the original_
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3, 3.4, 3.5_

  - [x] 3.2 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Visible EMI Installments Route to Current Due
    - **IMPORTANT**: Re-run the SAME test from task 1 — do NOT write a new test
    - The test from task 1 asserts `nonEmiDueByAccount.get('acc-1') === 5000` and `emiOutstandingByAccount.get('acc-1') === undefined`
    - Run bug condition exploration test from step 1 against the FIXED code
    - **EXPECTED OUTCOME**: Test PASSES (confirms the visible-EMI misrouting is resolved)
    - _Requirements: 2.1, 2.3_

  - [x] 3.3 Verify preservation tests still pass
    - **Property 2: Preservation** - Non-Buggy Routing Unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 2 — do NOT write new tests
    - Run all six preservation cases (A–F) from step 2 against the FIXED code
    - **EXPECTED OUTCOME**: All tests PASS (confirms no regressions in future EMI, non-EMI, person-account, settled, and partial-payment routing)
    - _Requirements: 2.2, 2.4, 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 4. Checkpoint — Ensure all tests pass
  - Re-run the full test suite (tasks 1, 2, 3.2, 3.3) in one go
  - Confirm Property 1 (Bug Condition) passes — visible EMI installments land in `nonEmiDueByAccount`
  - Confirm Property 2 (Preservation) passes — all non-buggy routing paths are unchanged
  - Confirm no TypeScript compilation errors (`tsc --noEmit` in `web-app/`)
  - If any test fails or a compile error appears, do not mark this task complete — diagnose and fix first
  - Ask the user if any questions or edge cases arise before closing out
