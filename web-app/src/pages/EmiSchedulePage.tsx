import React, { useMemo, useEffect, useRef } from 'react';
import { useApp } from '../context/AppContext';
import { formatMoney, formatDate } from '../utils/format';
import { CustomerTransaction } from '../types/models';
import AnimatedMoney from '../components/AnimatedMoney';
import { fadeInUp, staggerFadeInUp } from '../utils/animations';

interface EmiRow {
  groupId: string;
  transactionIds: string[];
  customerName: string;
  planName: string;
  accountName: string;
  emiIndex: number;
  emiTotal: number;
  amount: number;
  transactionDate: string;
  dueDate: string;          // per-installment due date from Firestore
  isSettled: boolean;
  isPast: boolean;          // transaction date ≤ today
  isOverdue: boolean;       // dueDate has passed and not settled
  isDueSoon: boolean;       // due within 20 days and not settled
  daysUntilDue: number | null;
  isCurrent: boolean;
  isSplitInstallment: boolean;
}

export default function EmiSchedulePage() {
  const { customers, toggleTransactionSettled } = useApp();
  const pageRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (pageRef.current) fadeInUp(pageRef.current, 0, 400);
    staggerFadeInUp('.emi-card', 80, 'first', 400);
  }, []);

  const today = useMemo(() => new Date(), []);

  const allRows = useMemo((): EmiRow[] => {
    const result: EmiRow[] = [];

    customers.forEach(customer => {
      const emiGroups = new Map<string, CustomerTransaction[]>();
      customer.transactions
        .filter(t => t.emiGroupId)
        .forEach(t => {
          const key = t.emiGroupId || `${t.name.split(' — EMI')[0]}_${customer.name}`;
          if (!emiGroups.has(key)) emiGroups.set(key, []);
          emiGroups.get(key)!.push(t);
        });

      emiGroups.forEach((instalments, groupKey) => {
        const byIndex = new Map<number, CustomerTransaction[]>();
        instalments.forEach(t => {
          if (!byIndex.has(t.emiIndex)) byIndex.set(t.emiIndex, []);
          byIndex.get(t.emiIndex)!.push(t);
        });

        byIndex.forEach((parts, emiIndex) => {
          const first = parts[0];
          const txnDate = new Date(first.transactionDate);
          const isPast = isNaN(txnDate.getTime()) || txnDate <= today;
          const isCurrent = !isNaN(txnDate.getTime())
            && txnDate.getFullYear() === today.getFullYear()
            && txnDate.getMonth() === today.getMonth();

          // Due date logic
          const dueDate = first.dueDate ?? '';
          const dueDateObj = dueDate ? new Date(dueDate) : null;
          const allSettled = parts.every(p => p.isSettled);

          let isOverdue = false;
          let isDueSoon = false;
          let daysUntilDueVal: number | null = null;

          if (dueDateObj && !isNaN(dueDateObj.getTime()) && !allSettled) {
            const diffMs = dueDateObj.getTime() - today.getTime();
            daysUntilDueVal = Math.ceil(diffMs / (24 * 60 * 60 * 1000));
            isOverdue = daysUntilDueVal < 0;
            isDueSoon = !isOverdue && daysUntilDueVal <= 20;
          }

          const planName = first.name.split(' — EMI')[0].trim();
          const combinedAmount = parts.reduce((s, p) => s + p.amount, 0);
          const isSplit = parts.length > 1;
          const accountName = isSplit
            ? parts.map(p => p.accountName).filter(Boolean).join(', ')
            : first.accountName;

          result.push({
            groupId: groupKey,
            transactionIds: parts.map(p => p.id),
            customerName: customer.name,
            planName,
            accountName,
            emiIndex,
            emiTotal: first.emiTotal,
            amount: combinedAmount,
            transactionDate: first.transactionDate,
            dueDate,
            isSettled: allSettled,
            isPast,
            isOverdue,
            isDueSoon,
            daysUntilDue: daysUntilDueVal,
            isCurrent,
            isSplitInstallment: isSplit,
          });
        });
      });
    });

    // Sort: overdue first, then due-soon, then current, then upcoming, then settled
    return result.sort((a, b) => {
      const priority = (r: EmiRow) => {
        if (r.isSettled) return 4;
        if (r.isOverdue) return 0;
        if (r.isDueSoon) return 1;
        if (r.isCurrent) return 2;
        return 3;
      };
      const pd = priority(a) - priority(b);
      if (pd !== 0) return pd;
      return a.transactionDate.localeCompare(b.transactionDate);
    });
  }, [customers, today]);

  // Group by plan
  const grouped = useMemo(() => {
    const map = new Map<string, EmiRow[]>();
    allRows.forEach(row => {
      if (!map.has(row.groupId)) map.set(row.groupId, []);
      map.get(row.groupId)!.push(row);
    });
    return Array.from(map.entries()).map(([key, rows]) => ({ key, rows }));
  }, [allRows]);

  return (
    <div className="page-content" ref={pageRef}>
      <div style={{ marginBottom: '1.5rem' }}>
        <h2>EMI Schedule</h2>
        <p className="text-muted text-sm" style={{ marginTop: 4 }}>
          All EMI installments across customers.
        </p>
      </div>

      {grouped.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state-icon">📅</div>
          <h3>No EMI plans yet</h3>
          <p>Add EMI transactions from a customer's detail page to see them here.</p>
        </div>
      ) : (
        grouped.map(({ key, rows }) => {
          const first = rows[0];
          const totalAmount = rows.reduce((s, r) => s + r.amount, 0);
          const settledCount = rows.filter(r => r.isSettled).length;
          const overdueCount = rows.filter(r => r.isOverdue).length;
          const dueSoonCount = rows.filter(r => r.isDueSoon).length;

          return (
            <div key={key} className="flow-card emi-card" style={{ marginBottom: '1rem' }}>
              {/* Plan header */}
              <div style={{ marginBottom: '0.75rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                  <div className="font-semibold">{first.planName}</div>
                  {overdueCount > 0 && (
                    <span style={{
                      fontSize: '0.625rem', fontWeight: 700, padding: '2px 7px',
                      borderRadius: 4, background: 'var(--red)', color: '#fff',
                      letterSpacing: '0.04em',
                    }}>
                      {overdueCount} OVERDUE
                    </span>
                  )}
                  {dueSoonCount > 0 && overdueCount === 0 && (
                    <span style={{
                      fontSize: '0.625rem', fontWeight: 700, padding: '2px 7px',
                      borderRadius: 4, background: 'var(--warning)', color: '#fff',
                      letterSpacing: '0.04em',
                    }}>
                      DUE SOON
                    </span>
                  )}
                </div>
                <div className="text-muted text-sm">
                  {first.customerName} • {first.accountName} • {settledCount}/{rows.length} settled
                </div>
                <div className="text-muted text-xs" style={{ marginTop: 2 }}>
                  Total: <AnimatedMoney value={totalAmount} />
                </div>
              </div>

              {/* Progress bar */}
              <div className="progress-bar" style={{ marginBottom: '0.75rem' }}>
                <div
                  className="progress-fill"
                  style={{
                    width: `${(settledCount / rows.length) * 100}%`,
                    background: overdueCount > 0 ? 'var(--red)' : 'var(--primary-deep)',
                  }}
                />
              </div>

              {/* Installment rows */}
              {rows.map(row => {
                // Determine color and label
                const statusColor = row.isSettled
                  ? 'var(--green)'
                  : row.isOverdue
                  ? 'var(--red)'
                  : row.isDueSoon
                  ? 'var(--warning)'
                  : row.isCurrent
                  ? 'var(--primary)'
                  : 'var(--text-muted)';

                const statusLabel = row.isSettled
                  ? '✓ Settled'
                  : row.isOverdue
                  ? `Overdue${row.daysUntilDue !== null ? ` ${Math.abs(row.daysUntilDue)}d` : ''}`
                  : row.isDueSoon
                  ? `Due in ${row.daysUntilDue}d`
                  : row.isCurrent
                  ? 'Current'
                  : 'Upcoming';

                return (
                  <div
                    key={`${row.groupId}_${row.emiIndex}`}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 12,
                      padding: '0.5rem 0',
                      borderBottom: '1px solid var(--outline)',
                      borderLeft: row.isOverdue && !row.isSettled
                        ? '3px solid var(--red)'
                        : row.isDueSoon && !row.isSettled
                        ? '3px solid var(--warning)'
                        : '3px solid transparent',
                      paddingLeft: row.isOverdue || row.isDueSoon ? '0.5rem' : 0,
                    }}
                  >
                    {/* Index circle */}
                    <div style={{
                      width: 28, height: 28, borderRadius: '50%',
                      background: `${statusColor}22`,
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      fontSize: '0.75rem', fontWeight: 700, color: statusColor,
                      flexShrink: 0,
                    }}>
                      {row.emiIndex + 1}
                    </div>

                    {/* Info */}
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div className="text-sm font-semibold" style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        EMI {row.emiIndex + 1}/{row.emiTotal}
                        {row.isSplitInstallment && (
                          <span style={{
                            fontSize: '0.625rem', fontWeight: 600, padding: '1px 6px',
                            borderRadius: 999, background: 'color-mix(in srgb, var(--primary) 12%, transparent)', color: 'var(--primary)',
                          }}>Split</span>
                        )}
                      </div>
                      {/* Transaction date */}
                      <div className="text-muted text-xs">
                        Installment: {formatDate(row.transactionDate)}
                      </div>
                      {/* Due date — always show if present */}
                      {row.dueDate && (
                        <div style={{
                          fontSize: '0.7rem',
                          color: statusColor,
                          fontWeight: row.isOverdue || row.isDueSoon ? 700 : 400,
                          marginTop: 1,
                        }}>
                          Due: {formatDate(row.dueDate)}
                        </div>
                      )}
                    </div>

                    {/* Amount + status */}
                    <div style={{ textAlign: 'right', flexShrink: 0 }}>
                      <div style={{ fontWeight: 700, color: statusColor }}><AnimatedMoney value={row.amount} /></div>
                      <div className="text-xs" style={{ color: statusColor }}>{statusLabel}</div>
                    </div>

                    {/* Settle toggle */}
                    <button
                      className="btn btn-ghost btn-sm"
                      style={{
                        color: row.isSettled ? 'var(--green)' : 'var(--text-muted)',
                        padding: '4px 8px',
                      }}
                      onClick={async () => {
                        const newState = !row.isSettled;
                        await Promise.all(row.transactionIds.map(id => toggleTransactionSettled(id, newState)));
                      }}
                      title={row.isSettled ? 'Mark unsettled' : 'Mark settled'}
                    >
                      {row.isSettled ? '✓' : '○'}
                    </button>
                  </div>
                );
              })}
            </div>
          );
        })
      )}
    </div>
  );
}
