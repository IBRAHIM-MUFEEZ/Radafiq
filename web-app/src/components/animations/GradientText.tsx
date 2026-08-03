import React from 'react';

interface GradientTextProps {
  children: React.ReactNode;
  className?: string;
  colors?: string[];
  duration?: number;
  style?: React.CSSProperties;
}

const gradStyleId = 'gradient-text-anim';

function injectGradStyles() {
  if (document.getElementById(gradStyleId)) return;
  const css = document.createElement('style');
  css.id = gradStyleId;
  css.textContent = `
    @keyframes gradient-text-shift {
      0% { background-position: 0% 50%; }
      50% { background-position: 100% 50%; }
      100% { background-position: 0% 50%; }
    }
  `;
  document.head.appendChild(css);
}
if (typeof document !== 'undefined') injectGradStyles();

export default function GradientText({
  children,
  className = '',
  colors = ['#5B7FFF', '#2DD4A0', '#A78BFA', '#F59E5A', '#5B7FFF'],
  duration = 4,
  style,
}: GradientTextProps) {
  return (
    <span
      className={className}
      style={{
        background: `linear-gradient(135deg, ${colors.join(', ')})`,
        backgroundSize: '300% 300%',
        WebkitBackgroundClip: 'text',
        WebkitTextFillColor: 'transparent',
        backgroundClip: 'text',
        animation: `gradient-text-shift ${duration}s ease infinite`,
        ...style,
      }}
    >
      {children}
    </span>
  );
}
