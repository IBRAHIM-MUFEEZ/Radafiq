import React, { useRef, useState, useEffect } from 'react';
import { motion, useInView } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import RadafiqLogo from '../components/RadafiqLogo';
import './LandingPage.css';

// Lazy-loaded 3D scene — not loaded on initial render so Three.js doesn't block
const LazyScene3D = React.lazy(() => import('./LazyScene3D'));

// ── Animation variants ─────────────────────────────────────────────────────────

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.08, delayChildren: 0.1 },
  },
};

const fadeInUp = {
  hidden: { opacity: 0, y: 16 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.45, ease: 'easeOut' as const } },
};

// ── Feature data ───────────────────────────────────────────────────────────────

const FEATURES = [
  {
    icon: '📊',
    color: '#5B7FFF',
    title: 'Customer Ledger',
    desc: 'Track every customer transaction with a full audit trail. Manage balances, partial payments, and credit history in one place.',
    tags: ['Transactions', 'Balances', 'History'],
  },
  {
    icon: '💳',
    color: '#2DD4A0',
    title: 'Accounts & EMIs',
    desc: 'Link bank accounts and credit cards. Track EMI schedules with installment breakdowns, overdue alerts, and settlement status.',
    tags: ['Bank Accounts', 'Credit Cards', 'EMI Schedule'],
  },
  {
    icon: '🏦',
    color: '#F59E5A',
    title: 'Savings Goals',
    desc: 'Manage customer-specific savings with deposits and withdrawals. Set goals and watch progress with clear visual tracking.',
    tags: ['Deposits', 'Withdrawals', 'Goals'],
  },
  {
    icon: '📈',
    color: '#A78BFA',
    title: 'Analytics & Reports',
    desc: 'Get actionable insights from your financial data. Track total used, paid, outstanding balances, and account activity at a glance.',
    tags: ['Dashboard', 'Reports', 'Insights'],
  },
  {
    icon: '🔐',
    color: '#F472B6',
    title: 'Bank-Grade Security',
    desc: 'Protect your data with passcode lock, biometric authentication, and encrypted Google Drive backup. Full data export/import.',
    tags: ['Passcode', 'Biometrics', 'Backup'],
  },
  {
    icon: '🎨',
    color: '#4ADE80',
    title: 'Beautiful Experience',
    desc: 'Modern glass-morphism design with smooth animations, dark/light themes, and responsive layout that works on any device.',
    tags: ['Dark Mode', 'Animations', 'Responsive'],
  },
];

const HIGHLIGHTS = [
  { number: '50+', label: 'Indian banks & cards supported' },
  { number: '100%', label: 'Offline-first with cloud sync' },
  { number: '24/7', label: 'Data accessible from any device' },
];

// ── Section wrapper with scroll animation ──────────────────────────────────────

function Section({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  const ref = useRef<HTMLDivElement>(null);
  const inView = useInView(ref, { once: true, margin: '-80px' });

  return (
    <motion.div
      ref={ref}
      className={className}
      variants={containerVariants}
      initial="hidden"
      animate={inView ? 'visible' : 'hidden'}
    >
      {children}
    </motion.div>
  );
}

// ── Top Navigation Bar ─────────────────────────────────────────────────────────

function LandingNavbar() {
  const navigate = useNavigate();
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 20);
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  return (
    <nav className={`landing-navbar${scrolled ? ' scrolled' : ''}`}>
      {/* Left spacer for symmetry */}
      <div className="landing-navbar-spacer" />

      {/* Centered logo */}
      <div className="landing-navbar-logo">
        <RadafiqLogo size={32} />
        <span className="landing-navbar-brand">Radafiq</span>
      </div>

      {/* Right actions */}
      <div className="landing-navbar-actions">
        <button
          className="btn btn-ghost btn-sm"
          onClick={() => navigate('/login')}
        >
          Login
        </button>
        <button
          className="btn btn-primary btn-sm"
          onClick={() => navigate('/login')}
        >
          Sign Up
        </button>
      </div>
    </nav>
  );
}

// ── Main Landing Page ─────────────────────────────────────────────────────────

