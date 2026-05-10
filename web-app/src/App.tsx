import React, { useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AppProvider, useApp } from './context/AppContext';
import Layout from './components/Layout';
import ProfileSetup from './pages/ProfileSetup';
import SecuritySetup from './pages/SecuritySetup';
import AppLock from './pages/AppLock';
import Dashboard from './pages/Dashboard';
import CustomersPage from './pages/CustomersPage';
import AccountsPage from './pages/AccountsPage';
import CustomerDetail from './pages/CustomerDetail';
import AccountDetail from './pages/AccountDetail';
import SavingsPage from './pages/SavingsPage';
import SettingsPage from './pages/SettingsPage';
import AnalyticsPage from './pages/AnalyticsPage';
import EmiSchedulePage from './pages/EmiSchedulePage';

function AppRoutes() {
  const { user, authLoading, profile, profileLoading, security, settings } = useApp();

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

  // Not signed in
  if (!user) {
    return <ProfileSetup />;
  }

  // Profile not complete
  if (!profile?.isProfileComplete) {
    return <ProfileSetup />;
  }

  // Security setup needed
  if (!security.hasPasscode) {
    return <SecuritySetup />;
  }

  // App locked
  if (security.lockEnabled && !security.isUnlocked) {
    return <AppLock />;
  }

  return (
    <Layout>
      <Routes>
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/customers" element={<CustomersPage />} />
        <Route path="/customers/:customerId" element={<CustomerDetail />} />
        <Route path="/customers/:customerId/savings" element={<SavingsPage />} />
        <Route path="/accounts" element={<AccountsPage />} />
        <Route path="/accounts/:accountId" element={<AccountDetail />} />
        <Route path="/emi" element={<EmiSchedulePage />} />
        <Route path="/analytics" element={<AnalyticsPage />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </Layout>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AppProvider>
        <AppRoutes />
      </AppProvider>
    </BrowserRouter>
  );
}
