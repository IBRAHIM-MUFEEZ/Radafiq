import React, { useState, useMemo, useRef } from 'react';
import { motion } from 'framer-motion';
import { useApp } from '../context/AppContext';
import { formatMoney } from '../utils/format';
import { CardSummary, CustomerSummary } from '../types/models';
import AnimatedMoney from '../components/AnimatedMoney';

type Metric = 'USAGE' | 'PAID' | 'OUTSTANDING';

const METRICS: { value: Metric; label: string }[] = [
  { value: 'USAGE', label: 'Usage Breakdown' },
  { value: 'PAID', label: 'Paid Amount' },
  { value: 'OUTSTANDING', label: 'Outstanding Balance' },
];

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.07, delayChildren: 0.1 },
  },
};

const itemVariants = {
  hidden: { opacity: 0, y: 16 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.35, ease: 'easeOut' as const } },
};

function cardMetricValue(card: CardSummary, metric: Metric): number {
  switch (metric) {
    case 'USAGE': return card.bill;
    case 'PAID': return Math.max(0, card.bill - card.payable);
    case 'OUTSTANDING': return card.payable;
  }
}

function customerMetricValue(customer: CustomerSummary, metric: Metric): number {
  switch (metric) {
    case 'USAGE': return customer.totalAmount;
    case 'PAID': return customer.creditDueAmount;
    case 'OUTSTANDING': return customer.balance;
  }
}

export default function AnalyticsPage() {
  const { cards, customers } = useApp();
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

  const totalUsed = customers.reduce((s, c) => s + c.totalAmount, 0);
  const totalPaid = customers.reduce((s, c) => s + c.creditDueAmount, 0);
  const totalBalance = customers.reduce((s, c) => s + c.balance, 0);

  const filteredCards = visibleCards.filter(c => c.accountKind === accountKindFilter);
  const sortedCustomers = [...customers].sort((a, b) => a.name.localeCompare(b.name));
  const effectiveCustomerId = selectedCustomerId || sortedCustomers[0]?.id || '';
  const selectedCustomer = sortedCustomers.find(c => c.id === effectiveCustomerId) ?? sortedCustomers[0];

  return (
    <motion.div
      className="page-content"
      variants={containerVariants}
      initial="hidden"
      animate="visible"
    >
      <motion.div style={{ marginBottom: '1.5rem' }} variants={itemVariants}>
        <h2>Analytics</h2>
        <p className="text-muted text-sm" style={{ marginTop: 4 }}>Inspect accounts and customers with quick metric filters.</p>
      </motion.div>

      {visibleCards.length === 0 && customers.length === 0 ? (
        <motion.div className="empty-state" variants={itemVariants}>
          <div className="empty-state-icon">📊</div>
          <h3>No analytics yet</h3>
          <p>Add customer transactions to unlock insights.</p>
        </motion.div>
      ) : (
        <>
          {/* Overall summary */}
          {visibleCards.length > 0 && (
            <>
              <motion.div className="hero-panel" style={{ marginBottom: '1rem' }} variants={itemVariants}>
                <p style={{ fontSize: '0.8125rem', fontWeight: 600, opacity: 0.8, textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 8 }}>Total Balance</p>
                <h1 style={{ fontSize: '2rem', fontWeight: 800, marginBottom: 4 }}><AnimatedMoney value={totalBalance} duration={800} /></h1>
                <p style={{ fontSize: '0.875rem', opacity: 0.75 }}>Used <AnimatedMoney value={totalUsed} /> minus paid <AnimatedMoney value={totalPaid} /></p>
              </motion.div>

              <motion.div className="flow-card" style={{ marginBottom: '1rem' }} variants={itemVariants}>
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
              </motion.div>
            </>
          )}

          {/* Account analytics */}
          <motion.div className="flow-card" style={{ marginBottom: '1rem' }} variants={itemVariants}>
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

            {/* Bar chart */}
            {filteredCards.length > 0 && (() => {
              const maxVal = Math.max(...filteredCards.map(c => cardMetricValue(c, accountMetric)), 1);
              const CHART_COLORS = ['#667EEA', '#A78BFA', '#2DD4A0', '#F59E5A', '#F472B6', '#4ADE80', '#5B7FFF', '#F5576C'];
              const metricLabel = METRICS.find(m => m.value === accountMetric)?.label ?? '';
              return (
                <div style={{ marginBottom: '1rem', padding: '0.75rem 0' }}>
                  <p style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.03em', marginBottom: 12 }}>
                    {metricLabel}
                  </p>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {filteredCards.map((card, i) => {
                      const val = cardMetricValue(card, accountMetric);
                      const pct = maxVal > 0 ? (val / maxVal) * 100 : 0;
                      return (
                        <div key={card.id} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                          <span className="truncate" style={{ width: 140, fontSize: '0.8125rem', fontWeight: 500, flexShrink: 0 }}>{card.name}</span>
                          <div style={{ flex: 1, height: 22, background: 'var(--bg)', borderRadius: 6, overflow: 'hidden', position: 'relative' }}>
                            <motion.div
                              style={{
                                height: '100%',
                                borderRadius: 6,
                                background: `linear-gradient(90deg, ${CHART_COLORS[i % CHART_COLORS.length]}, color-mix(in srgb, ${CHART_COLORS[i % CHART_COLORS.length]} 60%, transparent))`,
                              }}
                              initial={{ width: 0 }}
                              animate={{ width: `${Math.max(pct, 2)}%` }}
                              transition={{ duration: 0.8, ease: 'easeOut', delay: i * 0.06 }}
                            />
                          </div>
                          <span style={{ width: 90, textAlign: 'right', fontSize: '0.8125rem', fontWeight: 600, fontVariantNumeric: 'tabular-nums', flexShrink: 0 }}>
                            <AnimatedMoney value={val} />
                          </span>
                        </div>
                      );
                    })}
                  </div>
                </div>
              );
            })()}

            {filteredCards.length === 0 ? (
              <p className="text-muted text-sm">No {accountKindFilter === 'credit_card' ? 'credit card' : 'bank account'} data available yet.</p>
            ) : (
              filteredCards.map((card, i) => (
                <motion.div
                  key={card.id}
                  style={{ background: 'var(--bg-soft)', borderRadius: 14, padding: '0.875rem', marginBottom: 8 }}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.3, delay: i * 0.05 }}
                >
                  <div className="truncate font-semibold" style={{ marginBottom: 2 }}>{card.name}</div>
                  <div className="text-muted text-xs" style={{ marginBottom: 8 }}>{card.accountKind === 'credit_card' ? 'Credit Card' : 'Bank Account'}</div>
                  <div className="accent-row">
                    <span className="accent-label">{METRICS.find(m => m.value === accountMetric)?.label}</span>
                    <span className="accent-value" style={{ color: 'var(--primary)' }}><AnimatedMoney value={cardMetricValue(card, accountMetric)} /></span>
                  </div>
                </motion.div>
              ))
            )}
          </motion.div>

          {/* Customer analytics */}
          {sortedCustomers.length > 0 && selectedCustomer && (
            <motion.div className="flow-card" variants={itemVariants}>
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
            </motion.div>
          )}
        </>
      )}
    </motion.div>
  );
}
