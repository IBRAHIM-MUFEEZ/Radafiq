import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { useApp } from '../context/AppContext';
import RadafiqLogo from '../components/RadafiqLogo';

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.12, delayChildren: 0.1 },
  },
};

const itemVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.4, ease: 'easeOut' as const } },
};

const RECOVERY_QUESTIONS = [
  'What is your email ID?',
  "What was your first pet's name?",
  'What city were you born in?',
  "What is your mother's first name?",
];

export default function SecuritySetup() {
  const { setPasscode } = useApp();
  const [passcode, setPasscodeVal] = useState('');
  const [confirm, setConfirm] = useState('');
  const [question, setQuestion] = useState(RECOVERY_QUESTIONS[0]);
  const [answer, setAnswer] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const passcodesMatch = passcode.length === 6 && passcode === confirm;
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
    <motion.div
      className="radafiq-bg"
      style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '1rem' }}
      variants={containerVariants}
      initial="hidden"
      animate="visible"
    >
      <div style={{ width: '100%', maxWidth: 480 }}>
        <motion.div style={{ textAlign: 'center', marginBottom: '2rem' }} variants={itemVariants}>
          <motion.div
            style={{ display: 'flex', justifyContent: 'center', marginBottom: '1rem' }}
            whileHover={{ scale: 1.05 }}
            transition={{ type: 'spring', stiffness: 300, damping: 10 }}
          >
            <RadafiqLogo size={72} />
          </motion.div>
          <h2>Protect the App</h2>
          <p className="text-muted text-sm" style={{ marginTop: 4 }}>
            Set a 6-digit passcode and a recovery question to secure your data.
          </p>
        </motion.div>

        <motion.div className="flow-card" variants={itemVariants} whileHover={{ y: -1 }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <motion.div className="form-group" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.15, duration: 0.3 }}>
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
            </motion.div>

            <motion.div className="form-group" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2, duration: 0.3 }}>
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
                <motion.span className="form-error" initial={{ opacity: 0, x: -5 }} animate={{ opacity: 1, x: 0 }}>
                  Passcodes must match and be exactly 6 digits.
                </motion.span>
              )}
            </motion.div>

            <motion.div className="form-group" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.25, duration: 0.3 }}>
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
            </motion.div>

            <motion.div className="form-group" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3, duration: 0.3 }}>
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
            </motion.div>

            {error && <p className="text-error text-sm">{error}</p>}

            <motion.button
              className="btn btn-primary btn-full btn-lg"
              onClick={handleSave}
              disabled={!canSave || saving}
              whileTap={{ scale: 0.98 }}
              whileHover={{ scale: 1.01 }}
            >
              {saving ? 'Saving...' : 'Save Security Setup'}
            </motion.button>
          </div>
        </motion.div>
      </div>
    </motion.div>
  );
}
