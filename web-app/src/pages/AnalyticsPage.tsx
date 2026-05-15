import React, { useState, useMemo, useEffect, useRef } from 'react';
import { useApp } from '../context/AppContext';
import { formatMoney } from '../utils/format';
import { CardSummary, CustomerSummary } from '../types/models';
import AnimatedMoney from '../components/AnimatedMoney';
import { fadeInUp, staggerFadeInUp } from '../utils/animations';

type Metric = 'USAGE' | 'PAID' | 'OUTSTANDING';

const METRICS: { value: Metric; label: string }[] = [
  { value: 'USAGE', label: 'Usage Breakdown' },
  { value: 'PAID', label: 'Paid Amount' },
  { value: 'OUTSTANDING', label: 'Outstanding Balance' },
];

function cardMetricValue(card: CardSummary, metric: Metric): number {
  switch (metric) {
    case 'USAGE': return card.bill;
    // card.pending = owner's own bank payments (not customer-paid)
    // customer-paid = bill - payable (what's been collected from customers)
    case 'PAID': return Math.max(0, card.bill - card.payable);
    case 'OUTSTANDING': return card.payable;
  }
}

function customerMetricValue(customer: CustomerSummary, metric: Metric): number {
  switch (metric) {
    case 'USAGE': return customer.totalAmount;
    // creditDueAmount is already the total paid (manual + settled + partial)
    // as computed in firebaseRepository.ts buildAppData
    case 'PAID': return customer.creditDueAmount;
    case 'OUTSTANDING': return customer.balance;
  }
}

