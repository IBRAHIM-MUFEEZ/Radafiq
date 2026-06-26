import React, { useMemo, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowLeft } from 'lucide-react';
import { useApp } from '../context/AppContext';
import { formatMoney, formatDate } from '../utils/format';
import { isVisibleInTransactions } from '../types/models';
import AnimatedMoney from '../components/AnimatedMoney';

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

export default function AccountDetail() {
  const { accountId } = useParams<{ accountId: string }>();
  const navigate = useNavigate();
  const { cards, customers, updateCreditCardDue, addPayment } = useApp();

  const card = cards.find(c => c.id === accountId);
  const [showDueEditor, setShowDueEditor] = useState(false);
  const [showPaymentEditor, setShowPaymentEditor] = useState(false);
  const [dueAmount, setDueAmount] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [remindersEnabled, setRemindersEnabled] = useState(false);
  const [reminderEmail, setReminderEmail] = useState('');
  const [reminderWhatsApp, setReminderWhatsApp] = useState('');
  const [paymentAmount, setPaymentAmount] = useState('');

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
  const remainingDue = card.dueAmount - card.pending;
  const progress = card.dueAmount > 0 ? Math.min(1, card.pending / card.dueAmount) : 0;

  const openDueEditor = () => {
    setDueAmount(card.dueAmount > 0 ? String(card.dueAmount) : '');
    setDueDate(card.dueDate);
    setRemindersEnabled(card.remindersEnabled);
    setReminderEmail(card.reminderEmail);
    setReminderWhatsApp(card.reminderWhatsApp);
    setShowDueEditor(true);
  };

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
      <motion.div
        className="flow-card"
        style={{ '--card-accent': accentColor, marginBottom: '1rem' } as React.CSSProperties}
        variants={itemVariants}
        whileHover={{ y: -1 }}
      >
        <h2 style={{ marginBottom: 4 }}>{card.name}</h2>
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

        {card.accountKind === 'credit_card' && (
          <>
            <div className="divider" />
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
              <div>
                <div className="font-semibold text-sm">Credit Card Due</div>
                <div className="text-muted text-xs">{card.dueDate ? `Due ${card.dueDate}` : 'No due date set'}</div>
              </div>
              <div style={{ display: 'flex', gap: 8 }}>
                <button className="btn btn-sm btn-outline" onClick={() => { setPaymentAmount(''); setShowPaymentEditor(true); }}>
                  Add Payment
                </button>
                <button className="btn btn-sm btn-outline" onClick={openDueEditor}>
                  Edit Due
                </button>
              </div>
            </div>

            {card.dueAmount > 0 && (
              <>
                <div className="two-col" style={{ marginBottom: '0.75rem' }}>
                  <div className="metric-pill">
                    <span className="label">Due Amount</span>
                    <span className="value" style={{ color: remainingDue > 0 ? 'var(--warning)' : 'var(--green)' }}><AnimatedMoney value={card.dueAmount} /></span>
                  </div>
                  <div className="metric-pill">
                    <span className="label">Remaining</span>
                    <span className="value" style={{ color: remainingDue > 0 ? 'var(--warning)' : 'var(--green)' }}><AnimatedMoney value={Math.abs(remainingDue)} /></span>
                  </div>
                </div>
                <div className="progress-bar" style={{ marginBottom: 8 }}>
                  <div className="progress-fill" style={{ width: `${progress * 100}%`, background: remainingDue > 0 ? 'var(--warning)' : 'var(--green)' }} />
                </div>
                <p className="text-sm" style={{ color: remainingDue > 0 ? 'var(--warning)' : 'var(--green)', fontWeight: 600 }}>
                  {remainingDue > 0
                    ? `You still need to pay ${formatMoney(remainingDue)} on this credit card.`
                    : remainingDue < 0
                    ? `You have overpaid ${formatMoney(Math.abs(remainingDue))} on this credit card.`
                    : 'This credit card due is fully covered.'}
                </p>
              </>
            )}
          </>
        )}
      </motion.div>

      {/* Customers using this account */}
      {accountCustomers.length > 0 && (
        <>
          <motion.h3 style={{ marginBottom: '0.75rem' }} variants={itemVariants}>Customers</motion.h3>
          {accountCustomers.map(({ customer, used, due }, i) => (
            <motion.div
              key={customer.id}
              className="flow-card"
              style={{ cursor: 'pointer', marginBottom: '0.75rem' }}
              onClick={() => navigate(`/customers/${customer.id}`)}
              variants={itemVariants}
              whileHover={{ y: -2 }}
              whileTap={{ scale: 0.99 }}
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
          ))}
        </>
      )}

      {/* Edit Due Modal */}
      {showDueEditor && (
        <div className="modal-overlay" onClick={() => setShowDueEditor(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h3 className="modal-title">Edit {card.name} Due</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.875rem' }}>
              <div className="form-group">
                <label className="form-label">Credit Card Due Amount</label>
                <input className="form-input" type="number" value={dueAmount} onChange={e => setDueAmount(e.target.value)} placeholder="0.00" />
              </div>
              <div className="form-group">
                <label className="form-label">Due Date (YYYY-MM-DD)</label>
                <input className="form-input" type="date" value={dueDate} onChange={e => setDueDate(e.target.value)} />
              </div>
              <div className="toggle-row">
                <div className="toggle-info">
                  <div className="toggle-title">Daily due reminders</div>
                  <div className="toggle-subtitle">One reminder per day from 5 days before the due date.</div>
                </div>
                <label className="switch">
                  <input type="checkbox" checked={remindersEnabled} onChange={e => setRemindersEnabled(e.target.checked)} />
                  <span className="switch-track" />
                </label>
              </div>
              {remindersEnabled && (
                <>
                  <div className="form-group">
                    <label className="form-label">Reminder Email</label>
                    <input className="form-input" type="email" value={reminderEmail} onChange={e => setReminderEmail(e.target.value)} placeholder="email@example.com" />
                  </div>
                  <div className="form-group">
                    <label className="form-label">WhatsApp Number</label>
                    <input className="form-input" type="tel" value={reminderWhatsApp} onChange={e => setReminderWhatsApp(e.target.value)} placeholder="+91..." />
                  </div>
                </>
              )}
            </div>
            <div className="modal-actions">
              <button className="btn btn-outline" onClick={() => setShowDueEditor(false)}>Cancel</button>
              <button
                className="btn btn-primary"
                onClick={async () => {
                  await updateCreditCardDue({ accountId: card.id, accountName: card.name, amount: dueAmount, dueDate, remindersEnabled, reminderEmail, reminderWhatsApp });
                  setShowDueEditor(false);
                }}
                disabled={dueAmount === '' || !dueDate}
              >
                Save
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Add Payment Modal */}
      {showPaymentEditor && (
        <div className="modal-overlay" onClick={() => setShowPaymentEditor(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h3 className="modal-title">Add Payment to {card.name}</h3>
            <div className="form-group" style={{ marginBottom: '1rem' }}>
              <label className="form-label">Payment Amount</label>
              <input className="form-input" type="number" value={paymentAmount} onChange={e => setPaymentAmount(e.target.value)} placeholder="0.00" autoFocus />
            </div>
            <div className="modal-actions">
              <button className="btn btn-outline" onClick={() => setShowPaymentEditor(false)}>Cancel</button>
              <button
                className="btn btn-primary"
                onClick={async () => {
                  await addPayment(card.id, card.name, card.accountKind, paymentAmount);
                  setShowPaymentEditor(false);
                }}
                disabled={!paymentAmount || parseFloat(paymentAmount) <= 0}
              >
                Add Payment
              </button>
            </div>
          </div>
        </div>
      )}
    </motion.div>
  );
}
