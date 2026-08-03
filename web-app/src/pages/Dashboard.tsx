import React, { useMemo, useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { TrendingUp, TrendingDown, Sparkles } from 'lucide-react';
import { useApp } from '../context/AppContext';
import { formatMoney, getGreeting } from '../utils/format';
import { CardSummary, isVisibleInTransactions, isScheduledForFutureMonth } from '../types/models';
import AnimatedAvatar from '../components/AnimatedAvatar';
import AnimatedMoney from '../components/AnimatedMoney';
import SpotlightCard from '../components/animations/SpotlightCard';
import BlurText from '../components/animations/BlurText';
import ScrollReveal from '../components/animations/ScrollReveal';

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.07, delayChildren: 0.1 },
  },
};

const itemVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.4, ease: 'easeOut' as const } },
};

// ── Hero panel ────────────────────────────────────────────────────────────────
function HeroPanel({ title, value, subtitle }: { title: string; value: number; subtitle: string }) {
  return (
    <SpotlightCard spotlightColor="rgba(91, 127, 255, 0.06)">
      <motion.div
        className="hero-panel"
        style={{ marginBottom: '1rem' }}
        variants={itemVariants}
      >
        <p style={{ fontSize: '0.8125rem', fontWeight: 600, opacity: 0.8, textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 8 }}>{title}</p>
        <h1 style={{ fontSize: '2.25rem', fontWeight: 800, marginBottom: 8, letterSpacing: '-0.02em' }}>
          <AnimatedMoney value={value} duration={800} />
        </h1>
        <p style={{ fontSize: '0.875rem', opacity: 0.75 }}>{subtitle}</p>
      </motion.div>
    </SpotlightCard>
  );
}

