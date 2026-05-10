import React, { useState, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Plus, Trash2, Check, PiggyBank, ChevronDown, ChevronUp, Download } from 'lucide-react';
import { useApp } from '../context/AppContext';
import { formatMoney, formatDate, todayString, evaluateExpression } from '../utils/format';
import { CustomerTransaction, isVisibleInTransactions, isEmi, isSplit, isEmiOverdue, daysUntilDue, AccountKind, AccountOption, SplitEntry, ACCOUNT_KIND_LABELS, getAccountOptions, ALL_ACCOUNTS } from '../types/models';
import { generateAndDownloadStatement } from '../utils/statementGenerator';

type TxnFilter = 'ALL' | 'bank_account' | 'credit_card' | 'person';

function TransactionRow({
  txn,
  runningBalance,
  onSettle,
  onPartialPay,
  onDelete,
  onEdit,
}: {
  txn: CustomerTransaction;
  runningBalance: number;
  onSettle: (id: string, settled: boolean) => void;
  onPartialPay: (id: string) => void;
  onDelete: (id: string) => void;
  onEdit: (txn: CustomerTransaction) => void;
}) {
  const today = new Date();
  const overdue = isEmiOverdue(txn, today);
  const days = isEmi(txn) ? daysUntilDue(txn, today) : null;

  const statusColor = txn.isSettled
    ? 'var(--green)'
    : overdue
    ? 'var(--red)'
    : txn.partialPaidAmount > 0
    ? 'var(--orange)'
    : 'var(--red)';
  const remaining = Math.max(0, txn.amount - txn.partialPaidAmount);

  // Due date label for EMI rows
  const dueDateLabel = (() => {
    if (!isEmi(txn) || txn.isSettled || !txn.dueDate) return null;
    if (overdue) {
      const daysLate = days !== null ? Math.abs(days) : null;
      return { text: daysLate ? `Overdue by ${daysLate}d` : 'Overdue', color: 'var(--red)' };
    }
    if (days !== null && days <= 20) {
      return { text: `Due in ${days}d (${formatDate(txn.dueDate)})`, color: days <= 5 ? 'var(--warning)' : 'var(--text-muted)' };
    }
    return { text: `Due ${formatDate(txn.dueDate)}`, color: 'var(--text-muted)' };
  })();

  return (
    <div className="txn-row" style={{ borderLeft: overdue && !txn.isSettled ? '3px solid var(--red)' : undefined }}>
      <div className="txn-dot" style={{ background: statusColor }} />
      <div className="txn-info">
        <div className="txn-name" style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          {txn.name}
          {overdue && !txn.isSettled && (
            <span style={{
              fontSize: '0.6rem', fontWeight: 700, padding: '1px 5px',
              borderRadius: 4, background: 'var(--red)', color: '#fff',
              letterSpacing: '0.03em', flexShrink: 0,
            }}>OVERDUE</span>
          )}
        </div>
        <div className="txn-meta">
          {txn.accountName} • {formatDate(txn.transactionDate)}
          {txn.isSettled && ' • ✓ Settled'}
          {!txn.isSettled && txn.partialPaidAmount > 0 && ` • Partial ${formatMoney(txn.partialPaidAmount)}`}
          {isEmi(txn) && ` • EMI ${txn.emiIndex + 1}/${txn.emiTotal}`}
        </div>
        {dueDateLabel && (
          <div style={{ fontSize: '0.7rem', color: dueDateLabel.color, marginTop: 2, fontWeight: overdue ? 700 : 500 }}>
            {dueDateLabel.text}
          </div>
        )}
        <div className="txn-balance" style={{
          color: runningBalance > 0 ? 'var(--warning)' : 'var(--primary)',
        }}>
          Bal. {formatMoney(runningBalance)}
        </div>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}>
        <div className="txn-amount" style={{ color: statusColor }}>{formatMoney(txn.amount)}</div>
        {!txn.isSettled && txn.partialPaidAmount > 0 && (
          <div className="text-xs text-muted">Due: {formatMoney(remaining)}</div>
        )}
        <div style={{ display: 'flex', gap: 4 }}>
          <button
            className="btn btn-ghost btn-sm"
            style={{ padding: '2px 6px', fontSize: '0.75rem', color: txn.isSettled ? 'var(--green)' : 'var(--text-muted)' }}
            onClick={() => onSettle(txn.id, !txn.isSettled)}
            title={txn.isSettled ? 'Mark unsettled' : 'Mark settled'}
          >
            <Check size={12} />
          </button>
          {!txn.isSettled && (
            <button
              className="btn btn-ghost btn-sm"
              style={{ padding: '2px 6px', fontSize: '0.75rem' }}
              onClick={() => onPartialPay(txn.id)}
              title="Add partial payment"
            >
              ₹
            </button>
          )}
          <button
            className="btn btn-ghost btn-sm"
            style={{ padding: '2px 6px', fontSize: '0.75rem' }}
            onClick={() => onEdit(txn)}
            title="Edit"
          >
            ✏️
          </button>
          <button
            className="btn btn-ghost btn-sm"
            style={{ padding: '2px 6px', fontSize: '0.75rem', color: 'var(--red)' }}
            onClick={() => onDelete(txn.id)}
            title="Delete"
          >
            <Trash2 size={12} />
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Split Group Row ────────────────────────────────────────────────────────────
// Groups all split entries under one collapsible header with per-entry actions.
function SplitGroupRow({
  splits,
  expanded,
  onToggle,
  runningBalance,
  onSettle,
  onPartialPay,
  onDelete,
  onEdit,
}: {
  splits: CustomerTransaction[];
  expanded: boolean;
  onToggle: () => void;
  runningBalance: number;
  onSettle: (id: string, settled: boolean) => void;
  onPartialPay: (id: string) => void;
  onDelete: (id: string) => void;
  onEdit: (txn: CustomerTransaction) => void;
}) {
  const totalAmount = splits.reduce((s, t) => s + t.amount, 0);
  const totalDue = splits.reduce((s, t) => s + (t.isSettled ? 0 : Math.max(0, t.amount - t.partialPaidAmount)), 0);
  const allSettled = splits.every(t => t.isSettled);
  const anyPartial = splits.some(t => !t.isSettled && t.partialPaidAmount > 0);
  const groupStatusColor = allSettled ? 'var(--green)' : anyPartial ? 'var(--orange)' : 'var(--red)';
  const name = splits[0]?.name ?? 'Split Transaction';
  const date = splits[0]?.transactionDate ?? '';

  return (
    <div style={{ borderBottom: '1px solid var(--outline)' }}>
      {/* Group header — click to expand/collapse */}
      <div
        className="txn-row"
        style={{ borderBottom: 'none', cursor: 'pointer', paddingBottom: expanded ? '0.5rem' : '0.875rem' }}
        onClick={onToggle}
      >
        <div className="txn-dot" style={{ background: groupStatusColor }} />
        <div className="txn-info">
          <div className="txn-name" style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            {name}
            <span style={{
              fontSize: '0.6875rem', fontWeight: 600, padding: '1px 7px',
              borderRadius: 999, background: 'color-mix(in srgb, var(--primary) 12%, transparent)', color: 'var(--primary)',
            }}>
              Split · {splits.length}
            </span>
          </div>
          <div className="txn-meta">
            {formatDate(date)}
            {allSettled && ' • ✓ All Settled'}
            {!allSettled && totalDue > 0 && ` • Due ${formatMoney(totalDue)}`}
          </div>
          <div className="txn-balance" style={{
            color: runningBalance > 0 ? 'var(--warning)' : 'var(--primary)',
          }}>
            Bal. {formatMoney(runningBalance)}
          </div>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}>
          <div className="txn-amount" style={{ color: groupStatusColor }}>{formatMoney(totalAmount)}</div>
          <div style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
            {/* Mark All Paid shortcut on group header */}
            {!allSettled && (
              <button
                className="btn btn-ghost btn-sm"
                style={{
                  padding: '2px 8px', fontSize: '0.7rem',
                  color: 'var(--text-muted)',
                  border: '1px solid var(--outline)',
                  borderRadius: 6,
                }}
                onClick={e => {
                  e.stopPropagation();
                  splits.filter(s => !s.isSettled).forEach(s => onSettle(s.id, true));
                }}
                title="Mark all entries as paid"
              >
                Mark All Paid
              </button>
            )}
            <div style={{ color: 'var(--text-muted)' }}>
              {expanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
            </div>
          </div>
        </div>
      </div>

      {/* Expanded split entries */}
      {expanded && (
        <div style={{
          marginLeft: 22,
          marginBottom: '0.75rem',
          borderLeft: '2px solid var(--outline)',
          paddingLeft: '0.75rem',
          display: 'flex',
          flexDirection: 'column',
          gap: 0,
        }}>
          {splits.map(split => {
            const splitStatusColor = split.isSettled ? 'var(--green)' : split.partialPaidAmount > 0 ? 'var(--orange)' : 'var(--red)';
            const remaining = Math.max(0, split.amount - split.partialPaidAmount);
            const accentColor = split.accountKind === 'credit_card' ? 'var(--warning)' : split.accountKind === 'person' ? 'var(--primary)' : 'var(--secondary)';
            const kindLabel = split.accountKind === 'credit_card' ? 'Credit Card' : split.accountKind === 'person' ? 'Person' : 'Bank Account';
            return (
              <div key={split.id} style={{
                display: 'flex',
                alignItems: 'flex-start',
                gap: 10,
                padding: '0.625rem 0',
                borderBottom: '1px solid var(--outline)',
              }}>
                <div style={{ width: 8, height: 8, borderRadius: '50%', background: splitStatusColor, flexShrink: 0, marginTop: 6 }} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: '0.875rem', fontWeight: 600, color: 'var(--text)' }}>
                    {split.accountName}
                  </div>
                  <div style={{ fontSize: '0.75rem', color: accentColor, marginTop: 1 }}>{kindLabel}</div>
                  {split.isSettled && (
                    <div style={{ fontSize: '0.75rem', color: 'var(--green)', marginTop: 1 }}>✓ Settled</div>
                  )}
                  {!split.isSettled && split.partialPaidAmount > 0 && (
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: 1 }}>
                      Partial {formatMoney(split.partialPaidAmount)} • Due {formatMoney(remaining)}
                    </div>
                  )}
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}>
                  <div style={{ fontSize: '0.9375rem', fontWeight: 700, color: splitStatusColor }}>
                    {formatMoney(split.amount)}
                  </div>
                  <div style={{ display: 'flex', gap: 2, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                    {/* Mark as Paid / Unpaid button — explicit label */}
                    <button
                      className="btn btn-ghost btn-sm"
                      style={{
                        padding: '2px 8px', fontSize: '0.7rem',
                        color: split.isSettled ? 'var(--green)' : 'var(--text-muted)',
                        border: `1px solid ${split.isSettled ? 'var(--green)' : 'var(--outline)'}`,
                        borderRadius: 6,
                      }}
                      onClick={e => { e.stopPropagation(); onSettle(split.id, !split.isSettled); }}
                      title={split.isSettled ? 'Mark as unpaid' : 'Mark as paid'}
                    >
                      {split.isSettled ? '✓ Paid' : 'Mark Paid'}
                    </button>
                    {!split.isSettled && (
                      <button
                        className="btn btn-ghost btn-sm"
                        style={{ padding: '2px 6px', fontSize: '0.75rem' }}
                        onClick={e => { e.stopPropagation(); onPartialPay(split.id); }}
                        title="Add partial payment"
                      >
                        ₹
                      </button>
                    )}
                    <button
                      className="btn btn-ghost btn-sm"
                      style={{ padding: '2px 6px', fontSize: '0.75rem' }}
                      onClick={e => { e.stopPropagation(); onEdit(split); }}
                      title="Edit"
                    >
                      ✏️
                    </button>
                    <button
                      className="btn btn-ghost btn-sm"
                      style={{ padding: '2px 6px', fontSize: '0.75rem', color: 'var(--red)' }}
                      onClick={e => { e.stopPropagation(); onDelete(split.id); }}
                      title="Delete split entry"
                    >
                      <Trash2 size={12} />
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

interface AddTxnForm {
  transactionName: string;
  accountKind: AccountKind;
  accountId: string;
  accountName: string;
  personName: string;
  amount: string;
  transactionDate: string;
  mode: 'simple' | 'split' | 'emi';
  splits: SplitEntry[];
  emiMonths: string;
  emiFirstMonth: string;
}

const defaultForm = (): AddTxnForm => ({
  transactionName: '',
  accountKind: 'credit_card',
  accountId: '',
  accountName: '',
  personName: '',
  amount: '',
  transactionDate: todayString(),
  mode: 'simple',
  splits: [
    { accountKind: 'credit_card', accountId: '', accountName: '', personName: '', amount: '' },
    { accountKind: 'credit_card', accountId: '', accountName: '', personName: '', amount: '' },
  ],
  emiMonths: '',
  emiFirstMonth: '',
});

export default function CustomerDetail() {
  const { customerId } = useParams<{ customerId: string }>();
  const navigate = useNavigate();
  const {
    customers, settings, profile,
    addTransaction, addEmiTransactions, addSplitTransactions, convertEmiInstallmentToSplit,
    updateTransaction, deleteTransaction, addPartialPayment,
    toggleTransactionSettled, deleteCustomer, updateCustomerDueAmount,
    dataLoading,
  } = useApp();

  const customer = customers.find(c => c.id === customerId);
  const [filter, setFilter] = useState<TxnFilter>('ALL');
  const [showAddTxn, setShowAddTxn] = useState(false);
  const [editTxn, setEditTxn] = useState<CustomerTransaction | null>(null);
  const [form, setForm] = useState<AddTxnForm>(defaultForm);
  const [partialPayId, setPartialPayId] = useState<string | null>(null);
  const [partialAmount, setPartialAmount] = useState('');
  const [showDueEditor, setShowDueEditor] = useState(false);
  const [dueAmount, setDueAmount] = useState('');
  const [saving, setSaving] = useState(false);
  const [confirmDeleteCustomer, setConfirmDeleteCustomer] = useState(false);
  // Track which split groups are expanded — keyed by groupId so state
  // survives re-renders triggered by settle/partial-pay Firestore updates.
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());

  const toggleGroup = (groupId: string) => {
    setExpandedGroups(prev => {
      const next = new Set(prev);
      if (next.has(groupId)) next.delete(groupId);
      else next.add(groupId);
      return next;
    });
  };

  // Settle confirmation — holds the pending action until user confirms
  const [settleConfirm, setSettleConfirm] = useState<{
    id: string;
    name: string;
    markingAs: boolean; // true = marking paid, false = marking unpaid
    onConfirm: () => Promise<void>;
  } | null>(null);

  // EMI-to-split conversion state (used when editing an EMI installment)
  const [emiSplitMode, setEmiSplitMode] = useState(false);
  const [emiSplits, setEmiSplits] = useState<SplitEntry[]>([
    { accountKind: 'credit_card', accountId: '', accountName: '', personName: '', amount: '' },
    { accountKind: 'credit_card', accountId: '', accountName: '', personName: '', amount: '' },
  ]);

  const visibleTxns = useMemo(() => {
    if (!customer) return [];
    const today = new Date();
    return customer.transactions
      .filter(t => isVisibleInTransactions(t, today))
      .filter(t => filter === 'ALL' || t.accountKind === filter)
      .sort((a, b) =>
        b.transactionDate.localeCompare(a.transactionDate) || b.amount - a.amount
      );
  }, [customer, filter]);

  // Group split transactions together; keep singles as-is.
  // Also compute a running balance per item (descending date order = newest first).
  // Running balance starts at the total due across all visible+filtered transactions,
  // then decrements by each item's due amount — matching the mobile app behaviour.
  type TxnListItem =
    | { kind: 'single'; txn: CustomerTransaction; runningBalance: number }
    | { kind: 'split'; groupId: string; splits: CustomerTransaction[]; runningBalance: number };

  const txnListItems = useMemo((): TxnListItem[] => {
    // Step 1: compute total due across all visible filtered transactions
    const totalDue = visibleTxns.reduce((sum, t) => {
      return sum + (t.isSettled ? 0 : Math.max(0, t.amount - t.partialPaidAmount));
    }, 0);

    // Step 2: build groups (split or single) in sorted order
    type RawGroup =
      | { kind: 'single'; txn: CustomerTransaction }
      | { kind: 'split'; groupId: string; splits: CustomerTransaction[] };

    const splitMap = new Map<string, CustomerTransaction[]>();
    const rawGroups: RawGroup[] = [];

    visibleTxns.forEach(t => {
      if (t.splitGroupId) {
        const group = splitMap.get(t.splitGroupId);
        if (group) {
          group.push(t);
        } else {
          const newGroup = [t];
          splitMap.set(t.splitGroupId, newGroup);
          rawGroups.push({ kind: 'split', groupId: t.splitGroupId, splits: newGroup });
        }
      } else {
        rawGroups.push({ kind: 'single', txn: t });
      }
    });

    // Step 3: assign running balance — starts at totalDue, decrements by each group's due
    let runningBal = totalDue;
    return rawGroups.map(group => {
      const groupDue = group.kind === 'single'
        ? (group.txn.isSettled ? 0 : Math.max(0, group.txn.amount - group.txn.partialPaidAmount))
        : group.splits.reduce((s, t) => s + (t.isSettled ? 0 : Math.max(0, t.amount - t.partialPaidAmount)), 0);

      const balanceAtThisRow = runningBal;
      runningBal -= groupDue;

      return group.kind === 'single'
        ? { kind: 'single' as const, txn: group.txn, runningBalance: balanceAtThisRow }
        : { kind: 'split' as const, groupId: group.groupId, splits: group.splits, runningBalance: balanceAtThisRow };
    });
  }, [visibleTxns]);

  // Frequency of each account across ALL customers — used to show "Frequently Used" at the top
  const allAccountFreq = useMemo(() => {
    const freq = new Map<string, number>();
    for (const c of customers) {
      for (const t of c.transactions) {
        freq.set(t.accountId, (freq.get(t.accountId) || 0) + 1);
      }
    }
    return freq;
  }, [customers]);

  function groupedSelectOptions(options: AccountOption[], freq: Map<string, number>) {
    const frequent = options
      .filter(o => (freq.get(o.id) || 0) > 0)
      .sort((a, b) => (freq.get(b.id) || 0) - (freq.get(a.id) || 0));
    const other = options.filter(o => (freq.get(o.id) || 0) === 0);
    return (
      <>
        {frequent.length > 0 && (
          <optgroup label="Frequently Used">
            {frequent.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
          </optgroup>
        )}
        <optgroup label={frequent.length > 0 ? 'Other Accounts' : 'All Accounts'}>
          {other.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
        </optgroup>
      </>
    );
  }

  const accountOptions = useMemo(() =>
    getAccountOptions(form.accountKind, settings.selectedAccountIds),
    [form.accountKind, settings.selectedAccountIds]
  );

  if (!customer) {
    return (
      <div className="page-content">
        <button className="btn btn-ghost" onClick={() => navigate('/customers')}>
          <ArrowLeft size={18} /> Back
        </button>
        <div className="empty-state"><h3>Customer not found</h3></div>
      </div>
    );
  }

  const handleSaveTxn = async () => {
    if (!form.transactionName.trim()) return;
    setSaving(true);
    try {
      if (form.mode === 'split') {
        await addSplitTransactions({
          customerId: customer.id,
          customerName: customer.name,
          transactionName: form.transactionName,
          transactionDate: form.transactionDate,
          splits: form.splits,
        });
      } else if (form.mode === 'emi') {
        const total = evaluateExpression(form.amount) ?? parseFloat(form.amount);
        const months = parseInt(form.emiMonths);
        if (!isNaN(total) && !isNaN(months) && months > 0) {
          await addEmiTransactions({
            customerId: customer.id,
            transactionName: form.transactionName,
            customerName: customer.name,
            accountId: form.accountId || form.accountName,
            accountName: form.accountName,
            totalAmount: total,
            transactionDate: form.transactionDate,
            months,
            firstMonthOverride: form.emiFirstMonth ? parseFloat(form.emiFirstMonth) : undefined,
          });
        }
      } else {
        const amount = evaluateExpression(form.amount) ?? parseFloat(form.amount);
        // BUG-42 fix: validate account is selected for non-person accounts
        if (!isNaN(amount) && amount > 0) {
          if (form.accountKind !== 'person' && !form.accountId) {
            setSaving(false);
            return; // account not selected — do nothing (button should be disabled)
          }
          await addTransaction({
            customerId: customer.id,
            transactionName: form.transactionName,
            customerName: customer.name,
            accountId: form.accountKind === 'person'
              ? `person_${form.personName.trim().toLowerCase().replace(/\s+/g, '_')}`
              : form.accountId,
            accountName: form.accountKind === 'person' ? form.personName : form.accountName,
            accountKind: form.accountKind,
            amount: String(amount),
            transactionDate: form.transactionDate,
            personName: form.accountKind === 'person' ? form.personName : undefined,
          });
        }
      }
      setShowAddTxn(false);
      setForm(defaultForm());
    } finally {
      setSaving(false);
    }
  };

  const handleUpdateTxn = async () => {
    if (!editTxn || !form.transactionName.trim()) return;
    setSaving(true);
    try {
      // EMI installment being converted to split across multiple accounts
      if (editTxn.emiGroupId && emiSplitMode) {
        const validSplits = emiSplits.filter(s => {
          const amt = parseFloat(s.amount);
          return !isNaN(amt) && amt > 0 && (s.accountKind === 'person' ? s.personName.trim() : s.accountId);
        });
        if (validSplits.length >= 1) {
          await convertEmiInstallmentToSplit({
            originalTransactionId: editTxn.id,
            customerId: customer.id,
            customerName: customer.name,
            transactionName: form.transactionName,
            transactionDate: form.transactionDate,
            emiGroupId: editTxn.emiGroupId,
            emiIndex: editTxn.emiIndex,
            emiTotal: editTxn.emiTotal,
            splits: validSplits,
          });
        }
      } else {
        // Normal update
        const amount = evaluateExpression(form.amount) ?? parseFloat(form.amount);
        if (isNaN(amount) || amount <= 0) {
          setSaving(false);
          return; // invalid amount — do nothing
        }
        // Validate account is selected for non-person accounts (same guard as handleSaveTxn)
        if (form.accountKind !== 'person' && !form.accountId) {
          setSaving(false);
          return; // account not selected — do nothing
        }
        await updateTransaction({
          transactionId: editTxn.id,
          transactionName: form.transactionName,
          accountId: form.accountKind === 'person'
            ? `person_${form.personName.trim().toLowerCase().replace(/\s+/g, '_')}`
            : form.accountId,
          accountName: form.accountKind === 'person' ? form.personName : form.accountName,
          accountKind: form.accountKind,
          amount: String(amount),
          transactionDate: form.transactionDate,
          personName: form.accountKind === 'person' ? form.personName : undefined,
        });
      }
      setEditTxn(null);
      setEmiSplitMode(false);
      setForm(defaultForm());
    } finally {
      setSaving(false);
    }
  };

  const openEdit = (txn: CustomerTransaction) => {
    setEditTxn(txn);
    setEmiSplitMode(false);
    setEmiSplits([
      { accountKind: txn.accountKind, accountId: txn.accountId, accountName: txn.accountName, personName: txn.personName, amount: String(txn.amount) },
      { accountKind: 'credit_card', accountId: '', accountName: '', personName: '', amount: '' },
    ]);
    setForm({
      ...defaultForm(),
      transactionName: txn.name,
      accountKind: txn.accountKind,
      accountId: txn.accountId,
      accountName: txn.accountName,
      personName: txn.personName,
      amount: String(txn.amount),
      transactionDate: txn.transactionDate,
      mode: 'simple',
    });
  };

  // Account breakdown — group visible transactions by account (include person)
  const accountBreakdowns = useMemo(() => {
    const today = new Date();
    const map = new Map<string, {
      accountId: string;
      accountName: string;
      accountKind: AccountKind;
      totalUsed: number;
      totalDue: number;
    }>();
    customer.transactions
      .filter(t => isVisibleInTransactions(t, today))
      .forEach(t => {
        const key = `${t.accountKind}::${t.accountId}`;
        const due = t.isSettled ? 0 : Math.max(0, t.amount - t.partialPaidAmount);
        const existing = map.get(key);
        if (!existing) {
          map.set(key, { accountId: t.accountId, accountName: t.accountName, accountKind: t.accountKind, totalUsed: t.amount, totalDue: due });
        } else {
          map.set(key, { ...existing, totalUsed: existing.totalUsed + t.amount, totalDue: existing.totalDue + due });
        }
      });
    return Array.from(map.values()).sort((a, b) => b.totalDue - a.totalDue || b.totalUsed - a.totalUsed);
  }, [customer.transactions]);

  // ── Account-level payment state ─────────────────────────────────────────────
  const [accountPayTarget, setAccountPayTarget] = useState<{
    accountId: string;
    accountName: string;
    totalDue: number;
  } | null>(null);
  const [accountPayAmount, setAccountPayAmount] = useState('');
  // Per-account saving state — keyed by accountId so only the tapped row shows spinner
  const [accountPaySavingId, setAccountPaySavingId] = useState<string | null>(null);

  /**
   * Distribute paymentAmount across all unsettled transactions for accountId,
   * sorted oldest-first. Greedily settles transactions that are fully covered
   * and applies a partial payment to the first partially-covered transaction.
   *
   * Uses Math.round to avoid floating-point residuals (e.g. 0.000000001)
   * that would cause the loop to continue past zero.
   */
  const applyAccountPayment = async (accountId: string, paymentAmount: number) => {
    const today = new Date();
    const unsettled = customer.transactions
      .filter(t =>
        t.accountId === accountId &&
        !t.isSettled &&
        isVisibleInTransactions(t, today)
      )
      .sort((a, b) => a.transactionDate.localeCompare(b.transactionDate));

    // Round to paise (2 decimal places) to eliminate floating-point residuals
    let remaining = Math.round(paymentAmount * 100) / 100;

    for (const txn of unsettled) {
      if (remaining <= 0) break;
      const due = Math.round(Math.max(0, txn.amount - txn.partialPaidAmount) * 100) / 100;
      if (due <= 0) continue;
      if (remaining >= due) {
        // Fully covers this transaction — settle it
        await toggleTransactionSettled(txn.id, true);
        remaining = Math.round((remaining - due) * 100) / 100;
      } else {
        // Partially covers — add partial payment and stop
        await addPartialPayment(txn.id, String(remaining));
        remaining = 0;
      }
    }
  };

  return (
    <div className="page-content">
      {/* Back */}
      <button className="btn btn-ghost" style={{ marginBottom: '1rem' }} onClick={() => navigate('/customers')}>
        <ArrowLeft size={18} /> Customers
      </button>

      {/* Customer header */}
      <div className="flow-card" style={{ marginBottom: '1rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: '1rem' }}>
          <div className="avatar" style={{ width: 52, height: 52, fontSize: '1.125rem' }}>
            {customer.name.slice(0, 2).toUpperCase()}
          </div>
          <div style={{ flex: 1 }}>
            <h2 style={{ fontSize: '1.375rem' }}>{customer.name}</h2>
            <p className="text-muted text-sm">{customer.transactions.length} transaction(s)</p>
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <button
              className="btn btn-ghost btn-sm"
              onClick={() => {
                const userName = profile?.displayName?.trim() || profile?.email?.trim() || 'Radafiq User';
                generateAndDownloadStatement(customer, userName, false);
              }}
              title="Download Statement PDF"
              style={{ color: 'var(--primary)' }}
            >
              <Download size={16} />
            </button>
            <button
              className="btn btn-ghost btn-sm"
              onClick={() => navigate(`/customers/${customer.id}/savings`)}
              title="Savings"
            >
              <PiggyBank size={16} />
            </button>
            <button
              className="btn btn-ghost btn-sm"
              style={{ color: 'var(--red)' }}
              onClick={() => setConfirmDeleteCustomer(true)}
              title="Delete customer"
            >
              <Trash2 size={16} />
            </button>
          </div>
        </div>

        <div className="two-col" style={{ marginBottom: '0.75rem' }}>
          <div className="metric-pill">
            <span className="label">Total Used</span>
            <span className="value text-primary">{formatMoney(customer.totalAmount)}</span>
          </div>
          <div className="metric-pill">
            <span className="label">Customer Paid</span>
            <span className="value" style={{ color: 'var(--secondary)' }}>{formatMoney(customer.creditDueAmount)}</span>
          </div>
        </div>

        <div className="accent-row">
          <span className="accent-label">Balance Remaining</span>
          <span className="accent-value" style={{ color: customer.balance > 0 ? 'var(--warning)' : 'var(--primary)' }}>
            {formatMoney(customer.balance)}
          </span>
        </div>

        <p className="text-muted text-xs" style={{ marginTop: 8 }}>
          Manual paid {formatMoney(customer.manualPaidAmount)} • Settled {formatMoney(customer.settledTransactionAmount)} • Partial {formatMoney(customer.partialPaidAmount)}
        </p>

        <div style={{ display: 'flex', gap: 8, marginTop: '1rem' }}>
          <button className="btn btn-sm btn-outline" onClick={() => { setDueAmount(String(customer.creditDueAmount)); setShowDueEditor(true); }}>
            Adjust Paid
          </button>
        </div>
      </div>

      {/* Account Breakdown */}
      <div className="flow-card" style={{ marginBottom: '1rem', padding: '0.875rem' }}>
        <p style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '0.625rem' }}>
          Account Breakdown
        </p>
        {dataLoading ? (
          <p style={{ fontSize: '0.8125rem', color: 'var(--text-muted)' }}>Loading...</p>
        ) : accountBreakdowns.length === 0 ? (
          <p style={{ fontSize: '0.8125rem', color: 'var(--text-muted)' }}>No transactions yet.</p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {accountBreakdowns.map((b, i) => {
              const accent = b.accountKind === 'credit_card' ? 'var(--warning)' : b.accountKind === 'person' ? 'var(--primary)' : 'var(--secondary)';
              const kindLabel = b.accountKind === 'credit_card' ? 'Credit Card' : b.accountKind === 'person' ? 'Person' : 'Bank Account';
              return (
                <div key={i} style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 8,
                  background: `color-mix(in srgb, ${accent} 8%, transparent)`,
                  borderRadius: 10,
                  padding: '8px 12px',
                  border: `1px solid color-mix(in srgb, ${accent} 20%, transparent)`,
                }}>
                  {/* Account info row */}
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, flex: 1, minWidth: 0 }}>
                      <div style={{ width: 8, height: 8, borderRadius: '50%', background: accent, flexShrink: 0 }} />
                      <div style={{ minWidth: 0 }}>
                        <div style={{ fontSize: '0.8125rem', fontWeight: 600, color: 'var(--text)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {b.accountName}
                        </div>
                        <div style={{ fontSize: '0.6875rem', color: accent }}>{kindLabel}</div>
                      </div>
                    </div>
                    <div style={{ textAlign: 'right', flexShrink: 0 }}>
                      <div style={{ fontSize: '0.8125rem', fontWeight: 700, color: accent }}>
                        {formatMoney(b.totalUsed)}
                      </div>
                      <div style={{ fontSize: '0.6875rem', color: b.totalDue > 0 ? 'var(--warning)' : 'var(--primary)' }}>
                        {b.totalDue > 0 ? `Due ${formatMoney(b.totalDue)}` : '✓ Settled'}
                      </div>
                    </div>
                  </div>
                  {/* Pay buttons — only when there is an outstanding due */}
                  {b.totalDue > 0 && (
                    <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                      <button
                        className="btn btn-outline btn-sm"
                        style={{
                          fontSize: '0.75rem', padding: '4px 12px',
                          borderColor: `color-mix(in srgb, ${accent} 50%, transparent)`,
                          color: accent,
                        }}
                        onClick={() => {
                          setAccountPayTarget({ accountId: b.accountId, accountName: b.accountName, totalDue: b.totalDue });
                          setAccountPayAmount('');
                        }}
                        disabled={accountPaySavingId === b.accountId}
                      >
                        Partial Pay
                      </button>
                      <button
                        className="btn btn-sm"
                        style={{
                          fontSize: '0.75rem', padding: '4px 12px',
                          background: accent, color: '#fff', border: 'none',
                        }}
                        onClick={async () => {
                          setAccountPaySavingId(b.accountId);
                          try {
                            await applyAccountPayment(b.accountId, b.totalDue);
                          } finally {
                            setAccountPaySavingId(null);
                          }
                        }}
                        disabled={accountPaySavingId === b.accountId}
                      >
                        {accountPaySavingId === b.accountId ? 'Saving…' : 'Full Payment'}
                      </button>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Transactions */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.75rem' }}>
        <h3>Transactions</h3>
        <button className="btn btn-primary btn-sm" onClick={() => { setShowAddTxn(true); setForm(defaultForm()); }}>
          <Plus size={14} /> Add
        </button>
      </div>

      {/* Filter */}
      <div style={{ display: 'flex', gap: 8, marginBottom: '1rem', flexWrap: 'wrap' }}>
        {(['ALL', 'bank_account', 'credit_card', 'person'] as TxnFilter[]).map(f => (
          <button
            key={f}
            className={`btn btn-sm ${filter === f ? 'btn-primary' : 'btn-outline'}`}
            onClick={() => setFilter(f)}
          >
            {f === 'ALL' ? 'All' : ACCOUNT_KIND_LABELS[f as AccountKind]}
          </button>
        ))}
      </div>

      {visibleTxns.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state-icon">📋</div>
          <h3>No transactions</h3>
          <p>Add a transaction to start tracking this customer's ledger.</p>
        </div>
      ) : (
        <div className="flow-card">
          {txnListItems.map(item => {
            if (item.kind === 'split') {
              return (
                <SplitGroupRow
                  key={item.groupId}
                  splits={item.splits}
                  expanded={expandedGroups.has(item.groupId)}
                  onToggle={() => toggleGroup(item.groupId)}
                  runningBalance={item.runningBalance}
                  onSettle={(id, settled) => {
                    const split = item.splits.find(s => s.id === id);
                    setSettleConfirm({
                      id,
                      name: split?.accountName ?? 'this entry',
                      markingAs: settled,
                      onConfirm: async () => { await toggleTransactionSettled(id, settled); },
                    });
                  }}
                  onPartialPay={(id) => { setPartialPayId(id); setPartialAmount(''); }}
                  onDelete={async (id) => { await deleteTransaction(id); }}
                  onEdit={openEdit}
                />
              );
            }
            return (
              <TransactionRow
                key={item.txn.id}
                txn={item.txn}
                runningBalance={item.runningBalance}
                onSettle={(id, settled) => {
                  setSettleConfirm({
                    id,
                    name: item.txn.name,
                    markingAs: settled,
                    onConfirm: async () => { await toggleTransactionSettled(id, settled); },
                  });
                }}
                onPartialPay={(id) => { setPartialPayId(id); setPartialAmount(''); }}
                onDelete={async (id) => { await deleteTransaction(id); }}
                onEdit={openEdit}
              />
            );
          })}
        </div>
      )}

      {/* Add/Edit Transaction Modal */}
      {(showAddTxn || editTxn) && (
        <div className="modal-overlay" onClick={() => { setShowAddTxn(false); setEditTxn(null); }}>
          <div className="modal" style={{ maxWidth: 560 }} onClick={e => e.stopPropagation()}>
            <h3 className="modal-title">{editTxn ? 'Edit Transaction' : 'Add Transaction'}</h3>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.875rem' }}>
              {!editTxn && (
                <div style={{ display: 'flex', gap: 8 }}>
                  {(['simple', 'split', 'emi'] as const).map(m => (
                    <button
                      key={m}
                      className={`btn btn-sm ${form.mode === m ? 'btn-primary' : 'btn-outline'}`}
                      onClick={() => setForm(f => ({ ...f, mode: m }))}
                    >
                      {m === 'simple' ? 'Simple' : m === 'split' ? 'Split' : 'EMI'}
                    </button>
                  ))}
                </div>
              )}

              {/* EMI installment — show split toggle */}
              {editTxn?.emiGroupId && (
                <div style={{
                  background: 'color-mix(in srgb, var(--primary) 8%, transparent)',
                  border: '1px solid color-mix(in srgb, var(--primary) 20%, transparent)',
                  borderRadius: 12, padding: '0.75rem',
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
                    <div>
                      <div style={{ fontWeight: 600, fontSize: '0.875rem' }}>
                        EMI {editTxn.emiIndex + 1}/{editTxn.emiTotal}
                      </div>
                      <div className="text-muted text-xs" style={{ marginTop: 2 }}>
                        Split this installment across multiple accounts or persons
                      </div>
                    </div>
                    <label className="switch" style={{ flexShrink: 0 }}>
                      <input
                        type="checkbox"
                        checked={emiSplitMode}
                        onChange={e => setEmiSplitMode(e.target.checked)}
                      />
                      <span className="switch-track" />
                    </label>
                  </div>
                </div>
              )}

              <div className="form-group">
                <label className="form-label">Transaction Name</label>
                <input className="form-input" value={form.transactionName} onChange={e => setForm(f => ({ ...f, transactionName: e.target.value }))} placeholder="e.g. Grocery, Rent" />
              </div>

              {/* EMI split form */}
              {editTxn?.emiGroupId && emiSplitMode ? (
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                    <label className="form-label" style={{ margin: 0 }}>Split Entries</label>
                    <span className="text-muted text-xs">
                      Total: {formatMoney(emiSplits.reduce((s, e) => s + (parseFloat(e.amount) || 0), 0))}
                      {' / '}
                      {formatMoney(editTxn.amount)}
                    </span>
                  </div>
                  {emiSplits.map((split, i) => (
                    <div key={i} style={{ background: 'var(--bg-soft)', borderRadius: 14, padding: '0.75rem', marginBottom: 8 }}>
                      <div style={{ display: 'flex', gap: 8, marginBottom: 8, alignItems: 'center' }}>
                        <select
                          className="form-select"
                          style={{ flex: 1 }}
                          value={split.accountKind}
                          onChange={e => {
                            const s = [...emiSplits];
                            s[i] = { ...s[i], accountKind: e.target.value as AccountKind, accountId: '', accountName: '' };
                            setEmiSplits(s);
                          }}
                        >
                          <option value="credit_card">Credit Card</option>
                          <option value="bank_account">Bank Account</option>
                          <option value="person">Person</option>
                        </select>
                        <input
                          className="form-input"
                          style={{ width: 110 }}
                          value={split.amount}
                          onChange={e => {
                            const s = [...emiSplits];
                            s[i] = { ...s[i], amount: e.target.value };
                            setEmiSplits(s);
                          }}
                          placeholder="Amount"
                        />
                        {emiSplits.length > 1 && (
                          <button
                            className="btn btn-ghost btn-sm"
                            style={{ color: 'var(--red)', padding: '4px 8px' }}
                            onClick={() => setEmiSplits(s => s.filter((_, j) => j !== i))}
                          >✕</button>
                        )}
                      </div>
                      {split.accountKind === 'person' ? (
                        <input
                          className="form-input"
                          value={split.personName}
                          onChange={e => {
                            const s = [...emiSplits];
                            s[i] = { ...s[i], personName: e.target.value };
                            setEmiSplits(s);
                          }}
                          placeholder="Person name"
                        />
                      ) : (
                        <select
                          className="form-select"
                          value={split.accountId}
                          onChange={e => {
                            const opt = getAccountOptions(split.accountKind, settings.selectedAccountIds).find(a => a.id === e.target.value);
                            const s = [...emiSplits];
                            s[i] = { ...s[i], accountId: e.target.value, accountName: opt?.name ?? '' };
                            setEmiSplits(s);
                          }}
                        >
                          <option value="">Select account</option>
                          {groupedSelectOptions(getAccountOptions(split.accountKind, settings.selectedAccountIds), allAccountFreq)}
                        </select>
                      )}
                    </div>
                  ))}
                  <button
                    className="btn btn-outline btn-sm"
                    onClick={() => setEmiSplits(s => [...s, { accountKind: 'credit_card', accountId: '', accountName: '', personName: '', amount: '' }])}
                  >
                    + Add Split
                  </button>
                </div>
              ) : (
                /* Normal simple edit fields */
                !editTxn?.emiGroupId || !emiSplitMode ? (
                  <>
                    {form.mode !== 'split' && (
                      <>
                        <div className="form-group">
                          <label className="form-label">Account Type</label>
                          <select className="form-select" value={form.accountKind} onChange={e => setForm(f => ({ ...f, accountKind: e.target.value as AccountKind, accountId: '', accountName: '' }))}>
                            <option value="credit_card">Credit Card</option>
                            <option value="bank_account">Bank Account</option>
                            <option value="person">Person</option>
                          </select>
                        </div>

                        {form.accountKind === 'person' ? (
                          <div className="form-group">
                            <label className="form-label">Person Name</label>
                            <input className="form-input" value={form.personName} onChange={e => setForm(f => ({ ...f, personName: e.target.value }))} placeholder="Enter person name" />
                          </div>
                        ) : (
                          <div className="form-group">
                            <label className="form-label">{form.accountKind === 'credit_card' ? 'Credit Card' : 'Bank Account'}</label>
                            <select
                              className="form-select"
                              value={form.accountId}
                              onChange={e => {
                                const opt = accountOptions.find(a => a.id === e.target.value);
                                setForm(f => ({ ...f, accountId: e.target.value, accountName: opt?.name ?? '' }));
                              }}
                            >
                              <option value="">Select account</option>
                              {groupedSelectOptions(accountOptions, allAccountFreq)}
                            </select>
                          </div>
                        )}

                        <div className="form-group">
                          <label className="form-label">Amount (supports expressions like 100+50)</label>
                          <input className="form-input" value={form.amount} onChange={e => setForm(f => ({ ...f, amount: e.target.value }))} placeholder="0.00" />
                        </div>

                        {form.mode === 'emi' && (
                          <>
                            <div className="form-group">
                              <label className="form-label">Number of EMI Months</label>
                              <input className="form-input" type="number" min="1" value={form.emiMonths} onChange={e => setForm(f => ({ ...f, emiMonths: e.target.value }))} placeholder="e.g. 12" />
                            </div>
                            <div className="form-group">
                              <label className="form-label">First Month Override (optional)</label>
                              <input className="form-input" value={form.emiFirstMonth} onChange={e => setForm(f => ({ ...f, emiFirstMonth: e.target.value }))} placeholder="Leave blank for equal EMIs" />
                            </div>
                          </>
                        )}
                      </>
                    )}

                    {form.mode === 'split' && (
                      <div>
                        <label className="form-label" style={{ marginBottom: 8, display: 'block' }}>Split Entries</label>
                        {form.splits.map((split, i) => (
                          <div key={i} style={{ background: 'var(--bg-soft)', borderRadius: 14, padding: '0.75rem', marginBottom: 8 }}>
                            <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
                              <select
                                className="form-select"
                                style={{ flex: 1 }}
                                value={split.accountKind}
                                onChange={e => {
                                  const splits = [...form.splits];
                                  splits[i] = { ...splits[i], accountKind: e.target.value as AccountKind, accountId: '', accountName: '' };
                                  setForm(f => ({ ...f, splits }));
                                }}
                              >
                                <option value="credit_card">Credit Card</option>
                                <option value="bank_account">Bank Account</option>
                                <option value="person">Person</option>
                              </select>
                              <input
                                className="form-input"
                                style={{ width: 100 }}
                                value={split.amount}
                                onChange={e => {
                                  const splits = [...form.splits];
                                  splits[i] = { ...splits[i], amount: e.target.value };
                                  setForm(f => ({ ...f, splits }));
                                }}
                                placeholder="Amount"
                              />
                            </div>
                            {split.accountKind === 'person' ? (
                              <input
                                className="form-input"
                                value={split.personName}
                                onChange={e => {
                                  const splits = [...form.splits];
                                  splits[i] = { ...splits[i], personName: e.target.value };
                                  setForm(f => ({ ...f, splits }));
                                }}
                                placeholder="Person name"
                              />
                            ) : (
                              <select
                                className="form-select"
                                value={split.accountId}
                                onChange={e => {
                                  const opt = getAccountOptions(split.accountKind, settings.selectedAccountIds).find(a => a.id === e.target.value);
                                  const splits = [...form.splits];
                                  splits[i] = { ...splits[i], accountId: e.target.value, accountName: opt?.name ?? '' };
                                  setForm(f => ({ ...f, splits }));
                                }}
                              >
                                <option value="">Select account</option>
                                {groupedSelectOptions(getAccountOptions(split.accountKind, settings.selectedAccountIds), allAccountFreq)}
                              </select>
                            )}
                          </div>
                        ))}
                        <button
                          className="btn btn-outline btn-sm"
                          onClick={() => setForm(f => ({ ...f, splits: [...f.splits, { accountKind: 'credit_card', accountId: '', accountName: '', personName: '', amount: '' }] }))}
                        >
                          + Add Split
                        </button>
                      </div>
                    )}
                  </>
                ) : null
              )}

              <div className="form-group">
                <label className="form-label">Date</label>
                <input className="form-input" type="date" value={form.transactionDate} onChange={e => setForm(f => ({ ...f, transactionDate: e.target.value }))} />
              </div>
            </div>

            <div className="modal-actions">
              <button className="btn btn-outline" onClick={() => { setShowAddTxn(false); setEditTxn(null); setEmiSplitMode(false); }}>Cancel</button>
              <button
                className="btn btn-primary"
                onClick={editTxn ? handleUpdateTxn : handleSaveTxn}
                disabled={
                  saving ||
                  !form.transactionName.trim() ||
                  // For edit: block if account not selected (non-person, non-split-mode)
                  (!!editTxn && !emiSplitMode && form.accountKind !== 'person' && !form.accountId)
                }
              >
                {saving ? 'Saving...' : editTxn ? (editTxn.emiGroupId && emiSplitMode ? 'Split Installment' : 'Update') : 'Add Transaction'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Settle confirmation modal */}
      {settleConfirm && (
        <div className="modal-overlay" onClick={() => setSettleConfirm(null)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h3 className="modal-title">
              {settleConfirm.markingAs ? 'Mark as Paid?' : 'Mark as Unpaid?'}
            </h3>
            <p className="modal-subtitle">
              {settleConfirm.markingAs
                ? <>Are you sure you want to mark <strong>{settleConfirm.name}</strong> as fully paid and settled?</>
                : <>Are you sure you want to mark <strong>{settleConfirm.name}</strong> as unpaid? This will restore it to outstanding.</>
              }
            </p>
            <div className="modal-actions">
              <button className="btn btn-outline" onClick={() => setSettleConfirm(null)}>
                Cancel
              </button>
              <button
                className={`btn ${settleConfirm.markingAs ? 'btn-primary' : 'btn-danger'}`}
                onClick={async () => {
                  const action = settleConfirm;
                  setSettleConfirm(null);
                  await action.onConfirm();
                }}
              >
                {settleConfirm.markingAs ? 'Yes, Mark Paid' : 'Yes, Mark Unpaid'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Partial payment modal */}
      {partialPayId && (
        <div className="modal-overlay" onClick={() => setPartialPayId(null)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h3 className="modal-title">Add Partial Payment</h3>
            <div className="form-group" style={{ marginBottom: '1rem' }}>
              <label className="form-label">Amount</label>
              <input
                className="form-input"
                type="number"
                value={partialAmount}
                onChange={e => setPartialAmount(e.target.value)}
                placeholder="0.00"
                autoFocus
              />
            </div>
            <div className="modal-actions">
              <button className="btn btn-outline" onClick={() => setPartialPayId(null)}>Cancel</button>
              <button
                className="btn btn-primary"
                onClick={async () => {
                  await addPartialPayment(partialPayId, partialAmount);
                  setPartialPayId(null);
                }}
                disabled={!partialAmount || parseFloat(partialAmount) <= 0}
              >
                Add Payment
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Adjust paid modal */}
      {showDueEditor && (
        <div className="modal-overlay" onClick={() => setShowDueEditor(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h3 className="modal-title">Adjust Customer Paid Amount</h3>
            <p className="modal-subtitle">Set the total amount this customer has paid manually.</p>
            <div className="form-group" style={{ marginBottom: '1rem' }}>
              <label className="form-label">Paid Amount</label>
              <input
                className="form-input"
                type="number"
                value={dueAmount}
                onChange={e => setDueAmount(e.target.value)}
                placeholder="0.00"
                autoFocus
              />
            </div>
            <div className="modal-actions">
              <button className="btn btn-outline" onClick={() => setShowDueEditor(false)}>Cancel</button>
              <button
                className="btn btn-primary"
                onClick={async () => {
                  await updateCustomerDueAmount(customer.id, customer.name, dueAmount);
                  setShowDueEditor(false);
                }}
              >
                Save
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Account-level payment dialog */}
      {accountPayTarget && (
        <div className="modal-overlay" onClick={() => { if (!accountPaySavingId) setAccountPayTarget(null); }}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h3 className="modal-title">Pay — {accountPayTarget.accountName}</h3>
            <p className="modal-subtitle">
              Outstanding: <strong>{formatMoney(accountPayTarget.totalDue)}</strong>
            </p>
            <p className="modal-subtitle" style={{ marginTop: 4 }}>
              The payment is applied to the oldest unsettled transactions first,
              settling each fully before moving to the next.
            </p>
            <div className="form-group" style={{ margin: '1rem 0' }}>
              <label className="form-label">Amount</label>
              <input
                className="form-input"
                type="number"
                value={accountPayAmount}
                onChange={e => setAccountPayAmount(e.target.value)}
                placeholder="0.00"
                autoFocus
                disabled={!!accountPaySavingId}
                style={{
                  borderColor: (() => {
                    const v = parseFloat(accountPayAmount);
                    return !isNaN(v) && v > accountPayTarget.totalDue ? 'var(--red)' : undefined;
                  })(),
                }}
              />
              {(() => {
                const v = parseFloat(accountPayAmount);
                if (!isNaN(v) && v > accountPayTarget.totalDue) {
                  return (
                    <p style={{ fontSize: '0.75rem', color: 'var(--red)', marginTop: 4 }}>
                      Amount cannot exceed outstanding {formatMoney(accountPayTarget.totalDue)}
                    </p>
                  );
                }
                return null;
              })()}
            </div>
            <div className="modal-actions">
              <button
                className="btn btn-outline"
                onClick={() => setAccountPayTarget(null)}
                disabled={!!accountPaySavingId}
              >
                Cancel
              </button>
              <button
                className="btn btn-primary"
                disabled={
                  !!accountPaySavingId ||
                  !accountPayAmount ||
                  parseFloat(accountPayAmount) <= 0 ||
                  parseFloat(accountPayAmount) > accountPayTarget.totalDue
                }
                onClick={async () => {
                  const amount = parseFloat(accountPayAmount);
                  if (isNaN(amount) || amount <= 0 || amount > accountPayTarget.totalDue) return;
                  const targetId = accountPayTarget.accountId;
                  setAccountPaySavingId(targetId);
                  try {
                    await applyAccountPayment(targetId, amount);
                    setAccountPayTarget(null);
                  } finally {
                    setAccountPaySavingId(null);
                  }
                }}
              >
                {accountPaySavingId ? 'Saving…' : 'Apply Payment'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Confirm delete customer */}
      {confirmDeleteCustomer && (
        <div className="modal-overlay" onClick={() => setConfirmDeleteCustomer(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h3 className="modal-title">Delete Customer?</h3>
            <p className="modal-subtitle">
              <strong>{customer.name}</strong> will be moved to the recycle bin. You can restore them later.
            </p>
            <div className="modal-actions">
              <button className="btn btn-outline" onClick={() => setConfirmDeleteCustomer(false)}>Cancel</button>
              <button
                className="btn btn-danger"
                onClick={() => {
                  // Navigate first — before the Firestore listener fires and removes
                  // the customer from state, which would cause this component to
                  // re-render with customer=undefined and show a blank screen.
                  const id = customer.id;
                  const name = customer.name;
                  navigate('/customers');
                  deleteCustomer(id, name);
                }}
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
