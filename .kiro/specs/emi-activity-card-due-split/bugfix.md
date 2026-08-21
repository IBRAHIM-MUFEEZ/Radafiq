# Bugfix Requirements Document

## Introduction

On the Dashboard "Account Activity" section, each credit card's `ActivityCard` shows two financial pills: **Current Due** and **EMI Outstanding**. The bug causes all EMI installments — whether they have already become payable or are still in the future pipeline — to be bucketed exclusively into EMI Outstanding. As a result, when an EMI installment's effective date is reached and it is pushed into the visible transaction list, the Current Due pill never updates to reflect the real-time obligation, and the EMI Outstanding pile grows without a corresponding Current Due entry. This gives the user a misleading picture of what is actually owed today versus what is scheduled for future months.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN an EMI installment's effective date has passed (the installment is visible, i.e., `isVisibleInTransactions(t) === true`) and the installment is unsettled THEN the system adds its due amount to `emiOutstandingByAccount` (EMI Outstanding) instead of `nonEmiDueByAccount` (Current Due)

1.2 WHEN a future EMI installment (not yet visible, `isVisibleInTransactions(t) === false`) is unsettled THEN the system also adds its due amount to `emiOutstandingByAccount`, meaning visible and future installments are indistinguishably merged

1.3 WHEN the user views the Dashboard Account Activity for an account that has EMI installments currently due THEN the system displays ₹0 (or unchanged) in the Current Due pill even though unsettled visible EMI amounts are outstanding

1.4 WHEN the user views the Dashboard Account Activity for an account that has EMI installments currently due THEN the system displays the combined total of both visible and future EMI due amounts in the EMI Outstanding pill, inflating it with amounts that belong in Current Due

### Expected Behavior (Correct)

2.1 WHEN an EMI installment's effective date has passed (the installment is visible, `isVisibleInTransactions(t) === true`) and the installment is unsettled THEN the system SHALL add its due amount to `nonEmiDueByAccount` (Current Due), not to `emiOutstandingByAccount`

2.2 WHEN a future EMI installment (not yet visible, `isVisibleInTransactions(t) === false`) is unsettled THEN the system SHALL add its due amount to `emiOutstandingByAccount` (EMI Outstanding), representing the future pipeline

2.3 WHEN the user views the Dashboard Account Activity for an account that has visible unsettled EMI installments THEN the system SHALL display the sum of those visible unsettled EMI amounts in the Current Due pill

2.4 WHEN the user views the Dashboard Account Activity for an account that has future (not yet visible) unsettled EMI installments THEN the system SHALL display only the sum of those future unsettled EMI amounts in the EMI Outstanding pill

2.5 WHEN an EMI installment transitions from not-visible to visible (its effective date is reached) THEN the system SHALL reflect that installment's due amount moving from EMI Outstanding into Current Due in the ActivityCard, so the combined total of the two pills is preserved

### Unchanged Behavior (Regression Prevention)

3.1 WHEN a non-EMI (regular) transaction is unsettled and not scheduled for a future month THEN the system SHALL CONTINUE TO include its due amount in `nonEmiDueByAccount` (Current Due)

3.2 WHEN a non-EMI transaction is scheduled for a future month THEN the system SHALL CONTINUE TO exclude it from both pills

3.3 WHEN a transaction belongs to a person account (`accountKind === 'person'`) THEN the system SHALL CONTINUE TO exclude it from both `emiOutstandingByAccount` and `nonEmiDueByAccount`

3.4 WHEN a transaction is fully settled (`isSettled === true`) THEN the system SHALL CONTINUE TO contribute zero to both pills

3.5 WHEN a transaction is partially paid THEN the system SHALL CONTINUE TO use `amount - partialPaidAmount` as the due amount in whichever pill it belongs to

3.6 WHEN the EMI Outstanding pill amount is zero THEN the system SHALL CONTINUE TO hide the pill from the ActivityCard UI

3.7 WHEN a bank account (`accountKind === 'bank_account'`) has transactions THEN the system SHALL CONTINUE TO show its net receivable amount unchanged in the ActivityCard

---

## Bug Condition Pseudocode

**Bug Condition Function** — identifies EMI transactions that are misrouted:

```pascal
FUNCTION isBugCondition(t: CustomerTransaction)
  INPUT: t of type CustomerTransaction
  OUTPUT: boolean

  RETURN t.emiGroupId ≠ '' 
    AND NOT t.isSettled 
    AND (t.amount - t.partialPaidAmount) > 0
    AND isVisibleInTransactions(t) = true
END FUNCTION
```

**Fix Checking Property:**

```pascal
// Property: Visible unsettled EMI installments appear in Current Due
FOR ALL t WHERE isBugCondition(t) DO
  result ← nonEmiDueByAccount.get(t.accountId)
  ASSERT result INCLUDES (t.amount - t.partialPaidAmount)
  ASSERT emiOutstandingByAccount does NOT include (t.amount - t.partialPaidAmount) for t
END FOR
```

**Preservation Property:**

```pascal
// Property: Future/non-visible EMI installments remain in EMI Outstanding
FOR ALL t WHERE NOT isBugCondition(t) DO
  ASSERT routing behavior F(t) = F'(t)
END FOR
```