// ── Activity card (per account) ───────────────────────────────────────────────
const ActivityCard = React.memo(function ActivityCard({ card, currentDue, emiOutstanding }: {
  card: CardSummary;
  currentDue: number;
  emiOutstanding: number;
}) {
  const isCredit = card.accountKind === 'credit_card';
  const accentColor = isCredit ? 'var(--warning)' : 'var(--secondary)';
  const [expanded, setExpanded] = useState(false);

  return (
    <SpotlightCard spotlightColor={isCredit ? 'rgba(245, 87, 108, 0.06)' : 'rgba(45, 212, 160, 0.06)'}>
    <motion.div
      className="flow-card"
      style={{ '--card-accent': accentColor, marginBottom: '0.75rem', cursor: 'pointer' } as React.CSSProperties}
      onClick={() => setExpanded(v => !v)}
      whileHover={{ y: -2 }}
      whileTap={{ scale: 0.99 }}
      transition={{ type: 'spring', stiffness: 400, damping: 20 }}
      layout
    >
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
        <motion.div
          style={{
            width: 42, height: 42, borderRadius: '50%',
            background: `color-mix(in srgb, ${accentColor} 15%, transparent)`,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            flexShrink: 0, marginTop: 2,
          }}
          whileHover={{ rotate: 15 }}
        >
          {isCredit ? <TrendingUp size={18} color={accentColor} /> : <TrendingDown size={18} color={accentColor} />}
        </motion.div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div className="truncate font-semibold">{card.name}</div>
          <div className="text-muted text-sm">
            {isCredit ? 'Credit Card' : 'Bank Account'}
            {card.dueDate && ` • Due ${card.dueDate}`}
          </div>

          {isCredit ? (
            <div style={{ display: 'flex', gap: 8, marginTop: 8, flexWrap: 'wrap' }}>
              <div style={{
                flex: 1, minWidth: 100,
                background: 'color-mix(in srgb, var(--warning) 8%, transparent)',
                border: '1px solid color-mix(in srgb, var(--warning) 20%, transparent)',
                borderRadius: 8, padding: '6px 10px',
              }}>
                <div style={{ fontSize: '0.6875rem', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                  Current Due
                </div>
                <div style={{ fontSize: '0.9375rem', fontWeight: 700, color: 'var(--warning)', marginTop: 2 }}>
                  <AnimatedMoney value={currentDue} />
                </div>
              </div>
              {emiOutstanding > 0 && (
                <div style={{
                  flex: 1, minWidth: 100,
                  background: 'color-mix(in srgb, var(--primary) 8%, transparent)',
                  border: '1px solid color-mix(in srgb, var(--primary) 20%, transparent)',
                  borderRadius: 8, padding: '6px 10px',
                }}>
                  <div style={{ fontSize: '0.6875rem', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                    EMI Outstanding
                  </div>
                  <div style={{ fontSize: '0.9375rem', fontWeight: 700, color: 'var(--primary)', marginTop: 2 }}>
                    <AnimatedMoney value={emiOutstanding} />
                  </div>
                </div>
              )}
              <AnimatePresence>
                {expanded && (
                  <motion.div
                    style={{ width: '100%', marginTop: 4, fontSize: '0.8125rem', color: 'var(--text-muted)' }}
                    initial={{ height: 0, opacity: 0 }}
                    animate={{ height: 'auto', opacity: 1 }}
                    exit={{ height: 0, opacity: 0 }}
                    transition={{ duration: 0.2 }}
                  >
                    <div className="accent-row">
                      <span className="accent-label">Total Used</span>
                      <span className="accent-value" style={{ fontSize: '0.875rem' }}>
                        <AnimatedMoney value={card.bill} />
                      </span>
                    </div>
                    <div className="accent-row">
                      <span className="accent-label">Total Paid</span>
                      <span className="accent-value" style={{ fontSize: '0.875rem', color: 'var(--secondary)' }}>
                        <AnimatedMoney value={Math.max(0, card.bill - card.payable)} />
                      </span>
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          ) : (
            <div style={{ marginTop: 6, fontWeight: 700, fontSize: '1rem', color: accentColor }}>
              +<AnimatedMoney value={card.payable} />
            </div>
          )}
        </div>
      </div>
    </motion.div>
    </SpotlightCard>
  );
});

// ── Person summary card ───────────────────────────────────────────────────────
interface PersonSummary {
  personId: string;
  personName: string;
  totalUsed: number;
  totalDue: number;
}

const PersonCard = React.memo(function PersonCard({ person }: { person: PersonSummary }) {
  return (
    <SpotlightCard spotlightColor="rgba(167, 139, 250, 0.06)">
    <motion.div
      className="flow-card"
      style={{ '--card-accent': 'var(--primary)', marginBottom: '0.75rem' } as React.CSSProperties}
      whileHover={{ y: -2 }}
      whileTap={{ scale: 0.99 }}
      transition={{ type: 'spring', stiffness: 400, damping: 20 }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <AnimatedAvatar name={person.personName} size={40} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div className="truncate font-semibold">{person.personName}</div>
          <div className="text-muted text-sm">Person • Used <AnimatedMoney value={person.totalUsed} /></div>
        </div>
        <div style={{ textAlign: 'right', flexShrink: 0 }}>
          <div style={{ fontWeight: 700, fontSize: '1.05rem', color: person.totalDue > 0 ? 'var(--warning)' : 'var(--primary)' }}>
            <AnimatedMoney value={person.totalDue} />
          </div>
          <div className="text-muted text-xs">{person.totalDue > 0 ? 'Due' : 'Settled'}</div>
        </div>
      </div>
    </motion.div>
    </SpotlightCard>
  );
});

// ── Main Dashboard ────────────────────────────────────────────────────────────
export default function Dashboard() {
  const { cards, customers, profile } = useApp();
  const navigate = useNavigate();
  const pageRef = useRef<HTMLDivElement>(null);

  const usedAccountIds = useMemo(() =>
    new Set(customers.flatMap(c => c.transactions.map(t => t.accountId))),
    [customers]
  );

  const visibleCards = useMemo(() =>
    cards.filter(c => usedAccountIds.has(c.id) && c.accountKind !== 'person'),
    [cards, usedAccountIds]
  );

  const totalUsed    = customers.reduce((s, c) => s + c.totalAmount, 0);
  const totalPaid    = customers.reduce((s, c) => s + c.creditDueAmount, 0);
  const totalBalance = customers.reduce((s, c) => s + c.balance, 0);

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

  const personSummaries = useMemo((): PersonSummary[] => {
    const map = new Map<string, PersonSummary>();
    customers.forEach(customer => {
      customer.transactions
        .filter(t => t.accountKind === 'person' && isVisibleInTransactions(t))
        .forEach(t => {
          const key  = t.accountId;
          const name = t.personName || t.accountName;
          const due  = t.isSettled ? 0 : Math.max(0, t.amount - t.partialPaidAmount);
          const existing = map.get(key);
          if (!existing) {
            map.set(key, { personId: key, personName: name, totalUsed: t.amount, totalDue: due });
          } else {
            map.set(key, { ...existing, totalUsed: existing.totalUsed + t.amount, totalDue: existing.totalDue + due });
          }
        });
    });
    return Array.from(map.values()).sort((a, b) => b.totalDue - a.totalDue);
  }, [customers]);

  const greeting = getGreeting();
  const name = profile?.displayName?.trim() || 'Your Profile';

  return (
    <motion.div
      className="page-content"
      ref={pageRef}
      variants={containerVariants}
      initial="hidden"
      animate="visible"
    >
      {/* Header */}
      <motion.div
        style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem' }}
        variants={itemVariants}
      >
        <div>
          <p className="text-muted text-sm" style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            {greeting}
            <Sparkles size={12} style={{ color: 'var(--warning)', opacity: 0.6 }} />
          </p>
          <BlurText as="h2" text={name} delay={0.1} duration={0.4} blurAmount={8} />
        </div>
        <AnimatedAvatar
          name={name}
          photoUrl={profile?.photoUrl}
          size={44}
          onClick={() => navigate('/settings')}
          style={{ cursor: 'pointer' }}
        />
      </motion.div>

      {/* Hero panel */}
      <HeroPanel
        title="Outstanding Balance"
        value={totalBalance}
        subtitle={`${visibleCards.length} active account(s) contributing to your ledger flow.`}
      />

      {/* Metrics */}
      <ScrollReveal direction="up" distance={20}>
      <motion.div className="flow-card" style={{ marginBottom: '1.5rem' }} variants={itemVariants}>
        <div className="two-col">
          <div className="metric-pill">
            <span className="label">Total Used</span>
            <span className="value" style={{ background: 'linear-gradient(135deg, var(--warning), var(--orange))', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
              <AnimatedMoney value={totalUsed} />
            </span>
          </div>
          <div className="metric-pill">
            <span className="label">Total Paid</span>
            <span className="value" style={{ background: 'linear-gradient(135deg, var(--secondary), var(--cyan))', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
              <AnimatedMoney value={totalPaid} />
            </span>
          </div>
        </div>
      </motion.div>
      </ScrollReveal>

      {/* Account Activity */}
      <ScrollReveal direction="up" distance={20}>
      <motion.div style={{ marginBottom: '1rem' }} variants={itemVariants}>
        <h3 style={{ marginBottom: 4 }}>Account Activity</h3>
        <p className="text-muted text-sm" style={{ marginBottom: '1rem' }}>Live summary of your accounts and person balances.</p>

        {visibleCards.length === 0 && personSummaries.length === 0 ? (
          <motion.div
            className="empty-state"
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.4 }}
          >
            <div className="empty-state-icon">📊</div>
            <h3>No activity yet</h3>
            <p>As customers and payments are recorded, activity cards will appear here.</p>
          </motion.div>
        ) : (
          <motion.div variants={containerVariants} initial="hidden" animate="visible">
            {[...visibleCards].sort((a, b) => b.payable - a.payable).slice(0, 6).map((card, i) => {
              const isCredit = card.accountKind === 'credit_card';
              const currentDue = nonEmiDueByAccount.get(card.id) ?? 0;
              const emiOutstanding = emiOutstandingByAccount.get(card.id) ?? 0;
              return (
                <motion.div key={card.id} variants={itemVariants} custom={i}>
                  <ActivityCard
                    card={card}
                    currentDue={currentDue}
                    emiOutstanding={emiOutstanding}
                  />
                </motion.div>
              );
            })}
            {visibleCards.length > 6 && (
              <p className="text-muted text-xs" style={{ textAlign: 'center', padding: '0.5rem 0' }}>
                Showing 6 of {visibleCards.length} accounts — open Accounts tab to see all
              </p>
            )}
            {personSummaries.slice(0, 6).map((person, i) => (
              <motion.div key={person.personId} variants={itemVariants} custom={i}>
                <PersonCard person={person} />
              </motion.div>
            ))}
            {personSummaries.length > 6 && (
              <p className="text-muted text-xs" style={{ textAlign: 'center', padding: '0.5rem 0' }}>
                Showing 6 of {personSummaries.length} persons — open Accounts tab to see all
              </p>
            )}
          </motion.div>
        )}
      </motion.div>
      </ScrollReveal>
    </motion.div>
  );
}
