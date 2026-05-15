import React, { useMemo, useEffect, useRef } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import {
  Home, Users, CreditCard, Calendar, BarChart2, Settings, Sparkles, type LucideIcon,
} from 'lucide-react';
import { useApp } from '../context/AppContext';
import RadafiqLogo from './RadafiqLogo';
import AnimatedAvatar from './AnimatedAvatar';
import { navItemEntrance, fadeIn } from '../utils/animations';

const NAV_ITEMS = [
  { path: '/dashboard', label: 'Home', icon: Home },
  { path: '/customers', label: 'Customers', icon: Users },
  { path: '/accounts', label: 'Accounts', icon: CreditCard },
  { path: '/emi', label: 'EMI', icon: Calendar },
  { path: '/analytics', label: 'Analytics', icon: BarChart2 },
  { path: '/settings', label: 'Settings', icon: Settings },
];

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
  const sidebarRef = useRef<HTMLDivElement>(null);
  const mainRef = useRef<HTMLDivElement>(null);

  const navItemsWithSparkles = useMemo(() => NAV_ITEMS, []);

  useEffect(() => {
    if (sidebarRef.current) {
      navItemEntrance('.nav-item', 80);
    }
  }, []);

  useEffect(() => {
    if (mainRef.current) {
      fadeIn(mainRef.current);
    }
  }, [location.pathname]);

  return (
    <div className="app-layout">
      <div className="bg-orbs">
        <div className="bg-orb" />
        <div className="bg-orb" />
        <div className="bg-orb" />
      </div>

      {/* Sidebar */}
      <aside className="sidebar" ref={sidebarRef}>
        <div className="nav-logo">
          <RadafiqLogo size={36} />
          <div>
            <div className="nav-logo-text">Radafiq</div>
            <div className="nav-logo-sub">Finance Manager</div>
          </div>
        </div>

        <nav style={{ flex: 1 }}>
          {navItemsWithSparkles.map(({ path, label, icon: Icon }) => {
            const isActive = location.pathname.startsWith(path);
            return (
              <NavLink
                key={path}
                to={path}
                className={`nav-item${isActive ? ' active' : ''}`}
              >
                <NavIcon Icon={Icon} isActive={isActive} />
                {label}
              </NavLink>
            );
          })}
        </nav>

        {profile && (
          <div className="nav-footer">
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
          </div>
        )}
      </aside>

      {/* Main content */}
      <main className="main-content" ref={mainRef}>
        <div className="radafiq-bg">
          <div key={location.pathname}>
            {children}
          </div>
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