export default function AnalyticsPage() {
  const { cards, customers } = useApp();
  const pageRef = useRef<HTMLDivElement>(null);
  const [accountKindFilter, setAccountKindFilter] = useState<'credit_card' | 'bank_account'>('credit_card');
  const [accountMetric, setAccountMetric] = useState<Metric>('USAGE');
  const [selectedCustomerId, setSelectedCustomerId] = useState('');
  const [customerMetric, setCustomerMetric] = useState<Metric>('USAGE');

  const usedAccountIds = useMemo(() =>
    new Set(customers.flatMap(c => c.transactions.map(t => t.accountId))),
    [customers]
  );

  const visibleCards = useMemo(() =>
    cards.filter(c => usedAccountIds.has(c.id)),
    [cards, usedAccountIds]
  );

  const totalUsed = visibleCards.reduce((s, c) => s + c.bill, 0);
  const totalPaid = visibleCards.reduce((s, c) => s + Math.max(0, c.bill - c.payable), 0);
  const totalBalance = visibleCards.reduce((s, c) => s + c.payable, 0);

  useEffect(() => {
    if (pageRef.current) fadeInUp(pageRef.current, 0, 400);
    staggerFadeInUp('.analytics-card', 80, 'first', 400);
  }, []);

  const filteredCards = visibleCards.filter(c => c.accountKind === accountKindFilter);
  const sortedCustomers = [...customers].sort((a, b) => a.name.localeCompare(b.name));
  // BUG-57 fix: derive selectedCustomer from state, default to first customer's id
  const effectiveCustomerId = selectedCustomerId || sortedCustomers[0]?.id || '';
  const selectedCustomer = sortedCustomers.find(c => c.id === effectiveCustomerId) ?? sortedCustomers[0];

  return (
    <div className="page-content" ref={pageRef}>
      <div style={{ marginBottom: '1.5rem' }}>
        <h2>Analytics</h2>
        <p className="text-muted text-sm" style={{ marginTop: 4 }}>Inspect accounts and customers with quick metric filters.</p>
      </div>

      {visibleCards.length === 0 && customers.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state-icon">📊</div>
          <h3>No analytics yet</h3>
          <p>Add customer transactions to unlock insights.</p>
        </div>
      ) : (
        <>
          {/* Overall summary */}
          {visibleCards.length > 0 && (
            <>
              <div className="hero-panel analytics-card" style={{ marginBottom: '1rem' }}>
                <p style={{ fontSize: '0.8125rem', fontWeight: 600, opacity: 0.8, textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 8 }}>Total Balance</p>
                <h1 style={{ fontSize: '2rem', fontWeight: 800, marginBottom: 4 }}><AnimatedMoney value={totalBalance} duration={800} /></h1>
                <p style={{ fontSize: '0.875rem', opacity: 0.75 }}>Used <AnimatedMoney value={totalUsed} /> minus paid <AnimatedMoney value={totalPaid} /></p>
              </div>

              <div className="flow-card analytics-card" style={{ marginBottom: '1rem' }}>
                <h3 style={{ marginBottom: '1rem' }}>Overall Summary</h3>
                <div className="two-col" style={{ marginBottom: '0.75rem' }}>
                  <div className="metric-pill">
                    <span className="label">Used</span>
                    <span className="value text-primary"><AnimatedMoney value={totalUsed} /></span>
                  </div>
                  <div className="metric-pill">
                    <span className="label">Paid</span>
                    <span className="value" style={{ color: 'var(--secondary)' }}><AnimatedMoney value={totalPaid} /></span>
                  </div>
                </div>
                <div className="accent-row">
                  <span className="accent-label">Balance</span>
                  <span className="accent-value" style={{ color: totalBalance > 0 ? 'var(--warning)' : 'var(--primary)' }}><AnimatedMoney value={totalBalance} /></span>
                </div>
              </div>
            </>
          )}

          {/* Account analytics */}
          <div className="flow-card analytics-card" style={{ marginBottom: '1rem' }}>
            <h3 style={{ marginBottom: '1rem' }}>Account Analytics</h3>

            <div style={{ display: 'flex', gap: 8, marginBottom: '0.875rem', flexWrap: 'wrap' }}>
              <button
                className={`btn btn-sm ${accountKindFilter === 'credit_card' ? 'btn-primary' : 'btn-outline'}`}
                onClick={() => setAccountKindFilter('credit_card')}
              >
                Credit Cards
              </button>
              <button
                className={`btn btn-sm ${accountKindFilter === 'bank_account' ? 'btn-primary' : 'btn-outline'}`}
                onClick={() => setAccountKindFilter('bank_account')}
              >
                Bank Accounts
              </button>
            </div>

            <div style={{ display: 'flex', gap: 8, marginBottom: '1rem', flexWrap: 'wrap' }}>
              {METRICS.map(m => (
                <button
                  key={m.value}
                  className={`btn btn-sm ${accountMetric === m.value ? 'btn-primary' : 'btn-outline'}`}
                  onClick={() => setAccountMetric(m.value)}
                >
                  {m.label}
                </button>
              ))}
            </div>

            <p className="text-muted text-sm" style={{ marginBottom: '0.75rem' }}>
              {filteredCards.length} {accountKindFilter === 'credit_card' ? 'credit card' : 'bank account'}(s)
            </p>

            {filteredCards.length === 0 ? (
              <p className="text-muted text-sm">No {accountKindFilter === 'credit_card' ? 'credit card' : 'bank account'} entries have activity yet.</p>
            ) : (
              filteredCards.map(card => (
                <div key={card.id} style={{ background: 'var(--bg-soft)', borderRadius: 14, padding: '0.875rem', marginBottom: 8 }}>
                  <div className="truncate font-semibold" style={{ marginBottom: 2 }}>{card.name}</div>
                  <div className="text-muted text-xs" style={{ marginBottom: 8 }}>{card.accountKind === 'credit_card' ? 'Credit Card' : 'Bank Account'}</div>
                  <div className="accent-row">
                    <span className="accent-label">{METRICS.find(m => m.value === accountMetric)?.label}</span>
                    <span className="accent-value" style={{ color: 'var(--primary)' }}><AnimatedMoney value={cardMetricValue(card, accountMetric)} /></span>
                  </div>
                </div>
              ))
            )}
          </div>

          {/* Customer analytics */}
          {sortedCustomers.length > 0 && selectedCustomer && (
            <div className="flow-card analytics-card">
              <h3 style={{ marginBottom: '1rem' }}>Customer Analytics</h3>

              <div className="form-group" style={{ marginBottom: '0.875rem' }}>
                <label className="form-label">Customer</label>
                <select
                  className="form-select"
                  value={effectiveCustomerId}
                  onChange={e => setSelectedCustomerId(e.target.value)}
                >
                  {sortedCustomers.map(c => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
              </div>

              <div style={{ display: 'flex', gap: 8, marginBottom: '1rem', flexWrap: 'wrap' }}>
                {METRICS.map(m => (
                  <button
                    key={m.value}
                    className={`btn btn-sm ${customerMetric === m.value ? 'btn-primary' : 'btn-outline'}`}
                    onClick={() => setCustomerMetric(m.value)}
                  >
                    {m.label}
                  </button>
                ))}
              </div>

              <h4 style={{ marginBottom: 4 }}>{selectedCustomer.name}</h4>
              <p className="text-muted text-sm" style={{ marginBottom: '1rem' }}>
                {selectedCustomer.transactions.length} transaction(s)
              </p>

              <div className="two-col" style={{ marginBottom: '0.75rem' }}>
                <div className="metric-pill">
                  <span className="label">Used</span>
                  <span className="value text-primary"><AnimatedMoney value={selectedCustomer.totalAmount} /></span>
                </div>
                <div className="metric-pill">
                  <span className="label">Paid</span>
                  <span className="value" style={{ color: 'var(--secondary)' }}><AnimatedMoney value={selectedCustomer.creditDueAmount} /></span>
                </div>
              </div>

              <div className="accent-row">
                <span className="accent-label">{METRICS.find(m => m.value === customerMetric)?.label}</span>
                <span className="accent-value" style={{ color: 'var(--primary)' }}>
                  <AnimatedMoney value={customerMetricValue(selectedCustomer, customerMetric)} />
                </span>
              </div>

              <p className="text-muted text-xs" style={{ marginTop: 8 }}>
                Outstanding balance: <AnimatedMoney value={selectedCustomer.balance} />
              </p>
            </div>
          )}
        </>
      )}
    </div>
  );
}
