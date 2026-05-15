import React, { useState, useEffect, useRef } from 'react';
import { useApp } from '../context/AppContext';
import RadafiqLogo from '../components/RadafiqLogo';
import { fadeInScale, fadeInUp } from '../utils/animations';

const RECOVERY_QUESTIONS = [
  'What is your email ID?',
  "What was your first pet's name?",
  'What city were you born in?',
  "What is your mother's first name?",
];

export default function SecuritySetup() {
  const { setPasscode } = useApp();
  const logoRef = useRef<HTMLDivElement>(null);
  const formRef = useRef<HTMLDivElement>(null);
  const [passcode, setPasscodeVal] = useState('');
  const [confirm, setConfirm] = useState('');
  const [question, setQuestion] = useState(RECOVERY_QUESTIONS[0]);
  const [answer, setAnswer] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (logoRef.current) fadeInScale(logoRef.current);
    if (formRef.current) fadeInUp(formRef.current, 200);
  }, []);

  const passcodesMatch = passcode.length === 6 && passcode === confirm;
  // BUG-30 fix: require at least 3 characters for recovery answer
  const canSave = passcodesMatch && question && answer.trim().length >= 3;

  const handleSave = async () => {
    if (!canSave) return;
    setSaving(true);
    setError('');
    try {
      await setPasscode(passcode, question, answer);
    } catch (e) {
      setError('Failed to set passcode. Please try again.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="radafiq-bg" style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '1rem' }}>
      <div style={{ width: '100%', maxWidth: 480 }}>
        <div style={{ textAlign: 'center', marginBottom: '2rem' }} ref={logoRef}>
          <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '1rem' }}>
            <RadafiqLogo size={72} />
          </div>
          <h2>Protect the App</h2>
          <p className="text-muted text-sm" style={{ marginTop: 4 }}>
            Set a 6-digit passcode and a recovery question to secure your data.
          </p>
        </div>

        <div className="flow-card">
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <div className="form-group">
              <label className="form-label">Create Passcode (6 digits)</label>
              <input
                className="form-input"
                type="password"
                inputMode="numeric"
                maxLength={6}
                value={passcode}
                onChange={e => setPasscodeVal(e.target.value.replace(/\D/g, '').slice(0, 6))}
                placeholder="••••••"
              />
            </div>

            <div className="form-group">
              <label className="form-label">Confirm Passcode</label>
              <input
                className={`form-input${confirm && !passcodesMatch ? ' error' : ''}`}
                type="password"
                inputMode="numeric"
                maxLength={6}
                value={confirm}
                onChange={e => setConfirm(e.target.value.replace(/\D/g, '').slice(0, 6))}
                placeholder="••••••"
              />
              {confirm && !passcodesMatch && (
                <span className="form-error">Passcodes must match and be exactly 6 digits.</span>
              )}
            </div>

            <div className="form-group">
              <label className="form-label">Recovery Question</label>
              <select
                className="form-select"
                value={question}
                onChange={e => setQuestion(e.target.value)}
              >
                {RECOVERY_QUESTIONS.map(q => (
                  <option key={q} value={q}>{q}</option>
                ))}
              </select>
            </div>

            <div className="form-group">
              <label className="form-label">Recovery Answer</label>
              <input
                className="form-input"
                value={answer}
                onChange={e => setAnswer(e.target.value)}
                placeholder="Your answer (min. 3 characters)"
                minLength={3}
              />
              <span className="text-muted text-xs" style={{ marginTop: 4 }}>
                Forgot passcode recovery works only through this answer.
              </span>
            </div>

            {error && <p className="text-error text-sm">{error}</p>}

            <button
              className="btn btn-primary btn-full btn-lg"
              onClick={handleSave}
              disabled={!canSave || saving}
            >
              {saving ? 'Saving...' : 'Save Security Setup'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
