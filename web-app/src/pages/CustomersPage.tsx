import React, { useState, useMemo, useRef, useCallback } from 'react';
import ReactDOM from 'react-dom';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Search, Plus, RefreshCw, Trash2, RotateCcw, Settings, Sparkles } from 'lucide-react';
import { useApp } from '../context/AppContext';
import { formatMoney } from '../utils/format';
import { CustomerSummary } from '../types/models';
import AnimatedAvatar from '../components/AnimatedAvatar';
import AnimatedMoney from '../components/AnimatedMoney';

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.06, delayChildren: 0.08 },
  },
};

const itemVariants = {
  hidden: { opacity: 0, y: 14 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.35, ease: 'easeOut' as const } },
};

function CustomerRow({ customer, onClick, index }: { customer: CustomerSummary; onClick: () => void; index: number }) {
  const txnCount = customer.transactions.length;
  const cardRef = useRef<HTMLDivElement>(null);
  const [tilt, setTilt] = useState({ x: 0, y: 0 });
  const [sparkle, setSparkle] = useState(false);

  const handleMouseMove = useCallback((e: React.MouseEvent) => {
    if (!cardRef.current) return;
    const rect = cardRef.current.getBoundingClientRect();
    const x = (e.clientX - rect.left) / rect.width - 0.5;
    const y = (e.clientY - rect.top) / rect.height - 0.5;
    setTilt({ x: x * 6, y: -y * 6 });
  }, []);

  const handleMouseLeave = useCallback(() => {
    setTilt({ x: 0, y: 0 });
  }, []);

  const handleClick = useCallback((e: React.MouseEvent) => {
    const rect = cardRef.current?.getBoundingClientRect();
    if (rect) {
      setSparkle(true);
      setTimeout(() => setSparkle(false), 500);
    }
    onClick();
  }, [onClick]);

  return (
    <motion.div
      ref={cardRef}
      className="flow-card tilt-card customer-card"
      style={{
        cursor: 'pointer',
        marginBottom: '0.75rem',
        transform: `perspective(800px) rotateX(${tilt.y}deg) rotateY(${tilt.x}deg)`,
        transition: 'transform 0.15s ease, box-shadow 0.15s ease',
        position: 'relative',
        overflow: 'hidden',
      }}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ delay: index * 0.04, duration: 0.35, ease: 'easeOut' }}
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
      onClick={handleClick}
      whileHover={{ boxShadow: '0 4px 20px rgba(0,0,0,0.12)' }}
    >
      {/* Sparkle overlay */}
      {sparkle && (
        <div style={{
          position: 'absolute', inset: 0,
          background: 'radial-gradient(circle at center, color-mix(in srgb, var(--primary) 15%, transparent) 0%, transparent 70%)',
          pointerEvents: 'none',
          animation: 'fadeIn 0.3s ease reverse',
          borderRadius: 'inherit',
        }} />
      )}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <AnimatedAvatar name={customer.name} size={44} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div className="truncate font-semibold" style={{ fontSize: '1rem' }}>{customer.name}</div>
          <div className="text-muted text-sm">
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
              {txnCount} transaction{txnCount !== 1 ? 's' : ''}
              {txnCount > 10 && <Sparkles size={10} style={{ color: 'var(--teal)', opacity: 0.6 }} />}
            </span>
          </div>
          {customer.savingsBalance > 0 && (
            <div style={{ color: 'var(--primary)', fontSize: '0.75rem', fontWeight: 600, marginTop: 2 }}>
              Savings <AnimatedMoney value={customer.savingsBalance} />
            </div>
          )}
        </div>
        <div style={{ textAlign: 'right', flexShrink: 0 }}>
          <div style={{ fontWeight: 700, fontSize: '1.05rem', color: customer.balance > 0 ? 'var(--warning)' : 'var(--primary)' }}>
            <AnimatedMoney value={customer.balance} />
          </div>
          <div className="text-muted text-xs">Balance</div>
        </div>
      </div>
    </motion.div>
  );
}