export default function LandingPage() {
  const navigate = useNavigate();
  const [show3d, setShow3d] = useState(false);

  // Defer 3D scene rendering until after first paint
  useEffect(() => {
    setShow3d(true);
  }, []);

  return (
    <div className="landing">
      {/* ── Top Navbar ──────────────────────────────────────────── */}
      <LandingNavbar />

      {/* ── Hero ────────────────────────────────────────────────── */}
      <div className="landing-hero">
        {/* 3D Background (lazy — loads after first paint) */}
        {show3d && (
          <div className="landing-3d-bg">
            <React.Suspense fallback={null}>
              <LazyScene3D />
            </React.Suspense>
          </div>
        )}

        {/* Content — minimal stagger, grouped instead of per-element */}
        <motion.div
          variants={containerVariants}
          initial="hidden"
          animate="visible"
        >
          <motion.div className="landing-hero-logo" variants={fadeInUp}>
            <div className="landing-hero-logo-glow" />
            <motion.div
              animate={{ y: [0, -6, 0] }}
              transition={{ duration: 3, repeat: Infinity, ease: 'easeInOut' }}
            >
              <RadafiqLogo size={140} />
            </motion.div>
          </motion.div>

          <motion.h1 variants={fadeInUp}>
            Your Business.
            <br />
            In Perfect Ledger.
          </motion.h1>

          <motion.p className="tagline" variants={fadeInUp}>
            Radafiq is a beautiful financial ledger app for tracking customer credit,
            EMI plans, savings, and account dues — all in one place with a stunning,
            modern experience.
          </motion.p>

          <motion.div className="landing-hero-actions" variants={fadeInUp}>
            <motion.button
              className="btn btn-primary btn-lg"
              onClick={() => navigate('/login')}
              whileHover={{ scale: 1.03 }}
              whileTap={{ scale: 0.97 }}
            >
              Get Started Free
            </motion.button>
            <motion.button
              className="btn btn-ghost btn-lg"
              onClick={() => {
                document.getElementById('features')?.scrollIntoView({ behavior: 'smooth' });
              }}
              whileHover={{ scale: 1.03 }}
              whileTap={{ scale: 0.97 }}
              style={{ border: '1px solid var(--outline)' }}
            >
              Explore Features
            </motion.button>
          </motion.div>
        </motion.div>

        {/* Scroll indicator */}
        <motion.div
          className="scroll-indicator"
          animate={{ opacity: [0.4, 1, 0.4], y: [0, 4, 0] }}
          transition={{ duration: 2, repeat: Infinity, ease: 'easeInOut' }}
        >
          <span>Scroll</span>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M7 13l5 5 5-5M7 6l5 5 5-5" />
          </svg>
        </motion.div>
      </div>

      {/* ── Features ─────────────────────────────────────────────── */}
      <Section>
        <div className="landing-section" id="features">
          <div className="landing-section-header">
            <motion.h2 variants={fadeInUp}>Everything you need to manage finances</motion.h2>
            <motion.p variants={fadeInUp}>
              From customer ledgers to EMI schedules, Radafiq brings all your financial tools
              into one seamless experience.
            </motion.p>
          </div>

          <motion.div
            className="feature-grid"
            variants={containerVariants}
          >
            {FEATURES.map((feature) => (
              <motion.div
                key={feature.title}
                className="feature-card"
                variants={fadeInUp}
                whileHover={{ y: -6 }}
                transition={{ type: 'spring', stiffness: 400, damping: 20 }}
              >
                <div
                  className="feature-card-icon"
                  style={{
                    background: `color-mix(in srgb, ${feature.color} 15%, transparent)`,
                    color: feature.color,
                  }}
                >
                  {feature.icon}
                </div>
                <h3>{feature.title}</h3>
                <p>{feature.desc}</p>
                <div className="feature-tags">
                  {feature.tags.map(tag => (
                    <span key={tag} className="feature-tag">{tag}</span>
                  ))}
                </div>
              </motion.div>
            ))}
          </motion.div>
        </div>
      </Section>

      {/* ── Highlights ───────────────────────────────────────────── */}
      <Section>
        <div className="landing-section">
          <div className="landing-section-header">
            <motion.h2 variants={fadeInUp}>Built for Indian finance management</motion.h2>
            <motion.p variants={fadeInUp}>
              Designed specifically for small business owners, shopkeepers, and anyone managing
              customer credit and installments in India.
            </motion.p>
          </div>

          <motion.div className="highlight-row" variants={containerVariants}>
            {HIGHLIGHTS.map(h => (
              <motion.div key={h.label} className="highlight-item" variants={fadeInUp}>
                <div className="number">{h.number}</div>
                <div className="label">{h.label}</div>
              </motion.div>
            ))}
          </motion.div>

          {/* Feature bullets */}
          <motion.div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
              gap: '1rem',
              marginTop: '3rem',
            }}
            variants={containerVariants}
          >
            {[
              'Support for 50+ Indian banks and credit cards',
              'Track EMI plans with full installment schedules',
              'Offline-first — works without internet',
              'Google Drive backup & restore',
              'Passcode & biometric security',
              'Dark & light themes',
              'Customer-wise savings and deposits',
              'Export statements as PDF',
            ].map(item => (
              <motion.div
                key={item}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.75rem',
                  padding: '0.75rem 1rem',
                  borderRadius: 10,
                  background: 'var(--surface)',
                  border: '1px solid var(--outline)',
                  fontSize: '0.9rem',
                }}
                variants={fadeInUp}
              >
                <span style={{ color: 'var(--green)' }}>✓</span>
                {item}
              </motion.div>
            ))}
          </motion.div>
        </div>
      </Section>

      {/* ── CTA ──────────────────────────────────────────────────── */}
      <div className="landing-cta">
        <Section>
          <motion.h2 variants={fadeInUp}>Ready to take control of your finances?</motion.h2>
          <motion.p variants={fadeInUp}>
            Join Radafiq today and manage your customer ledger, accounts, and EMIs
            with a beautiful, modern interface.
          </motion.p>
          <motion.div variants={fadeInUp}>
            <motion.button
              className="btn btn-primary btn-lg"
              onClick={() => navigate('/login')}
              whileHover={{ scale: 1.04 }}
              whileTap={{ scale: 0.96 }}
            >
              Get Started Free
            </motion.button>
          </motion.div>
        </Section>
      </div>

      {/* ── Footer ────────────────────────────────────────────────── */}
      <div className="landing-footer">
        <p>
          Radafiq — Customer Ledger & Finance Manager
          <br />
          <span style={{ opacity: 0.5 }}>© {new Date().getFullYear()} Radafiq. All rights reserved.</span>
        </p>
      </div>
    </div>
  );
}
