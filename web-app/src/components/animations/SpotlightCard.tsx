import React, { useRef, useCallback, useState } from 'react';

interface SpotlightCardProps {
  children: React.ReactNode;
  className?: string;
  spotlightColor?: string;
  style?: React.CSSProperties;
}

export default function SpotlightCard({
  children,
  className = '',
  spotlightColor = 'rgba(91, 127, 255, 0.08)',
  style,
}: SpotlightCardProps) {
  const ref = useRef<HTMLDivElement>(null);
  const [spotlight, setSpotlight] = useState({ x: 50, y: 50 });
  const [isHovered, setIsHovered] = useState(false);

  const handleMouseMove = useCallback((e: React.MouseEvent) => {
    if (!ref.current) return;
    const rect = ref.current.getBoundingClientRect();
    const x = ((e.clientX - rect.left) / rect.width) * 100;
    const y = ((e.clientY - rect.top) / rect.height) * 100;
    setSpotlight({ x, y });
  }, []);

  return (
    <div
      ref={ref}
      className={className}
      onMouseMove={handleMouseMove}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      style={{
        position: 'relative',
        overflow: 'hidden',
        ...style,
      }}
    >
      {isHovered && (
        <div
          style={{
            position: 'absolute',
            inset: 0,
            background: `radial-gradient(600px circle at ${spotlight.x}% ${spotlight.y}%, ${spotlightColor}, transparent 60%)`,
            pointerEvents: 'none',
            zIndex: 0,
            transition: 'opacity 0.2s ease',
          }}
        />
      )}
      <div style={{ position: 'relative', zIndex: 1 }}>
        {children}
      </div>
    </div>
  );
}
