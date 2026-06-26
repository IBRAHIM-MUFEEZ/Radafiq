import React, { useEffect, lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import { AppProvider, useApp } from './context/AppContext';
import Layout from './components/Layout';

// Critical pages loaded eagerly — users navigate here first and immediately
import LandingPage from './pages/LandingPage';
import Dashboard from './pages/Dashboard';

// All other pages lazy-loaded — they load on first visit
const Background3D = lazy(() => import('./components/Background3D'));
const ProfileSetup = lazy(() => import('./pages/ProfileSetup'));
const SecuritySetup = lazy(() => import('./pages/SecuritySetup'));
const AppLock = lazy(() => import('./pages/AppLock'));
const CustomersPage = lazy(() => import('./pages/CustomersPage'));
const AccountsPage = lazy(() => import('./pages/AccountsPage'));
const CustomerDetail = lazy(() => import('./pages/CustomerDetail'));
const AccountDetail = lazy(() => import('./pages/AccountDetail'));
const SavingsPage = lazy(() => import('./pages/SavingsPage'));
const SettingsPage = lazy(() => import('./pages/SettingsPage'));
const AnalyticsPage = lazy(() => import('./pages/AnalyticsPage'));
const EmiSchedulePage = lazy(() => import('./pages/EmiSchedulePage'));

// ── Shimmer-based page skeleton (matches theme.css shimmer classes) ──────────

function PageSkeleton() {
  return (
    <div className="radafiq-bg" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', flexDirection: 'column', gap: 16 }}>
      <div className="spinner" style={{ width: 48, height: 48, borderWidth: 4 }} />
      <p className="text-muted text-sm" style={{ animation: 'breathe 2s ease-in-out infinite' }}>
        Loading...
      </p>
    </div>
  );
}

// ── Page transition wrapper ──────────────────────────────────────────────────

const pageVariants = {
  initial: { opacity: 0, y: 20 },
  animate: { opacity: 1, y: 0, transition: { duration: 0.35, ease: 'easeOut' as const } },
  exit: { opacity: 0, y: -10, transition: { duration: 0.2, ease: 'easeIn' as const } },
};

function AnimatedPage({ children }: { children: React.ReactNode }) {
  const location = useLocation();
  return (
    <AnimatePresence mode="wait">
      <motion.div
        key={location.pathname}
        variants={pageVariants}
        initial="initial"
        animate="animate"
        exit="exit"
      >
        <Suspense fallback={<PageSkeleton />}>
          {children}
        </Suspense>
      </motion.div>
    </AnimatePresence>
  );
}

function AppRoutes() {
  const { user, authLoading, profile, profileLoading, security, settings, dataLoading } = useApp();

  // Apply theme
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', settings.themeMode === 'DARK' ? 'dark' : 'light');
  }, [settings.themeMode]);

  if (authLoading || profileLoading) {
    return (
      <div className="radafiq-bg" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', flexDirection: 'column', gap: 16 }}>
        <div className="spinner" style={{ width: 48, height: 48, borderWidth: 4 }} />
        <p className="text-muted text-sm" style={{ animation: 'breathe 2s ease-in-out infinite' }}>
          Loading your finances...
        </p>
      </div>
    );
  }

  // Not signed in — show landing page or login
  if (!user) {
    return (
      <Suspense fallback={<PageSkeleton />}>
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/login" element={<ProfileSetup />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    );
  }

  // Profile not complete
  if (!profile?.isProfileComplete) {
    return (
      <Suspense fallback={<PageSkeleton />}>
        <ProfileSetup />
      </Suspense>
    );
  }

  // Security setup needed
  if (!security.hasPasscode) {
    return (
      <Suspense fallback={<PageSkeleton />}>
        <SecuritySetup />
      </Suspense>
    );
  }

  // App locked
  if (security.lockEnabled && !security.isUnlocked) {
    return (
      <Suspense fallback={<PageSkeleton />}>
        <AppLock />
      </Suspense>
    );
  }

  // Wait for Firestore data before rendering main app
  if (dataLoading) {
    return (
      <div className="radafiq-bg" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', flexDirection: 'column', gap: 16 }}>
        <div className="spinner" style={{ width: 48, height: 48, borderWidth: 4 }} />
        <p className="text-muted text-sm" style={{ animation: 'breathe 2s ease-in-out infinite' }}>
          Loading your finances...
        </p>
      </div>
    );
  }

  return (
    <Layout>
      <AnimatedPage>
        <Routes>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/customers" element={<CustomersPage />} />
          <Route path="/customers/:customerId" element={<CustomerDetail />} />
          <Route path="/customers/:customerId/savings" element={<SavingsPage key="detail" />} />
          <Route path="/accounts" element={<AccountsPage />} />
          <Route path="/accounts/:accountId" element={<AccountDetail />} />
          <Route path="/savings" element={<SavingsPage key="overview" />} />
          <Route path="/emi" element={<EmiSchedulePage />} />
          <Route path="/analytics" element={<AnalyticsPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </AnimatedPage>
    </Layout>
  );
}

export default function App() {
  // Prefetch common page chunks after the first render so navigation is instant
  useEffect(() => {
    const prefetch = (importer: () => Promise<unknown>) => {
      // Use requestIdleCallback or setTimeout to avoid competing with critical work
      if ('requestIdleCallback' in window) {
        (window as any).requestIdleCallback(() => { importer(); }, { timeout: 2000 });
      } else {
        setTimeout(() => { importer(); }, 1000);
      }
    };
    prefetch(() => import('./pages/CustomersPage'));
    prefetch(() => import('./pages/AccountsPage'));
    prefetch(() => import('./pages/SettingsPage'));
  }, []);

  return (
    <BrowserRouter>
      <AppProvider>
        <Suspense fallback={null}>
          <Background3D />
        </Suspense>
        <AppRoutes />
      </AppProvider>
    </BrowserRouter>
  );
}
