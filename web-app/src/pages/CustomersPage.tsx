import React, { useState, useMemo, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, Plus, RefreshCw, Trash2, RotateCcw, Settings, Sparkles } from 'lucide-react';
import { useApp } from '../context/AppContext';
import { formatMoney } from '../utils/format';
import { CustomerSummary } from '../types/models';
import AnimatedAvatar from '../components/AnimatedAvatar';

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
    // Create a ripple-like effect
    const rect = cardRef.current?.getBoundingClientRect();
    if (rect) {
      setSparkle(true);
      setTimeout(() => setSparkle(false), 500);
    }
    onClick();
  }, [onClick]);

  return (
    <div
      ref={cardRef}
      className={`flow-card tilt-card stagger-${Math.min(index + 1, 10)}`}
      style={{
        cursor: 'pointer',
        marginBottom: '0.75rem',
        transform: `perspective(800px) rotateX(${tilt.y}deg) rotateY(${tilt.x}deg)`,
        transition: 'transform 0.15s ease, box-shadow 0.15s ease',
        position: 'relative',
        overflow: 'hidden',
      }}
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
      onClick={handleClick}
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
        </div>
        <div style={{ textAlign: 'right', flexShrink: 0 }}>
          <div style={{ fontWeight: 700, fontSize: '1.05rem', color: customer.balance > 0 ? 'var(--warning)' : 'var(--primary)' }}>
            {formatMoney(customer.balance)}
          </div>
          <div className="text-muted text-xs">Balance</div>
        </div>
      </div>
    </div>
  );
}

function DeletedCustomerRow({ customer, onRestore, onDelete, index }: {
  customer: CustomerSummary;
  onRestore: () => void;
  onDelete: () => void;
  index: number;
}) {
  return (
    <div className={`flow-card hover-lift stagger-${Math.min(index + 1, 10)}`} style={{ '--card-accent': 'var(--red)', marginBottom: '0.75rem' } as React.CSSProperties}>
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
    </div>
  );
}

export default function CustomersPage() {
  const { customers, deletedCustomers, addCustomer, restoreCustomer, permanentlyDeleteCustomer, syncStatus, triggerSync } = useApp();
  const navigate = useNavigate();
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
    <div className="page-content" style={{ position: 'relative' }}>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: '1rem' }}>
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
          <button className="btn btn-ghost btn-ripple" onClick={triggerSync} title="Sync" style={{ borderRadius: '50%', width: 36, height: 36, padding: 0 }}>
            <RefreshCw size={16} className={syncStatus.state === 'SYNCING' ? 'rotating' : ''} />
          </button>
          <button className="btn btn-sm btn-outline btn-ripple" onClick={() => setShowRecycleBin(v => !v)}>
            {showRecycleBin ? 'Customers' : 'Recycle Bin'}
          </button>
          <button className="btn btn-ghost btn-ripple" onClick={() => navigate('/settings')} style={{ borderRadius: '50%', width: 36, height: 36, padding: 0 }}>
            <Settings size={16} />
          </button>
        </div>
      </div>

      {/* Search */}
      <div style={{ position: 'relative', marginBottom: '1rem' }}>
        <Search size={16} style={{ position: 'absolute', left: 14, top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
        <input
          className="form-input"
          style={{ paddingLeft: 40 }}
          placeholder="Search customers..."
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
      </div>

      {/* List */}
      {filtered.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state-icon">👥</div>
          <h3>{showRecycleBin ? 'Recycle bin is empty' : 'No customers yet'}</h3>
          <p>{showRecycleBin ? 'Deleted customers will appear here.' : 'Tap + to add your first customer ledger.'}</p>
        </div>
      ) : (
        grouped.map(({ letter, items }) => (
          <div key={letter || 'all'}>
            {letter && (
              <div
                ref={el => { letterRefs.current[letter] = el; }}
                style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--primary)', padding: '4px 0 2px 4px' }}
              >
                {letter}
              </div>
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
        ))
      )}

      {/* Alphabet index */}
      {!showRecycleBin && !search && letters.length > 0 && (
        <div className="alpha-index">
          {letters.map(l => (
            <div key={l} className="alpha-index-letter" onClick={() => scrollToLetter(l)}>{l}</div>
          ))}
        </div>
      )}

      {/* FAB */}
      {!showRecycleBin && (
        <button
          className="btn btn-primary glow-pulse"
          style={{
            position: 'fixed', bottom: 80, right: 24,
            borderRadius: 20, padding: '0.875rem 1.25rem',
            boxShadow: '0 4px 20px color-mix(in srgb, var(--primary) 40%, transparent)',
            zIndex: 50,
            '--glow-color': 'var(--primary)',
          } as React.CSSProperties}
          onClick={() => setShowAddModal(true)}
        >
          <Plus size={18} /> Add Customer
        </button>
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
    </div>
  );
}