function DeletedCustomerRow({ customer, onRestore, onDelete, index }: {
  customer: CustomerSummary;
  onRestore: () => void;
  onDelete: () => void;
  index: number;
}) {
  return (
    <motion.div
      className="flow-card hover-lift customer-card"
      style={{ '--card-accent': 'var(--red)', marginBottom: '0.75rem' } as React.CSSProperties}
      initial={{ opacity: 0, x: -12 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ delay: index * 0.04, duration: 0.3, ease: 'easeOut' }}
      whileHover={{ y: -2 }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <div className="avatar" style={{ background: 'color-mix(in srgb, var(--red) 15%, transparent)', color: 'var(--red)', transition: 'all 0.3s ease' }}>
          {customer.name.slice(0, 2).toUpperCase()}
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div className="truncate font-semibold">{customer.name}</div>
          <div className="text-muted text-sm">{customer.transactions.length} transaction(s)</div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="btn btn-sm btn-outline btn-ripple" onClick={onRestore} title="Restore">
            <RotateCcw size={14} />
          </button>
          <button className="btn btn-sm btn-danger btn-ripple" onClick={onDelete} title="Delete forever">
            <Trash2 size={14} />
          </button>
        </div>
      </div>
    </motion.div>
  );
}

export default function CustomersPage() {
  const { customers, deletedCustomers, addCustomer, restoreCustomer, permanentlyDeleteCustomer, syncStatus, triggerSync } = useApp();
  const navigate = useNavigate();
  const listRef = useRef<HTMLDivElement>(null);
  const [search, setSearch] = useState('');
  const [showRecycleBin, setShowRecycleBin] = useState(false);
  const [showAddModal, setShowAddModal] = useState(false);
  const [newName, setNewName] = useState('');
  const [adding, setAdding] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState<CustomerSummary | null>(null);
  const letterRefs = useRef<Record<string, HTMLDivElement | null>>({});

  const activeList = showRecycleBin ? deletedCustomers : customers;

  const sorted = useMemo(() =>
    [...activeList].sort((a, b) => a.name.localeCompare(b.name)),
    [activeList]
  );

  const filtered = useMemo(() =>
    search ? sorted.filter(c => c.name.toLowerCase().includes(search.toLowerCase())) : sorted,
    [sorted, search]
  );

  const letters = useMemo(() => {
    if (search) return [];
    const seen = new Set<string>();
    return filtered.map(c => {
      const l = c.name[0]?.toUpperCase() ?? '#';
      const bucket = /[A-Z]/.test(l) ? l : '#';
      if (!seen.has(bucket)) { seen.add(bucket); return bucket; }
      return null;
    }).filter(Boolean) as string[];
  }, [filtered, search]);

  const handleAdd = async () => {
    if (!newName.trim()) return;
    setAdding(true);
    try {
      const id = await addCustomer(newName);
      setShowAddModal(false);
      setNewName('');
      navigate(`/customers/${id}`);
    } finally {
      setAdding(false);
    }
  };

  const scrollToLetter = (letter: string) => {
    letterRefs.current[letter]?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  // Group by letter
  const grouped = useMemo(() => {
    if (search) return [{ letter: '', items: filtered }];
    const map = new Map<string, CustomerSummary[]>();
    filtered.forEach(c => {
      const l = c.name[0]?.toUpperCase() ?? '#';
      const bucket = /[A-Z]/.test(l) ? l : '#';
      if (!map.has(bucket)) map.set(bucket, []);
      map.get(bucket)!.push(c);
    });
    return Array.from(map.entries()).map(([letter, items]) => ({ letter, items }));
  }, [filtered, search]);

  return (
    <motion.div
      className="page-content"
      style={{ position: 'relative' }}
      variants={containerVariants}
      initial="hidden"
      animate="visible"
    >
      {/* Header */}
      <motion.div
        style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: '1rem' }}
        variants={itemVariants}
      >
        <div>
          <h2 style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            {showRecycleBin ? 'Recycle Bin' : 'Customers'}
            {!showRecycleBin && customers.length > 0 && (
              <span style={{
                fontSize: '0.75rem', fontWeight: 600, color: 'var(--primary)',
                background: 'color-mix(in srgb, var(--primary) 12%, transparent)',
                padding: '2px 10px', borderRadius: 999,
              }}>
                {customers.length}
              </span>
            )}
          </h2>
          <p className="text-muted text-sm" style={{ marginTop: 4 }}>
            {showRecycleBin ? 'Restore or permanently delete customers.' : 'Manage customer ledgers and transactions.'}
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          {syncStatus.message && (
            <span className={`text-xs ${syncStatus.state === 'ERROR' ? 'text-error' : syncStatus.state === 'SUCCESS' ? 'text-success' : 'text-muted'}`}>
              {syncStatus.message}
            </span>
          )}
          <button className="btn btn-ghost btn-ripple" onClick={triggerSync} title="Refresh" style={{ borderRadius: '50%', width: 36, height: 36, padding: 0 }}>
            <RefreshCw size={16} className={syncStatus.state === 'SYNCING' ? 'rotating' : ''} />
          </button>
          <button className="btn btn-sm btn-outline btn-ripple" onClick={() => setShowRecycleBin(v => !v)}>
            {showRecycleBin ? 'Customers' : 'Recycle Bin'}
          </button>
          <button className="btn btn-ghost btn-ripple" onClick={() => navigate('/settings')} style={{ borderRadius: '50%', width: 36, height: 36, padding: 0 }}>
            <Settings size={16} />
          </button>
        </div>
      </motion.div>

      {/* Search */}
      <motion.div style={{ position: 'relative', marginBottom: '1rem' }} variants={itemVariants}>
        <Search size={16} style={{ position: 'absolute', left: 14, top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
        <input
          className="form-input"
          style={{ paddingLeft: 40 }}
          placeholder="Search customers..."
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
      </motion.div>

      {/* List */}
      {filtered.length === 0 ? (
        <motion.div className="empty-state" variants={itemVariants}>
          <div className="empty-state-icon">👥</div>
          <h3>{showRecycleBin ? 'Recycle bin is empty' : 'No customers yet'}</h3>
          <p>{showRecycleBin ? 'Deleted customers will appear here.' : 'Tap + to add your first customer ledger.'}</p>
        </motion.div>
      ) : (
        <div ref={listRef}>
        {grouped.map(({ letter, items }) => (
          <div key={letter || 'all'}>
            {letter && (
              <motion.div
                ref={el => { letterRefs.current[letter] = el; }}
                style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--primary)', padding: '4px 0 2px 4px' }}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ duration: 0.3 }}
              >
                {letter}
              </motion.div>
            )}
            {items.map((customer, idx) => (
              showRecycleBin ? (
                <DeletedCustomerRow
                  key={customer.id}
                  customer={customer}
                  index={idx}
                  onRestore={() => restoreCustomer(customer.id)}
                  onDelete={() => setConfirmDelete(customer)}
                />
              ) : (
                <CustomerRow
                  key={customer.id}
                  customer={customer}
                  index={idx}
                  onClick={() => navigate(`/customers/${customer.id}`)}
                />
              )
            ))}
          </div>
        ))}
        </div>
      )}

      {/* Alphabet index — rendered into document.body via portal so it is truly
          fixed to the viewport and unaffected by the overflow-y:auto scroll
          container in main-content */}
      {!showRecycleBin && !search && letters.length > 0 && ReactDOM.createPortal(
        <div style={{
          position: 'fixed',
          right: 6,
          top: '50%',
          transform: 'translateY(-50%)',
          zIndex: 200,
          display: 'flex',
          flexDirection: 'column',
          gap: 1,
          pointerEvents: 'auto',
        }}>
          {letters.map(l => (
            <div
              key={l}
              onClick={() => scrollToLetter(l)}
              style={{
                fontSize: '0.625rem',
                fontWeight: 700,
                color: 'var(--text-muted)',
                cursor: 'pointer',
                padding: '1px 4px',
                borderRadius: 4,
                lineHeight: 1.4,
                userSelect: 'none',
              }}
              onMouseEnter={e => {
                (e.currentTarget as HTMLDivElement).style.background = 'var(--bg-soft)';
                (e.currentTarget as HTMLDivElement).style.color = 'var(--text)';
              }}
              onMouseLeave={e => {
                (e.currentTarget as HTMLDivElement).style.background = 'transparent';
                (e.currentTarget as HTMLDivElement).style.color = 'var(--text-muted)';
              }}
            >
              {l}
            </div>
          ))}
        </div>,
        document.body
      )}

      {/* FAB — also rendered into document.body via portal so it is truly fixed
          to the viewport and never overlaps the customer list */}
      {!showRecycleBin && ReactDOM.createPortal(
        <motion.button
          onClick={() => setShowAddModal(true)}
          style={{
            position: 'fixed',
            bottom: 96,
            right: 24,
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            padding: '0.875rem 1.25rem',
            borderRadius: 20,
            background: 'var(--gradient-primary)',
            color: '#FFFFFF',
            border: 'none',
            fontFamily: 'var(--font)',
            fontSize: '0.9375rem',
            fontWeight: 600,
            cursor: 'pointer',
            boxShadow: '0 4px 20px rgba(99, 102, 241, 0.35)',
            zIndex: 50,
          }}
          initial={{ scale: 0, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ delay: 0.3, type: 'spring', stiffness: 400, damping: 20 }}
          whileHover={{ scale: 1.05, boxShadow: '0 6px 24px rgba(99, 102, 241, 0.45)' }}
          whileTap={{ scale: 0.95 }}
        >
          <Plus size={18} /> Add Customer
        </motion.button>,
        document.body
      )}

      {/* Add modal */}
      {showAddModal && (
        <div className="modal-overlay" onClick={() => setShowAddModal(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h3 className="modal-title">Add Customer</h3>
            <p className="modal-subtitle">Enter the customer's name to create a new ledger.</p>
            <div className="form-group">
              <label className="form-label">Customer Name</label>
              <input
                className="form-input"
                value={newName}
                onChange={e => setNewName(e.target.value)}
                placeholder="Enter name"
                autoFocus
                onKeyDown={e => e.key === 'Enter' && handleAdd()}
              />
            </div>
            <div className="modal-actions">
              <button className="btn btn-outline" onClick={() => setShowAddModal(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={handleAdd} disabled={!newName.trim() || adding}>
                {adding ? 'Adding...' : 'Add Customer'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Confirm delete */}
      {confirmDelete && (
        <div className="modal-overlay" onClick={() => setConfirmDelete(null)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h3 className="modal-title">Delete Forever?</h3>
            <p className="modal-subtitle">
              This will permanently delete <strong>{confirmDelete.name}</strong> and all their transactions. This cannot be undone.
            </p>
            <div className="modal-actions">
              <button className="btn btn-outline" onClick={() => setConfirmDelete(null)}>Cancel</button>
              <button
                className="btn btn-danger"
                onClick={async () => {
                  await permanentlyDeleteCustomer(confirmDelete.id, confirmDelete.name);
                  setConfirmDelete(null);
                }}
              >
                Delete Forever
              </button>
            </div>
          </div>
        </div>
      )}
    </motion.div>
  );
}
