import React, { useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowLeft } from 'lucide-react';
import { useApp } from '../context/AppContext';
import { isVisibleInTransactions } from '../types/models';
import AnimatedMoney from '../components/AnimatedMoney';
import SpotlightCard from '../components/animations/SpotlightCard';
import TiltedCard from '../components/animations/TiltedCard';
import BlurText from '../components/animations/BlurText';

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

// ── Memoized customer row for account detail list ──────────────────────────────

const AccountCustomerRow = React.memo(function AccountCustomerRow({ customer, used, due, onClick }: {
  customer: { id: string; name: string };
  used: number;
  due: number;
  onClick: () => void;
}) {
  return (
    <TiltedCard maxTilt={5} glare={false} scale={1.005} perspective={1000}>
    <motion.div
      className="flow-card"
      style={{ cursor: 'pointer', marginBottom: '0.75rem' }}
      onClick={onClick}
      variants={itemVariants}
      whileHover={{ y: -2 }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <div className="avatar">{customer.name.slice(0, 2).toUpperCase()}</div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div className="truncate font-semibold">{customer.name}</div>
          <div className="text-muted text-sm">Used <AnimatedMoney value={used} /></div>
        </div>
        <div style={{ textAlign: 'right' }}>
          <div style={{ fontWeight: 700, color: due > 0 ? 'var(--warning)' : 'var(--primary)' }}><AnimatedMoney value={due} /></div>
          <div className="text-muted text-xs">{due > 0 ? 'Due' : 'Settled'}</div>
        </div>
      </div>
    </motion.div>
    </TiltedCard>
  );
});

export default function AccountDetail() {
  const { accountId } = useParams<{ accountId: string }>();
  const navigate = useNavigate();
  const { cards, customers } = useApp();

  const card = cards.find(c => c.id === accountId);
  const accountCustomers = useMemo(() => {
    if (!accountId) return [];
    return customers.flatMap(customer => {
      const txns = customer.transactions.filter(t => t.accountId === accountId && isVisibleInTransactions(t));
      if (txns.length === 0) return [];
      const used = txns.reduce((s, t) => s + t.amount, 0);
      const paid = txns.filter(t => t.isSettled).reduce((s, t) => s + t.amount, 0)
        + txns.filter(t => !t.isSettled).reduce((s, t) => s + t.partialPaidAmount, 0);
      const due = Math.max(0, used - paid);
      return [{ customer, used, due, txns }];
    }).sort((a, b) => b.due - a.due);
  }, [customers, accountId]);

  if (!card) {
    return (
      <div className="page-content">
        <button className="btn btn-ghost" onClick={() => navigate('/accounts')}><ArrowLeft size={18} /> Back</button>
        <div className="empty-state"><h3>Account not found</h3></div>
      </div>
    );
  }

  const accentColor = card.accountKind === 'credit_card' ? 'var(--warning)' : 'var(--secondary)';

  return (
    <motion.div
      className="page-content"
      variants={containerVariants}
      initial="hidden"
      animate="visible"
    >
      <motion.div variants={itemVariants}>
        <button className="btn btn-ghost" style={{ marginBottom: '1rem' }} onClick={() => navigate('/accounts')}>
          <ArrowLeft size={18} /> Accounts
        </button>
      </motion.div>

      {/* Account summary */}
      <SpotlightCard spotlightColor={card.accountKind === 'credit_card' ? 'rgba(245, 87, 108, 0.06)' : 'rgba(45, 212, 160, 0.06)'}>
      <motion.div
        className="flow-card"
        style={{ '--card-accent': accentColor, marginBottom: '1rem' } as React.CSSProperties}
        variants={itemVariants}
      >
        <BlurText as="h2" text={card.name} delay={0.05} duration={0.35} blurAmount={6} style={{ marginBottom: 4 }} />
        <p className="text-muted text-sm" style={{ marginBottom: '1rem' }}>
          {card.accountKind === 'credit_card' ? 'Credit Card' : 'Bank Account'}
        </p>

        <div className="two-col" style={{ marginBottom: '0.75rem' }}>
          <div className="metric-pill">
            <span className="label">Total Used</span>
            <span className="value" style={{ color: accentColor }}><AnimatedMoney value={card.bill} /></span>
          </div>
          <div className="metric-pill">
            <span className="label">Personal Paid</span>
            <span className="value" style={{ color: 'var(--secondary)' }}><AnimatedMoney value={card.pending} /></span>
          </div>
        </div>

        <div className="accent-row">
          <span className="accent-label">Balance</span>
          <span className="accent-value" style={{ color: accentColor }}><AnimatedMoney value={card.payable} /></span>
        </div>

      </motion.div>
      </SpotlightCard>

      {/* Customers using this account */}
      {accountCustomers.length > 0 && (
        <>
          <motion.h3 style={{ marginBottom: '0.75rem' }} variants={itemVariants}>Customers</motion.h3>
          {accountCustomers.map(({ customer, used, due }) => (
            <AccountCustomerRow
              key={customer.id}
              customer={customer}
              used={used}
              due={due}
              onClick={() => navigate(`/customers/${customer.id}`)}
            />
          ))}
        </>
      )}

    </motion.div>
  );
}
