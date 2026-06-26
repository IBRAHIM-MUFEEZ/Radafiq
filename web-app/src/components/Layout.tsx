import React, { useMemo } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  Home, Users, CreditCard, Calendar, PiggyBank, BarChart2, Settings,
  Sparkles, type LucideIcon,
} from 'lucide-react';
import { useApp } from '../context/AppContext';
import RadafiqLogo from './RadafiqLogo';
import AnimatedAvatar from './AnimatedAvatar';

const NAV_ITEMS = [
  { path: '/dashboard', label: 'Home', icon: Home },
  { path: '/customers', label: 'Customers', icon: Users },
  { path: '/accounts', label: 'Accounts', icon: CreditCard },
  { path: '/savings', label: 'Savings', icon: PiggyBank },
  { path: '/emi', label: 'EMI', icon: Calendar },
  { path: '/analytics', label: 'Analytics', icon: BarChart2 },
  { path: '/settings', label: 'Settings', icon: Settings },
];

const sidebarVariants = {
  hidden: { opacity: 0, x: -20 },
  visible: {
    opacity: 1, x: 0,
    transition: { staggerChildren: 0.06, delayChildren: 0.1, ease: 'easeOut' as const },
  },
};

const navItemVariants = {
  hidden: { opacity: 0, x: -16 },
  visible: { opacity: 1, x: 0, transition: { duration: 0.35, ease: 'easeOut' as const } },
};

function NavIcon({ Icon, isActive }: { Icon: LucideIcon; isActive: boolean }) {
  return (
    <span style={{ position: 'relative', display: 'inline-flex' }}>
      <Icon size={18} />
      {isActive && (
        <Sparkles
          size={10}
          style={{
            position: 'absolute',
            top: -4,
            right: -6,
            color: 'var(--primary)',
            animation: 'pulse 2s ease-in-out infinite',
            opacity: 0.7,
          }}
        />
      )}
    </span>
  );
}

export default function Layout({ children }: { children: React.ReactNode }) {
  const { profile } = useApp();
  const location = useLocation();

  const navItemsWithSparkles = useMemo(() => NAV_ITEMS, []);

  return (
    <div className="app-layout">
      {/* Sidebar */}
      <motion.aside
        className="sidebar"
        variants={sidebarVariants}
        initial="hidden"
        animate="visible"
      >
        <div className="nav-logo">
          <motion.div
            className="nav-logo-circle"
            whileHover={{ scale: 1.08, rotate: -3 }}
            whileTap={{ scale: 0.95 }}
          >
            <RadafiqLogo size={36} />
          </motion.div>
          <div>
            <div className="nav-logo-text">Radafiq</div>
            <div className="nav-logo-sub">Finance Manager</div>
          </div>
        </div>

        <nav style={{ flex: 1 }}>
          {navItemsWithSparkles.map(({ path, label, icon: Icon }) => {
            const isActive = location.pathname.startsWith(path);
            return (
              <motion.div key={path} variants={navItemVariants}>
                <NavLink
                  to={path}
                  className={`nav-item${isActive ? ' active' : ''}`}
                >
                  <NavIcon Icon={Icon} isActive={isActive} />
                  {label}
                  {isActive && (
                    <motion.div
                      layoutId="nav-indicator"
                      style={{
                        position: 'absolute',
                        left: -12,
                        top: '50%',
                        transform: 'translateY(-50%)',
                        width: 3,
                        height: '60%',
                        borderRadius: '0 3px 3px 0',
                        background: 'var(--gradient-primary)',
                      }}
                      transition={{ type: 'spring', stiffness: 300, damping: 25 }}
                    />
                  )}
                </NavLink>
              </motion.div>
            );
          })}
        </nav>

        {profile && (
          <motion.div
            className="nav-footer"
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.5, duration: 0.4 }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <AnimatedAvatar
                name={profile.displayName}
                photoUrl={profile.photoUrl}
                size={36}
              />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: '0.875rem', fontWeight: 600, wordBreak: 'break-word' }}>
                  {profile.displayName}
                </div>
                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {profile.businessName}
                </div>
              </div>
            </div>
          </motion.div>
        )}
      </motion.aside>

      {/* Main content */}
      <main className="main-content">
        <div className="radafiq-bg">
          {children}
        </div>
      </main>

      {/* Bottom nav (mobile) */}
      <nav className="bottom-nav">
        {NAV_ITEMS.slice(0, 5).map(({ path, label, icon: Icon }) => {
          const isActive = location.pathname.startsWith(path);
          return (
            <NavLink
              key={path}
              to={path}
              className={`bottom-nav-item${isActive ? ' active' : ''}`}
            >
              <Icon size={20} />
              <span style={{ fontSize: '0.625rem', fontWeight: isActive ? 700 : 500 }}>
                {label}
              </span>
            </NavLink>
          );
        })}
      </nav>
    </div>
  );
}
