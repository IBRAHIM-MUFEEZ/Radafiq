import React, { useState, useRef, useEffect } from 'react';
import { useApp } from '../context/AppContext';
import { ALL_ACCOUNTS, BANK_ACCOUNTS, CREDIT_CARDS } from '../types/models';
import { currentTimestampLabel } from '../utils/format';
import { isPlatformAuthenticatorAvailable } from '../utils/passkey';

const RECOVERY_QUESTIONS = [
  'What is your email ID?',
  "What was your first pet's name?",
  'What city were you born in?',
  "What is your mother's first name?",
];

export default function SettingsPage() {
  const {
    profile, saveProfile, signOut,
    settings, setThemeMode, setAccountSelected,
    security, setPasscode, updatePasscode, clearPasscode, setLockEnabled,
    hasPasskey, registerPasskey, removePasskey,
    exportBackupToFile, importBackupFromFile, backupStatusMessage, backupInProgress,
  } = useApp();

  const [editProfile, setEditProfile] = useState(false);
  const [displayName, setDisplayName] = useState(profile?.displayName ?? '');
  const [businessName, setBusinessName] = useState(profile?.businessName ?? '');
  const [email, setEmail] = useState(profile?.email ?? '');
  const [savingProfile, setSavingProfile] = useState(false);

  const [showPasscodeSetup, setShowPasscodeSetup] = useState(false);
  const [showChangePasscode, setShowChangePasscode] = useState(false);
  const [passcode, setPasscodeVal] = useState('');
  const [confirmPasscode, setConfirmPasscode] = useState('');
  const [currentPasscode, setCurrentPasscode] = useState('');
  const [recoveryQuestion, setRecoveryQuestion] = useState(RECOVERY_QUESTIONS[0]);
  const [recoveryAnswer, setRecoveryAnswer] = useState('');
  const [passcodeError, setPasscodeError] = useState('');
  const [savingPasscode, setSavingPasscode] = useState(false);

  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);
  const [showAccountsConfig, setShowAccountsConfig] = useState(false);
  const [showRemovePasscodeConfirm, setShowRemovePasscodeConfirm] = useState(false);

  const [passkeySupported, setPasskeySupported] = useState(false);
  const [passkeyRegistering, setPasskeyRegistering] = useState(false);
  const [passkeyError, setPasskeyError] = useState('');

  // Check platform authenticator support on mount
  useEffect(() => {
    isPlatformAuthenticatorAvailable().then(setPasskeySupported);
  }, []);

  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleSaveProfile = async () => {
    setSavingProfile(true);
    try {
      // BUG-60 fix: preserve existing photoUrl — don't clear it on profile edit
      await saveProfile(displayName, businessName, email, profile?.photoUrl ?? '');
      setEditProfile(false);
    } finally {
      setSavingProfile(false);
    }
  };

  const handleSetPasscode = async () => {
    if (passcode.length !== 6 || passcode !== confirmPasscode || !recoveryAnswer.trim()) return;
    setSavingPasscode(true);
    setPasscodeError('');
    try {
      await setPasscode(passcode, recoveryQuestion, recoveryAnswer);
      setShowPasscodeSetup(false);
      setPasscodeVal('');
      setConfirmPasscode('');
      setRecoveryAnswer('');
    } finally {
      setSavingPasscode(false);
    }
  };

  const handleChangePasscode = async () => {
    if (!currentPasscode || passcode.length !== 6 || passcode !== confirmPasscode || !recoveryAnswer.trim()) return;
    setSavingPasscode(true);
    setPasscodeError('');
    try {
      const ok = await updatePasscode(currentPasscode, passcode, recoveryQuestion, recoveryAnswer);
      if (!ok) {
        setPasscodeError('Current passcode is incorrect.');
      } else {
        setShowChangePasscode(false);
        setPasscodeVal('');
        setConfirmPasscode('');
        setCurrentPasscode('');
        setRecoveryAnswer('');
      }
    } finally {
      setSavingPasscode(false);
    }
  };

  const handleImport = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) importBackupFromFile(file);
    e.target.value = '';
  };

  const handleRegisterPasskey = async () => {
    setPasskeyRegistering(true);
    setPasskeyError('');
    try {
      await registerPasskey();
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : 'Registration failed.';
      // User cancelled — don't show an error
      if (!msg.toLowerCase().includes('cancel') && !msg.toLowerCase().includes('abort')) {
        setPasskeyError(msg);
      }
    } finally {
      setPasskeyRegistering(false);
    }
  };

  return (
    <div className="page-content">
      <h2 style={{ marginBottom: '0.5rem' }}>Settings</h2>
      <p className="text-muted text-sm" style={{ marginBottom: '1.5rem' }}>
        Manage profile, security, backups, and account configuration.
      </p>

      {/* Profile */}
      <div className="flow-card" style={{ marginBottom: '1rem' }}>
        <h3 style={{ marginBottom: '0.75rem' }}>Profile</h3>
        {editProfile ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.875rem' }}>
            <div className="form-group">
              <label className="form-label">Your Name</label>
              <input className="form-input" value={displayName} onChange={e => setDisplayName(e.target.value)} />
            </div>
            <div className="form-group">
              <label className="form-label">Business / Shop Name</label>
              <input className="form-input" value={businessName} onChange={e => setBusinessName(e.target.value)} />
            </div>
            <div className="form-group">
              <label className="form-label">Email</label>
              <input className="form-input" type="email" value={email} onChange={e => setEmail(e.target.value)} />
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <button className="btn btn-outline" onClick={() => setEditProfile(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={handleSaveProfile} disabled={savingProfile || !displayName.trim() || !businessName.trim()}>
                {savingProfile ? 'Saving...' : 'Save Profile'}
              </button>
            </div>
          </div>
        ) : (
          <>
            <div className="font-semibold" style={{ fontSize: '1rem' }}>{profile?.displayName || 'Profile not set up'}</div>
            <div className="text-muted text-sm" style={{ marginTop: 2 }}>{profile?.businessName || 'Add your business details'}</div>
            <button className="btn btn-primary" style={{ marginTop: '0.875rem', display: 'inline-block' }} onClick={() => {
              setDisplayName(profile?.displayName ?? '');
              setBusinessName(profile?.businessName ?? '');
              setEmail(profile?.email ?? '');
              setEditProfile(true);
            }}>
              Edit Profile
            </button>
          </>
        )}
      </div>

      {/* Security */}
      <div className="flow-card" style={{ '--card-accent': 'var(--secondary)', marginBottom: '1rem' } as React.CSSProperties}>
        <h3 style={{ marginBottom: '0.875rem' }}>Security</h3>

        <div className="toggle-row" style={{ marginBottom: 8 }}>
          <div className="toggle-info">
            <div className="toggle-title">App Lock</div>
            <div className="toggle-subtitle">
              {security.hasPasscode ? 'Require a passcode when the app is reopened.' : 'Create a passcode to enable app lock.'}
            </div>
          </div>
          <label className="switch">
            <input
              type="checkbox"
              checked={security.lockEnabled}
              disabled={!security.hasPasscode}
              onChange={e => setLockEnabled(e.target.checked)}
            />
            <span className="switch-track" />
          </label>
        </div>

        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: '0.875rem' }}>
          <button
            className="btn btn-primary"
            onClick={() => {
              if (security.hasPasscode) {
                setCurrentPasscode('');
                setPasscodeVal('');
                setConfirmPasscode('');
                setRecoveryAnswer('');
                setPasscodeError('');
                setShowChangePasscode(true);
              } else {
                setPasscodeVal('');
                setConfirmPasscode('');
                setRecoveryAnswer('');
                setPasscodeError('');
                setShowPasscodeSetup(true);
              }
            }}
          >
            {security.hasPasscode ? 'Change Passcode' : 'Set Passcode'}
          </button>

          {security.hasPasscode && (
            <button className="btn btn-outline" onClick={() => setShowRemovePasscodeConfirm(true)}>
              Remove Passcode
            </button>
          )}
        </div>

        {security.hasRecoveryQuestion && (
          <p className="text-muted text-xs" style={{ marginTop: 10 }}>
            Recovery question: {security.recoveryQuestion}
          </p>
        )}

        {/* Passkey / Biometric unlock */}
        <div style={{
          marginTop: '1rem',
          padding: '0.875rem',
          background: 'var(--bg-soft)',
          borderRadius: 16,
          border: '1px solid var(--outline)',
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
            <span style={{ fontSize: '1.25rem' }}>🔑</span>
            <div>
              <div style={{ fontWeight: 600, fontSize: '0.9375rem' }}>Passkey (Biometric Unlock)</div>
              <div className="text-muted text-xs" style={{ marginTop: 2 }}>
                {hasPasskey
                  ? 'A passkey is registered on this device. You can unlock with fingerprint or Windows Hello.'
                  : passkeySupported
                    ? 'Register your fingerprint or Windows Hello to unlock without typing your passcode.'
                    : 'Use your device biometrics (Windows Hello, Touch ID) to unlock the app.'}
              </div>
            </div>
          </div>

          {passkeyError && (
            <p className="text-error text-xs" style={{ marginBottom: 8 }}>{passkeyError}</p>
          )}

          {!passkeySupported && (
            <p className="text-muted text-xs" style={{ marginBottom: 8, padding: '6px 10px', background: 'var(--bg)', borderRadius: 8, border: '1px solid var(--outline)' }}>
              ⚠️ Your browser reported no platform authenticator. Make sure you're on HTTPS and Windows Hello / biometrics is set up, then try registering anyway.
            </p>
          )}

          {hasPasskey ? (
            <div style={{ display: 'flex', gap: 8 }}>
              <button
                className="btn btn-outline btn-sm"
                onClick={handleRegisterPasskey}
                disabled={passkeyRegistering}
              >
                {passkeyRegistering ? 'Registering...' : 'Re-register Passkey'}
              </button>
              <button
                className="btn btn-outline btn-sm"
                style={{ color: 'var(--red)', borderColor: 'var(--red)' }}
                onClick={() => { removePasskey(); setPasskeyError(''); }}
              >
                Remove
              </button>
            </div>
          ) : (
            <button
              className="btn btn-primary"
              style={{ display: 'inline-block' }}
              onClick={handleRegisterPasskey}
              disabled={passkeyRegistering || !security.hasPasscode}
              title={!security.hasPasscode ? 'Set a passcode first to enable passkey unlock' : ''}
            >
              {passkeyRegistering ? 'Waiting for biometric...' : 'Register Passkey'}
            </button>
          )}

          {!security.hasPasscode && !hasPasskey && (
            <p className="text-muted text-xs" style={{ marginTop: 6 }}>
              Set a passcode first to enable passkey registration.
            </p>
          )}
        </div>
      </div>

      {/* Backup & Restore */}
      <div className="flow-card" style={{ marginBottom: '1rem' }}>
        <h3 style={{ marginBottom: '0.5rem' }}>Backup & Restore</h3>
        <p className="text-muted text-sm" style={{ marginBottom: '0.875rem' }}>
          Export your profile, settings, and ledger data to a JSON file, then import it anytime.
        </p>

        {backupStatusMessage && (
          <p className={`text-sm ${backupStatusMessage.includes('failed') || backupStatusMessage.includes('Failed') ? 'text-error' : 'text-primary'}`} style={{ marginBottom: 8 }}>
            {backupStatusMessage}
          </p>
        )}

        <div className="two-col">
          <button className="btn btn-primary" onClick={exportBackupToFile} disabled={backupInProgress}>
            {backupInProgress ? 'Please Wait' : 'Export Backup'}
          </button>
          <button className="btn btn-outline" onClick={() => fileInputRef.current?.click()} disabled={backupInProgress}>
            {backupInProgress ? 'Please Wait' : 'Import Backup'}
          </button>
        </div>
        <input ref={fileInputRef} type="file" accept=".json" style={{ display: 'none' }} onChange={handleImport} />
      </div>

      {/* Appearance */}
      <div className="flow-card hover-lift" style={{ '--card-accent': 'var(--secondary)', marginBottom: '1rem' } as React.CSSProperties}>
        <h3 style={{ marginBottom: '0.875rem' }}>Appearance</h3>
        <div className="two-col">
          <button
            className={`btn btn-ripple ${settings.themeMode === 'LIGHT' ? 'btn-primary' : 'btn-outline'}`}
            style={{ height: 56, fontSize: '0.9375rem', transition: 'all 0.3s ease' }}
            onClick={() => setThemeMode('LIGHT')}
          >
            <span style={{ fontSize: '1.2rem', display: 'inline-block', transition: 'transform 0.3s ease' }}>
              ☀️
            </span>
            {' '}Light
          </button>
          <button
            className={`btn btn-ripple ${settings.themeMode === 'DARK' ? 'btn-primary' : 'btn-outline'}`}
            style={{ height: 56, fontSize: '0.9375rem', transition: 'all 0.3s ease' }}
            onClick={() => setThemeMode('DARK')}
          >
            <span style={{ fontSize: '1.2rem', display: 'inline-block', transition: 'transform 0.3s ease' }}>
              🌙
            </span>
            {' '}Dark
          </button>
        </div>
      </div>

      {/* Account Selection */}
      <div className="flow-card" style={{ marginBottom: '1rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
          <h3>Account Selection</h3>
          <button className="btn btn-ghost btn-sm" onClick={() => setShowAccountsConfig(v => !v)}>
            {showAccountsConfig ? 'Hide' : 'Configure'}
          </button>
        </div>
        <p className="text-muted text-sm">Choose which accounts appear in transaction forms.</p>

        {showAccountsConfig && (
          <div style={{ marginTop: '1rem' }}>
            <div style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 8 }}>
              Credit Cards
            </div>
            {CREDIT_CARDS.map(a => (
              <div key={a.id} className="toggle-row" style={{ marginBottom: 6 }}>
                <div className="toggle-info">
                  <div className="toggle-title" style={{ fontSize: '0.875rem' }}>{a.name}</div>
                </div>
                <label className="switch">
                  <input
                    type="checkbox"
                    checked={settings.selectedAccountIds.has(a.id)}
                    onChange={e => setAccountSelected(a.id, e.target.checked)}
                  />
                  <span className="switch-track" />
                </label>
              </div>
            ))}

            <div style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em', margin: '1rem 0 8px' }}>
              Bank Accounts
            </div>
            {BANK_ACCOUNTS.map(a => (
              <div key={a.id} className="toggle-row" style={{ marginBottom: 6 }}>
                <div className="toggle-info">
                  <div className="toggle-title" style={{ fontSize: '0.875rem' }}>{a.name}</div>
                </div>
                <label className="switch">
                  <input
                    type="checkbox"
                    checked={settings.selectedAccountIds.has(a.id)}
                    onChange={e => setAccountSelected(a.id, e.target.checked)}
                  />
                  <span className="switch-track" />
                </label>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Account / Sign Out */}
      <div className="flow-card" style={{ '--card-accent': 'var(--red)' } as React.CSSProperties}>
        <h3 style={{ marginBottom: '0.5rem' }}>Account</h3>
        <p className="text-muted text-sm" style={{ marginBottom: '0.875rem' }}>
          Sign out and return to the profile setup screen.
        </p>
        <button className="btn btn-danger" style={{ display: 'inline-block' }} onClick={() => setShowLogoutConfirm(true)}>
          Sign Out
        </button>
      </div>

      {/* Passcode Setup Modal */}
      {showPasscodeSetup && (
        <div className="modal-overlay" onClick={() => setShowPasscodeSetup(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h3 className="modal-title">Set Passcode</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.875rem' }}>
              <div className="form-group">
                <label className="form-label">Create Passcode (6 digits)</label>
                <input className="form-input" type="password" inputMode="numeric" maxLength={6} value={passcode} onChange={e => setPasscodeVal(e.target.value.replace(/\D/g, '').slice(0, 6))} placeholder="••••••" />
              </div>
              <div className="form-group">
                <label className="form-label">Confirm Passcode</label>
                <input className={`form-input${confirmPasscode && passcode !== confirmPasscode ? ' error' : ''}`} type="password" inputMode="numeric" maxLength={6} value={confirmPasscode} onChange={e => setConfirmPasscode(e.target.value.replace(/\D/g, '').slice(0, 6))} placeholder="••••••" />
              </div>
              <div className="form-group">
                <label className="form-label">Recovery Question</label>
                <select className="form-select" value={recoveryQuestion} onChange={e => setRecoveryQuestion(e.target.value)}>
                  {RECOVERY_QUESTIONS.map(q => <option key={q} value={q}>{q}</option>)}
                </select>
              </div>
              <div className="form-group">
                <label className="form-label">Recovery Answer</label>
                <input className="form-input" value={recoveryAnswer} onChange={e => setRecoveryAnswer(e.target.value)} placeholder="Your answer" />
              </div>
              {passcodeError && <p className="text-error text-sm">{passcodeError}</p>}
            </div>
            <div className="modal-actions">
              <button className="btn btn-outline" onClick={() => setShowPasscodeSetup(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={handleSetPasscode} disabled={savingPasscode || passcode.length !== 6 || passcode !== confirmPasscode || !recoveryAnswer.trim()}>
                {savingPasscode ? 'Saving...' : 'Save Passcode'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Change Passcode Modal */}
      {showChangePasscode && (
        <div className="modal-overlay" onClick={() => setShowChangePasscode(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h3 className="modal-title">Change Passcode</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.875rem' }}>
              <div className="form-group">
                <label className="form-label">Current Passcode</label>
                <input className="form-input" type="password" inputMode="numeric" maxLength={6} value={currentPasscode} onChange={e => { setCurrentPasscode(e.target.value.replace(/\D/g, '').slice(0, 6)); setPasscodeError(''); }} placeholder="••••••" />
              </div>
              <div className="form-group">
                <label className="form-label">New Passcode (6 digits)</label>
                <input className="form-input" type="password" inputMode="numeric" maxLength={6} value={passcode} onChange={e => setPasscodeVal(e.target.value.replace(/\D/g, '').slice(0, 6))} placeholder="••••••" />
              </div>
              <div className="form-group">
                <label className="form-label">Confirm New Passcode</label>
                <input className={`form-input${confirmPasscode && passcode !== confirmPasscode ? ' error' : ''}`} type="password" inputMode="numeric" maxLength={6} value={confirmPasscode} onChange={e => setConfirmPasscode(e.target.value.replace(/\D/g, '').slice(0, 6))} placeholder="••••••" />
              </div>
              <div className="form-group">
                <label className="form-label">Recovery Question</label>
                <select className="form-select" value={recoveryQuestion} onChange={e => setRecoveryQuestion(e.target.value)}>
                  {RECOVERY_QUESTIONS.map(q => <option key={q} value={q}>{q}</option>)}
                </select>
              </div>
              <div className="form-group">
                <label className="form-label">Recovery Answer</label>
                <input className="form-input" value={recoveryAnswer} onChange={e => setRecoveryAnswer(e.target.value)} placeholder="Your answer" />
              </div>
              {passcodeError && <p className="text-error text-sm">{passcodeError}</p>}
            </div>
            <div className="modal-actions">
              <button className="btn btn-outline" onClick={() => setShowChangePasscode(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={handleChangePasscode} disabled={savingPasscode || !currentPasscode || passcode.length !== 6 || passcode !== confirmPasscode || !recoveryAnswer.trim()}>
                {savingPasscode ? 'Updating...' : 'Update Passcode'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Remove Passcode confirm */}
      {showRemovePasscodeConfirm && (
        <div className="modal-overlay" onClick={() => setShowRemovePasscodeConfirm(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h3 className="modal-title">Remove Passcode?</h3>
            <p className="modal-subtitle">This will disable app lock and remove all security settings. Are you sure?</p>
            <div className="modal-actions">
              <button className="btn btn-outline" onClick={() => setShowRemovePasscodeConfirm(false)}>Cancel</button>
              <button className="btn btn-danger" onClick={() => { clearPasscode(); setShowRemovePasscodeConfirm(false); }}>
                Remove
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Logout confirm */}
      {showLogoutConfirm && (
        <div className="modal-overlay" onClick={() => setShowLogoutConfirm(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h3 className="modal-title">Sign out?</h3>
            <p className="modal-subtitle">You'll be taken back to the profile setup screen. Your data stays saved.</p>
            <div className="modal-actions">
              <button className="btn btn-outline" onClick={() => setShowLogoutConfirm(false)}>Cancel</button>
              <button className="btn btn-danger" onClick={() => { setShowLogoutConfirm(false); signOut(); }}>
                Sign Out
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
