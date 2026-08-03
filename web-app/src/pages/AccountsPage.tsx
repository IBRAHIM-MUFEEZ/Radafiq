import React, { useMemo, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Settings } from 'lucide-react';
import { useApp } from '../context/AppContext';
import { formatMoney } from '../utils/format';
import { CardSummary, isVisibleInTransactions } from '../types/models';
import AnimatedMoney from '../components/AnimatedMoney';
import TiltedCard from '../components/animations/TiltedCard';
import BlurText from '../components/animations/BlurText';

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.06, delayChildren: 0.1 },
  },
};

const itemVariants = {
  hidden: { opacity: 0, y: 16 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.35, ease: 'easeOut' as const } },
};

function AccountRow({ card, onClick }: { card: CardSummary; onClick: () => void }) {
  const accentColor = card.accountKind === 'credit_card' ? 'var(--warning)' : 'var(--secondary)';
  return (
    <TiltedCard maxTilt={5} glare={false} scale={1.005} perspective={1000}>
    <motion.div
      className="flow-card"
      style={{ '--card-accent': accentColor, cursor: 'pointer', marginBottom: '0.75rem' } as React.CSSProperties}
      onClick={onClick}
      variants={itemVariants}
      whileHover={{ y: -2, transition: { duration: 0.2 } }}
    >
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ minWidth: 0 }}>
          <div className="truncate font-semibold">{card.name}</div>
          <div className="text-muted text-sm">{card.accountKind === 'credit_card' ? 'Credit Card' : 'Bank Account'}</div>
        </div>
        <div style={{ textAlign: 'right', flexShrink: 0 }}>
          <div style={{ fontWeight: 700, color: accentColor }}><AnimatedMoney value={card.bill} /></div>
          <div className="text-muted text-xs">Total used</div>
        </div>
      </div>
    </motion.div>
    </TiltedCard>
  );
}

export default function AccountsPage() {
  const { cards, customers } = useApp();
  const navigate = useNavigate();

  const usedAccountIds = useMemo(() =>
    new Set(customers.flatMap(c => c.transactions.map(t => t.accountId))),
    [customers]
  );

  const visibleCards = useMemo(() =>
    cards.filter(c => usedAccountIds.has(c.id) && c.accountKind !== 'person'),
    [cards, usedAccountIds]
  );

  const creditCards = visibleCards.filter(c => c.accountKind === 'credit_card');
  const bankAccounts = visibleCards.filter(c => c.accountKind === 'bank_account');

  const personCards = useMemo(() => {
    const map = new Map<string, { accountId: string; name: string; used: number; due: number }>();
    customers.forEach(customer => {
      customer.transactions
        .filter(t => t.accountKind === 'person' && isVisibleInTransactions(t))
        .forEach(t => {
          const key = t.accountId;
          const name = t.personName || t.accountName;
          const due = t.isSettled ? 0 : Math.max(0, t.amount - t.partialPaidAmount);
          const existing = map.get(key);
          if (!existing) {
            map.set(key, { accountId: key, name, used: t.amount, due });
          } else {
            map.set(key, { ...existing, used: existing.used + t.amount, due: existing.due + due });
          }
        });
    });
    return Array.from(map.values()).sort((a, b) => b.due - a.due);
  }, [customers]);

  return (
    <motion.div
      className="page-content"
      variants={containerVariants}
      initial="hidden"
      animate="visible"
    >
      <motion.div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: '1.5rem' }} variants={itemVariants}>
        <div>
          <BlurText as="h2" text="Accounts" delay={0.05} duration={0.35} blurAmount={6} />
          <p className="text-muted text-sm" style={{ marginTop: 4 }}>Monitor banks, credit cards, dues, and usage.</p>
        </div>
        <button className="btn btn-ghost" onClick={() => navigate('/settings')}>
          <Settings size={18} />
        </button>
      </motion.div>

      {visibleCards.length === 0 && personCards.length === 0 ? (
        <motion.div className="empty-state" variants={itemVariants}>
          <div className="empty-state-icon">💳</div>
          <h3>No linked totals yet</h3>
          <p>Customer transactions will appear here automatically.</p>
        </motion.div>
      ) : (
        <>
          {creditCards.length > 0 && (
            <motion.div variants={itemVariants}>
              <div style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 8 }}>
                Credit Cards
              </div>
              {creditCards.map(card => (
                <AccountRow key={card.id} card={card} onClick={() => navigate(`/accounts/${card.id}`)} />
              ))}
            </motion.div>
          )}

          {bankAccounts.length > 0 && (
            <motion.div variants={itemVariants}>
              <div style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em', margin: '1rem 0 8px' }}>
                Bank Accounts
              </div>
              {bankAccounts.map(card => (
                <AccountRow key={card.id} card={card} onClick={() => navigate(`/accounts/${card.id}`)} />
              ))}
            </motion.div>
          )}

          {personCards.length > 0 && (
            <motion.div variants={itemVariants}>
              <div style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em', margin: '1rem 0 8px' }}>
                Persons
              </div>
              {personCards.map(p => (
                <TiltedCard key={p.accountId} maxTilt={5} glare={false} scale={1.005} perspective={1000}>
                <motion.div
                  className="flow-card"
                  style={{ '--card-accent': 'var(--primary)', marginBottom: '0.75rem' } as React.CSSProperties}
                  variants={itemVariants}
                  whileHover={{ y: -2 }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <div style={{ minWidth: 0 }}>
                      <div className="truncate font-semibold">{p.name}</div>
                      <div className="text-muted text-sm">Person • Used <AnimatedMoney value={p.used} /></div>
                    </div>
                    <div style={{ textAlign: 'right', flexShrink: 0 }}>
                      <div style={{ fontWeight: 700, color: p.due > 0 ? 'var(--warning)' : 'var(--primary)' }}><AnimatedMoney value={p.due} /></div>
                      <div className="text-muted text-xs">{p.due > 0 ? 'Due' : 'Settled'}</div>
                    </div>
                  </div>
                </motion.div>
                </TiltedCard>
              ))}
            </motion.div>
          )}
        </>
      )}
    </motion.div>
  );
}
