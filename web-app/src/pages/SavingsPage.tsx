import React, { useState, useMemo, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Plus, Minus, Trash2 } from 'lucide-react';
import { useApp } from '../context/AppContext';
import { formatMoney, formatDate } from '../utils/format';
import { SavingsEntry, BANK_ACCOUNTS } from '../types/models';
import AnimatedMoney from '../components/AnimatedMoney';
import { fadeInUp, staggerFadeInUp } from '../utils/animations';

export default function SavingsPage() {
  const { customerId } = useParams<{ customerId: string }>();
  const navigate = useNavigate();
  const { customers, settings, addSavingsDeposit, addSavingsWithdrawal, deleteSavingsEntry } = useApp();
  const pageRef = useRef<HTMLDivElement>(null);
  const balanceRef = useRef<HTMLDivElement>(null);

  const customer = customers.find(c => c.id === customerId);
  const [showDeposit, setShowDeposit] = useState(false);
  const [showWithdraw, setShowWithdraw] = useState(false);
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const [bankAccountId, setBankAccountId] = useState('');
  const [bankAccountName, setBankAccountName] = useState('');
  const [saving, setSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState<SavingsEntry | null>(null);

  // Only show bank accounts that are enabled in settings
  const availableBankAccounts = useMemo(() =>
    BANK_ACCOUNTS.filter(a => settings.selectedAccountIds.has(a.id)),
    [settings.selectedAccountIds]
  );

  if (!customer) {
    return (
      <div className="page-content">
        <button className="btn btn-ghost" onClick={() => navigate(-1)}><ArrowLeft size={18} /> Back</button>
        <div className="empty-state"><h3>Customer not found</h3></div>
      </div>
    );
  }

  useEffect(() => {
    if (pageRef.current) fadeInUp(pageRef.current, 0, 400);
    if (balanceRef.current) fadeInUp(balanceRef.current, 100, 500);
    staggerFadeInUp('.savings-card', 60, 'first', 400);
  }, [customer.id]);

  const totalDeposited = customer.savingsEntries.filter(e => e.type === 'deposit').reduce((s, e) => s + e.amount, 0);
  const totalWithdrawn = customer.savingsEntries.filter(e => e.type === 'withdrawal').reduce((s, e) => s + e.amount, 0);

  // Compute per-bank-account breakdown of deposits
  const bankBreakdown = useMemo(() => {
    const map = new Map<string, { name: string; deposited: number; withdrawn: number }>();
    customer.savingsEntries.forEach(e => {
      if (!e.bankAccountId) return;
      const existing = map.get(e.bankAccountId);
      if (existing) {
        if (e.type === 'deposit') existing.deposited += e.amount;
        else existing.withdrawn += e.amount;
      } else {
        map.set(e.bankAccountId, {
          name: e.bankAccountName || e.bankAccountId,
          deposited: e.type === 'deposit' ? e.amount : 0,
          withdrawn: e.type === 'withdrawal' ? e.amount : 0,
        });
      }
    });
    return Array.from(map.values()).map(b => ({
      ...b,
      balance: Math.max(0, b.deposited - b.withdrawn),
    })).filter(b => b.deposited > 0);
  }, [customer.savingsEntries]);

  const openDeposit = () => {
    setAmount('');
    setNote('');
    // Pre-select first available bank account
    const first = availableBankAccounts[0];
    setBankAccountId(first?.id ?? '');
    setBankAccountName(first?.name ?? '');
    setShowDeposit(true);
  };

  const openWithdraw = () => {
    setAmount('');
    setNote('');
    setShowWithdraw(true);
  };

  const handleDeposit = async () => {
    if (!amount || parseFloat(amount) <= 0) return;
    setSaving(true);
    try {
      await addSavingsDeposit(customer.id, customer.name, amount, note, bankAccountId, bankAccountName);
      setShowDeposit(false);
    } finally {
      setSaving(false);
    }
  };

  const handleWithdraw = async () => {
    if (!amount || parseFloat(amount) <= 0) return;
    setSaving(true);
    try {
      await addSavingsWithdrawal(customer.id, customer.name, amount, note);
      setShowWithdraw(false);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="page-content" ref={pageRef}>
      <button className="btn btn-ghost" style={{ marginBottom: '1rem' }} onClick={() => navigate(`/customers/${customer.id}`)}>
        <ArrowLeft size={18} /> {customer.name}
      </button>

      {/* Balance card */}
      <div className="flow-card" style={{ marginBottom: '1rem' }} ref={balanceRef}>
        <p className="text-muted text-xs" style={{ textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 6 }}>
          Available Balance
        </p>
        <h1 style={{ fontSize: '2.5rem', fontWeight: 800, color: 'var(--primary)', marginBottom: 4 }}>
          <AnimatedMoney value={customer.savingsBalance} duration={800} />
        </h1>
        <p className="text-muted text-sm" style={{ marginBottom: '1.25rem' }}>
          Bank account savings — not a loan
        </p>

        <div className="two-col" style={{ marginBottom: '1rem' }}>
          <div className="metric-pill">
            <span className="label">Total Deposited</span>
            <span className="value text-primary"><AnimatedMoney value={totalDeposited} /></span>
          </div>
          <div className="metric-pill">
            <span className="label">Total Withdrawn</span>
            <span className="value" style={{ color: 'var(--warning)' }}><AnimatedMoney value={totalWithdrawn} /></span>
          </div>
        </div>

        {/* Per-bank-account breakdown */}
        {bankBreakdown.length > 0 && (
          <div style={{ marginBottom: '1.25rem' }}>
            <p style={{ fontSize: '0.6875rem', fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 8 }}>
              Held In
            </p>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {bankBreakdown.map(b => (
                <div key={b.name} style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  background: 'color-mix(in srgb, var(--secondary) 8%, transparent)',
                  border: '1px solid color-mix(in srgb, var(--secondary) 20%, transparent)',
                  borderRadius: 10,
                  padding: '8px 12px',
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <div style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--secondary)', flexShrink: 0 }} />
                    <div>
                      <div style={{ fontSize: '0.8125rem', fontWeight: 600, color: 'var(--text)' }}>{b.name}</div>
                      <div style={{ fontSize: '0.6875rem', color: 'var(--secondary)' }}>Bank Account</div>
                    </div>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: '0.8125rem', fontWeight: 700, color: 'var(--secondary)' }}>
                      <AnimatedMoney value={b.balance} />
                    </div>
                    <div style={{ fontSize: '0.6875rem', color: 'var(--text-muted)' }}>
                      Dep. <AnimatedMoney value={b.deposited} />
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        <div style={{ display: 'flex', gap: 10 }}>
          <button className="btn btn-primary" style={{ flex: 1 }} onClick={openDeposit}>
            <Plus size={16} /> Deposit
          </button>
          <button
            className="btn"
            style={{ flex: 1, background: 'var(--warning)', color: 'white' }}
            disabled={customer.savingsBalance <= 0}
            onClick={openWithdraw}
          >
            <Minus size={16} /> Withdraw
          </button>
        </div>
      </div>

      {/* History */}
      <h3 style={{ marginBottom: '0.75rem', textTransform: 'uppercase', fontSize: '0.75rem', letterSpacing: '0.04em', color: 'var(--text-muted)' }}>
        History
      </h3>

      {customer.savingsEntries.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state-icon">🐷</div>
          <h3>No savings yet</h3>
          <p>Tap Deposit to record the first deposit for {customer.name}.</p>
        </div>
      ) : (
        customer.savingsEntries.map(entry => {
          const isDeposit = entry.type === 'deposit';
          const accentColor = isDeposit ? 'var(--primary)' : 'var(--warning)';
          return (
            <div key={entry.id} className="flow-card savings-card" style={{ '--card-accent': accentColor, marginBottom: '0.75rem' } as React.CSSProperties}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <div style={{
                  width: 36, height: 36, borderRadius: '50%',
                  background: `color-mix(in srgb, ${accentColor} 15%, transparent)`,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  flexShrink: 0,
                }}>
                  {isDeposit ? <Plus size={16} color={accentColor} /> : <Minus size={16} color={accentColor} />}
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontWeight: 600, color: accentColor }}>
                    {isDeposit ? 'Deposit' : 'Withdrawal'}
                  </div>
                  {/* Bank account name — shown for deposits */}
                  {isDeposit && entry.bankAccountName && (
                    <div style={{
                      display: 'inline-flex', alignItems: 'center', gap: 4,
                      fontSize: '0.75rem', fontWeight: 600,
                      color: 'var(--secondary)',
                      background: 'color-mix(in srgb, var(--secondary) 10%, transparent)',
                      border: '1px solid color-mix(in srgb, var(--secondary) 25%, transparent)',
                      borderRadius: 6, padding: '1px 7px', marginTop: 2,
                    }}>
                      🏦 {entry.bankAccountName}
                    </div>
                  )}
                  {entry.note && (
                    <div className="text-muted text-sm truncate" style={{ marginTop: 2 }}>{entry.note}</div>
                  )}
                  <div className="text-muted text-xs" style={{ marginTop: 2 }}>{formatDate(entry.date)}</div>
                </div>
                <div style={{ textAlign: 'right', flexShrink: 0 }}>
                  <div style={{ fontWeight: 700, fontSize: '1rem', color: accentColor }}>
                    {isDeposit ? '+' : '-'}{formatMoney(entry.amount)}
                  </div>
                </div>
                <button
                  className="btn btn-ghost btn-sm"
                  style={{ color: 'var(--red)' }}
                  onClick={() => setConfirmDelete(entry)}
                >
                  <Trash2 size={14} />
                </button>
              </div>
            </div>
          );
        })
      )}

      {/* Deposit modal */}
      {showDeposit && (
        <div className="modal-overlay" onClick={() => setShowDeposit(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h3 className="modal-title">Deposit</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.875rem', marginBottom: '1rem' }}>
              <div className="form-group">
                <label className="form-label">Amount</label>
                <input
                  className="form-input"
                  type="number"
                  value={amount}
                  onChange={e => setAmount(e.target.value)}
                  placeholder="0.00"
                  autoFocus
                />
              </div>
              <div className="form-group">
                <label className="form-label">Bank Account (where savings are held)</label>
                {availableBankAccounts.length > 0 ? (
                  <select
                    className="form-select"
                    value={bankAccountId}
                    onChange={e => {
                      const opt = availableBankAccounts.find(a => a.id === e.target.value);
                      setBankAccountId(e.target.value);
                      setBankAccountName(opt?.name ?? '');
                    }}
                  >
                    <option value="">— No specific account —</option>
                    {availableBankAccounts.map(a => (
                      <option key={a.id} value={a.id}>{a.name}</option>
                    ))}
                  </select>
                ) : (
                  <input
                    className="form-input"
                    value={bankAccountName}
                    onChange={e => { setBankAccountName(e.target.value); setBankAccountId(''); }}
                    placeholder="e.g. SBI, HDFC Bank"
                  />
                )}
              </div>
              <div className="form-group">
                <label className="form-label">Note (optional)</label>
                <input
                  className="form-input"
                  value={note}
                  onChange={e => setNote(e.target.value)}
                  placeholder="Add a note"
                />
              </div>
            </div>
            <div className="modal-actions">
              <button className="btn btn-outline" onClick={() => setShowDeposit(false)}>Cancel</button>
              <button
                className="btn btn-primary"
                onClick={handleDeposit}
                disabled={saving || !amount || parseFloat(amount) <= 0}
              >
                {saving ? 'Saving...' : 'Deposit'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Withdraw modal */}
      {showWithdraw && (
        <div className="modal-overlay" onClick={() => setShowWithdraw(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h3 className="modal-title">Withdraw</h3>
            <p className="modal-subtitle">Available: {formatMoney(customer.savingsBalance)}</p>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.875rem', marginBottom: '1rem' }}>
              <div className="form-group">
                <label className="form-label">Amount</label>
                <input
                  className={`form-input${amount && parseFloat(amount) > customer.savingsBalance ? ' error' : ''}`}
                  type="number"
                  value={amount}
                  onChange={e => setAmount(e.target.value)}
                  placeholder="0.00"
                  autoFocus
                />
              </div>
              <div className="form-group">
                <label className="form-label">Note (optional)</label>
                <input
                  className="form-input"
                  value={note}
                  onChange={e => setNote(e.target.value)}
                  placeholder="Add a note"
                />
              </div>
            </div>
            <div className="modal-actions">
              <button className="btn btn-outline" onClick={() => setShowWithdraw(false)}>Cancel</button>
              <button
                className="btn"
                style={{ background: 'var(--warning)', color: 'white' }}
                onClick={handleWithdraw}
                disabled={saving || !amount || parseFloat(amount) <= 0 || parseFloat(amount) > customer.savingsBalance}
              >
                {saving ? 'Saving...' : 'Withdraw'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Confirm delete */}
      {confirmDelete && (
        <div className="modal-overlay" onClick={() => setConfirmDelete(null)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h3 className="modal-title">Delete Entry?</h3>
            <p className="modal-subtitle">
              Remove this {confirmDelete.type} of {formatMoney(confirmDelete.amount)} on {formatDate(confirmDelete.date)}? This cannot be undone.
            </p>
            <div className="modal-actions">
              <button className="btn btn-outline" onClick={() => setConfirmDelete(null)}>Cancel</button>
              <button
                className="btn btn-danger"
                onClick={async () => { await deleteSavingsEntry(confirmDelete.id); setConfirmDelete(null); }}
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
