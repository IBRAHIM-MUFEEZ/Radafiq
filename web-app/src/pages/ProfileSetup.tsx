import React, { useState, useEffect, useRef } from 'react';
import { useApp } from '../context/AppContext';
import RadafiqLogo from '../components/RadafiqLogo';
import { fadeInUp, fadeInScale } from '../utils/animations';

export default function ProfileSetup() {
  const { profile, saveProfile, signInWithGoogle, signOut, user } = useApp();
  const logoRef = useRef<HTMLDivElement>(null);
  const formRef = useRef<HTMLDivElement>(null);
  const [displayName, setDisplayName] = useState(profile?.displayName ?? '');
  const [businessName, setBusinessName] = useState(profile?.businessName ?? '');
  const [email, setEmail] = useState(profile?.email ?? user?.email ?? '');
  const [saving, setSaving] = useState(false);
  const [signingIn, setSigningIn] = useState(false);
  const [signInError, setSignInError] = useState('');

  // BUG-29 fix: sync form fields when profile loads after mount (e.g. after Google sign-in)
  useEffect(() => {
    if (profile) {
      setDisplayName(profile.displayName);
      setBusinessName(profile.businessName);
      setEmail(profile.email);
    }
  }, [profile]);

  useEffect(() => {
    if (logoRef.current) fadeInScale(logoRef.current);
    if (formRef.current) fadeInUp(formRef.current, 200);
  }, []);

  const handleSave = async () => {
    if (!displayName.trim() || !businessName.trim()) return;
    setSaving(true);
    try {
      await saveProfile(displayName, businessName, email, profile?.photoUrl ?? '');
    } finally {
      setSaving(false);
    }
  };

  const handleGoogleSignIn = async () => {
    setSigningIn(true);
    setSignInError('');
    try {
      await signInWithGoogle();
      // signInWithRedirect navigates away — page will reload on return
      // so we never reach here in the redirect flow
    } catch (e: unknown) {
      const err = e as { code?: string; message?: string };
      if (err.code === 'auth/unauthorized-domain') {
        setSignInError('This domain is not authorised. Add localhost to Firebase Console → Authentication → Authorized domains.');
      } else {
        setSignInError(`Sign-in failed: ${err.code ?? err.message ?? 'Unknown error'}`);
      }
      setSigningIn(false);
    }
  };

  return (
    <div className="radafiq-bg" style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '1rem' }}>
      <div style={{ width: '100%', maxWidth: 480 }}>
        {/* Logo */}
        <div style={{ textAlign: 'center', marginBottom: '2rem' }} ref={logoRef}>
          <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '1rem' }}>
            <RadafiqLogo size={88} />
          </div>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 800, color: 'var(--text)' }}>Radafiq</h1>
          <p className="text-muted" style={{ marginTop: 4 }}>Customer Ledger & Finance Manager</p>
        </div>

        <div ref={formRef}>
        {/* Google Sign-In */}
        {!user ? (
          <div className="flow-card" style={{ marginBottom: '1rem' }}>
            <h3 style={{ marginBottom: 8 }}>Sign in with Google</h3>
            <p className="text-muted text-sm" style={{ marginBottom: '1rem' }}>
              One tap to sign in, connect Google Drive, and restore your latest backup automatically.
            </p>
            <button
              className="btn btn-primary btn-full btn-lg"
              onClick={handleGoogleSignIn}
              disabled={signingIn}
            >
              {signingIn ? 'Signing in...' : 'Continue with Google'}
            </button>
            {signInError && (
              <p style={{ color: 'var(--red)', fontSize: '0.8rem', marginTop: 8 }}>
                {signInError}
              </p>
            )}
          </div>
        ) : (
          <div className="flow-card" style={{ marginBottom: '1rem', '--card-accent': 'var(--green)' } as React.CSSProperties}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <div style={{
                width: 36, height: 36, borderRadius: '50%',
                background: 'color-mix(in srgb, var(--green) 15%, transparent)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                color: 'var(--green)', fontSize: '1.125rem',
              }}>✓</div>
              <div>
                <div style={{ fontWeight: 600, color: 'var(--green)' }}>Signed in with Google</div>
                <div className="text-muted text-sm">{user.email}</div>
              </div>
              <button className="btn btn-ghost btn-sm" style={{ marginLeft: 'auto' }} onClick={() => signOut()}>
                Sign out
              </button>
            </div>
          </div>
        )}

        {/* Profile form */}
        <div className="flow-card">
          <h3 style={{ marginBottom: '1rem' }}>Profile Details</h3>

          {profile?.photoUrl && (
            <div style={{ textAlign: 'center', marginBottom: '1rem' }}>
              <img
                src={profile.photoUrl}
                alt="Profile"
                referrerPolicy="no-referrer"
                style={{ width: 72, height: 72, borderRadius: '50%', objectFit: 'cover', border: '2px solid var(--outline)' }}
              />
            </div>
          )}

          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <div className="form-group">
              <label className="form-label">Your Name *</label>
              <input
                className="form-input"
                value={displayName}
                onChange={e => setDisplayName(e.target.value)}
                placeholder="Enter your name"
              />
            </div>

            <div className="form-group">
              <label className="form-label">Business / Shop Name *</label>
              <input
                className="form-input"
                value={businessName}
                onChange={e => setBusinessName(e.target.value)}
                placeholder="Enter business name"
              />
            </div>

            <div className="form-group">
              <label className="form-label">Email</label>
              <input
                className="form-input"
                type="email"
                value={email}
                onChange={e => setEmail(e.target.value)}
                placeholder="Enter email"
              />
            </div>

            <button
              className="btn btn-primary btn-full btn-lg"
              onClick={handleSave}
              disabled={saving || !displayName.trim() || !businessName.trim() || !user}
            >
              {saving ? 'Saving...' : 'Save Profile'}
            </button>
          </div>
        </div>
        </div>
      </div>
    </div>
  );
}
