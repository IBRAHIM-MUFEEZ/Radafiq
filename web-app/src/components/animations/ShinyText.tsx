import React from 'react';

interface ShinyTextProps {
  children: React.ReactNode;
  className?: string;
  speed?: number;
  shineColor?: string;
  style?: React.CSSProperties;
}

const shinyStyleId = 'shiny-text-anim';

function injectShinyStyles() {
  if (document.getElementById(shinyStyleId)) return;
  const css = document.createElement('style');
  css.id = shinyStyleId;
  css.textContent = `
    @keyframes shiny-text-sweep {
      0% { background-position: 0% 50%; }
      100% { background-position: 200% 50%; }
    }
  `;
  document.head.appendChild(css);
}
if (typeof document !== 'undefined') injectShinyStyles();

export default function ShinyText({
  children,
  className = '',
  speed = 3,
  shineColor = 'rgba(255,255,255,0.6)',
  style,
}: ShinyTextProps) {
  return (
    <span
      className={className}
      style={{
        background: `linear-gradient(90deg, transparent 0%, ${shineColor} 50%, transparent 100%)`,
        backgroundSize: '200% 100%',
        WebkitBackgroundClip: 'text',
        WebkitTextFillColor: 'transparent',
        backgroundClip: 'text',
        animation: `shiny-text-sweep ${speed}s linear infinite`,
        ...style,
      }}
    >
      {children}
    </span>
  );
}
