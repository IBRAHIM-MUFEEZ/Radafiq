import React, { useState, useEffect, useRef } from 'react';
import { motion } from 'framer-motion';
import { useApp } from '../context/AppContext';
import RadafiqLogo from '../components/RadafiqLogo';
import { isPlatformAuthenticatorAvailable } from '../utils/passkey';

const pageVariants = {
  hidden: { opacity: 0, scale: 0.97 },
  visible: { opacity: 1, scale: 1, transition: { duration: 0.4, ease: 'easeOut' as const } },
};

const logoVariants = {
  hidden: { opacity: 0, scale: 0.85 },
  visible: { opacity: 1, scale: 1, transition: { duration: 0.5, ease: 'easeOut' as const } },
};

const padVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.4, delay: 0.2, ease: 'easeOut' as const } },
};

const dotVariants = {
  hidden: { scale: 0 },
  visible: { scale: 1, transition: { type: 'spring' as const, stiffness: 500, damping: 15 } },
};

export default function AppLock() {
  const { security, verifyPasscode, resetPasscodeWithRecovery, hasPasskey, authenticateWithPasskey } = useApp();
  const [passcode, setPasscode] = useState('');
  const [error, setError] = useState('');
  const [checking, setChecking] = useState(false);
  const [showRecovery, setShowRecovery] = useState(false);
  const [recoveryAnswer, setRecoveryAnswer] = useState('');
  const [newPasscode, setNewPasscode] = useState('');
  const [confirmNew, setConfirmNew] = useState('');
  const [passkeyAvailable, setPasskeyAvailable] = useState(false);
  const [passkeyChecking, setPasskeyChecking] = useState(false);

  useEffect(() => {
    if (hasPasskey) {
      isPlatformAuthenticatorAvailable().then(setPasskeyAvailable);
    }
  }, [hasPasskey]);

  useEffect(() => {
    if (passkeyAvailable && hasPasskey && !error) {
      handlePasskeyAuth();
    }
  }, [passkeyAvailable]);

  const handlePasskeyAuth = async () => {
    if (passkeyChecking) return;
    setPasskeyChecking(true);
    setError('');
    try {
      const ok = await authenticateWithPasskey();
      if (!ok) setError('Biometric authentication failed. Use your passcode instead.');
    } catch {
      setError('Biometric authentication was cancelled or failed.');
    } finally {
      setPasskeyChecking(false);
    }
  };

  const verify = async (code: string) => {
    setChecking(true);
    setError('');
    const ok = await verifyPasscode(code);
    if (!ok) {
      setError('Incorrect passcode. Please try again.');
      setPasscode('');
    }
    setChecking(false);
  };

  const pressDigit = (digit: string) => {
    if (checking || passcode.length >= 6) return;
    const next = passcode + digit;
    setPasscode(next);
    setError('');
    if (next.length === 6) verify(next);
  };

  const pressBackspace = () => {
    if (checking) return;
    setPasscode(p => p.slice(0, -1));
    setError('');
  };

  useEffect(() => {
    if (showRecovery) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.altKey || event.ctrlKey || event.metaKey) return;

      const isDigit = event.key.length === 1 && event.key >= '0' && event.key <= '9';
      if (isDigit) {
        event.preventDefault();
        pressDigit(event.key);
        return;
      }

      if (event.key === 'Backspace' || event.key === 'Delete') {
        event.preventDefault();
        pressBackspace();
        return;
      }

      if (event.key === 'Enter' && passcode.length === 6 && !checking) {
        event.preventDefault();
        verify(passcode);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [checking, passcode, showRecovery]);

  const handleRecovery = async () => {
    if (!recoveryAnswer.trim() || newPasscode.length !== 6 || newPasscode !== confirmNew) return;
    setChecking(true);
    setError('');
    const ok = await resetPasscodeWithRecovery(recoveryAnswer, newPasscode);
    if (!ok) setError('Recovery answer is incorrect.');
    setChecking(false);
  };

  const KEYS = [
    '1', '2', '3',
    '4', '5', '6',
    '7', '8', '9',
    '',  '0', '⌫',
  ];

  const page: React.CSSProperties = {
    width: '100vw',
    minHeight: '100vh',
    background: 'linear-gradient(160deg, var(--bg-deep) 0%, var(--bg-soft) 60%, var(--bg-soft) 100%)',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '2rem 1rem',
    boxSizing: 'border-box',
    fontFamily: "'Tonus Text Semi Bold', system-ui, sans-serif",
  };

  const card: React.CSSProperties = {
    width: '100%',
    maxWidth: 340,
    textAlign: 'center',
  };

  const numpadBtn = (disabled: boolean, isBack: boolean): React.CSSProperties => ({
    height: 64,
    width: '100%',
    fontSize: isBack ? '1.5rem' : '1.375rem',
    fontWeight: 700,
    borderRadius: 16,
    border: '1.5px solid color-mix(in srgb, var(--primary) 30%, transparent)',
    background: 'color-mix(in srgb, var(--surface) 90%, transparent)',
    color: isBack ? 'var(--text-muted)' : 'var(--text)',
    cursor: disabled ? 'default' : 'pointer',
    opacity: disabled ? 0.3 : 1,
    outline: 'none',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    userSelect: 'none' as const,
    WebkitUserSelect: 'none' as const,
    touchAction: 'manipulation' as const,
    transition: 'background 0.1s, transform 0.1s',
    boxSizing: 'border-box' as const,
  });

  if (showRecovery) {
    return (
      <motion.div style={page} variants={pageVariants} initial="hidden" animate="visible">
        <div style={{ ...card, maxWidth: 400 }}>
          <div style={{ marginBottom: '1.5rem' }}>
            <h2 style={{ color: 'var(--text)', marginBottom: 6 }}>Forgot Passcode</h2>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem' }}>
              Answer your recovery question to reset your passcode.
            </p>
          </div>

          <motion.div
            style={{ background: 'color-mix(in srgb, var(--surface) 90%, transparent)', border: '1px solid color-mix(in srgb, var(--primary) 25%, transparent)', borderRadius: 20, padding: '1.25rem', marginBottom: '1rem' }}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.15, duration: 0.3 }}
          >
            <p style={{ color: 'var(--text)', fontWeight: 600, fontSize: '0.9375rem' }}>{security.recoveryQuestion}</p>
          </motion.div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.875rem' }}>
            {[
              { label: 'Your Answer', val: recoveryAnswer, set: setRecoveryAnswer, type: 'text' },
              { label: 'New Passcode (6 digits)', val: newPasscode, set: (v: string) => setNewPasscode(v.replace(/\D/g,'').slice(0,6)), type: 'password' },
              { label: 'Confirm New Passcode', val: confirmNew, set: (v: string) => setConfirmNew(v.replace(/\D/g,'').slice(0,6)), type: 'password' },
            ].map(({ label, val, set, type }) => (
              <div key={label} style={{ textAlign: 'left' }}>
                <label style={{ display: 'block', fontSize: '0.8125rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: 6 }}>{label}</label>
                <input
                  type={type}
                  value={val}
                  onChange={e => set(e.target.value)}
                  style={{ width: '100%', background: 'color-mix(in srgb, var(--surface) 90%, transparent)', border: '1.5px solid color-mix(in srgb, var(--primary) 30%, transparent)', borderRadius: 12, padding: '0.75rem 1rem', color: 'var(--text)', fontSize: '0.9375rem', outline: 'none', boxSizing: 'border-box' }}
                />
              </div>
            ))}

            {error && <p style={{ color: '#EF4444', fontSize: '0.875rem' }}>{error}</p>}

            <button
              onClick={handleRecovery}
              disabled={checking || !recoveryAnswer.trim() || newPasscode.length !== 6 || newPasscode !== confirmNew}
              style={{ ...numpadBtn(checking || !recoveryAnswer.trim() || newPasscode.length !== 6 || newPasscode !== confirmNew, false), height: 48, background: 'var(--primary)', color: '#fff', borderRadius: 12, fontSize: '0.9375rem' }}
            >
              {checking ? 'Verifying...' : 'Reset Passcode'}
            </button>

            <button
              onClick={() => { setShowRecovery(false); setError(''); }}
              style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '0.875rem', padding: '0.5rem' }}
            >
              {'←'} Back to PIN
            </button>
          </div>
        </div>
      </motion.div>
    );
  }

  return (
    <motion.div style={page} variants={pageVariants} initial="hidden" animate="visible">
      <div style={card}>
        <motion.div style={{ display: 'flex', justifyContent: 'center', marginBottom: '1.25rem' }} variants={logoVariants}>
          <RadafiqLogo size={80} />
        </motion.div>

        <motion.h2
          style={{ color: 'var(--text)', marginBottom: 6, fontSize: '1.5rem', fontWeight: 700 }}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1, duration: 0.3 }}
        >
          Radafiq is Locked
        </motion.h2>
        <motion.p
          style={{ color: 'var(--text-muted)', fontSize: '0.875rem', marginBottom: '1.75rem' }}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.15, duration: 0.3 }}
        >
          Enter your 6-digit passcode to continue.
        </motion.p>

        <motion.div
          style={{ display: 'flex', justifyContent: 'center', gap: 14, marginBottom: '1.25rem' }}
          variants={padVariants}
        >
          {Array.from({ length: 6 }, (_, i) => (
            <motion.div
              key={i}
              style={{
                width: 14, height: 14, borderRadius: '50%', flexShrink: 0,
                background: i < passcode.length ? 'var(--primary)' : 'color-mix(in srgb, var(--primary) 25%, transparent)',
                transition: 'background 0.12s',
              }}
              variants={dotVariants}
              animate={i < passcode.length ? { scale: [0, 1.2, 1] } : undefined}
              transition={{ duration: 0.2 }}
            />
          ))}
        </motion.div>

        <div style={{ minHeight: 24, marginBottom: '0.75rem' }}>
          {error && (
            <motion.p
              style={{ color: '#EF4444', fontSize: '0.875rem' }}
              initial={{ opacity: 0, x: -5 }}
              animate={{ opacity: 1, x: 0 }}
            >
              {error}
            </motion.p>
          )}
        </div>

        <motion.div
          variants={padVariants}
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(3, 1fr)',
            gap: 10,
            width: '100%',
            maxWidth: 300,
            margin: '0 auto 1.5rem',
          }}
        >
          {KEYS.map((key, i) => {
            if (key === '') return <div key={i} />;
            const isBack = key === '⌫';
            const disabled = checking || (isBack ? passcode.length === 0 : passcode.length >= 6);
            return (
              <motion.button
                key={i}
                type="button"
                aria-label={isBack ? 'Backspace' : `Digit ${key}`}
                style={numpadBtn(disabled, isBack)}
                disabled={disabled}
                onClick={() => {
                  if (isBack) pressBackspace();
                  else pressDigit(key);
                }}
                whileTap={disabled ? {} : { scale: 0.93 }}
                whileHover={disabled ? {} : { background: 'color-mix(in srgb, var(--primary) 15%, transparent)' }}
                transition={{ type: 'spring', stiffness: 400, damping: 20 }}
              >
                {key}
              </motion.button>
            );
          })}
        </motion.div>

        {security.hasRecoveryQuestion && (
          <button
            type="button"
            onClick={() => { setShowRecovery(true); setError(''); setPasscode(''); }}
            style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--primary)', fontSize: '0.875rem', fontWeight: 500, padding: '0.5rem' }}
          >
            Forgot passcode?
          </button>
        )}

        {hasPasskey && passkeyAvailable && (
          <button
            type="button"
            onClick={handlePasskeyAuth}
            disabled={passkeyChecking}
            style={{
              marginTop: 8,
              background: 'color-mix(in srgb, var(--primary) 12%, transparent)',
              border: '1.5px solid color-mix(in srgb, var(--primary) 35%, transparent)',
              borderRadius: 14,
              color: passkeyChecking ? 'var(--text-muted)' : 'var(--primary)',
              cursor: passkeyChecking ? 'default' : 'pointer',
              fontSize: '0.9375rem',
              fontWeight: 600,
              padding: '0.75rem 1.5rem',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 8,
              width: '100%',
              maxWidth: 300,
              margin: '8px auto 0',
              transition: 'opacity 0.15s',
              opacity: passkeyChecking ? 0.6 : 1,
            }}
          >
            <span style={{ fontSize: '1.25rem', lineHeight: 1 }}>
              {passkeyChecking ? '⏳' : '🔑'}
            </span>
            {passkeyChecking ? 'Verifying...' : 'Use Fingerprint / Face ID'}
          </button>
        )}
      </div>
    </motion.div>
  );
}
